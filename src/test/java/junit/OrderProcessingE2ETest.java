//package junit;
//
//import app.model.Order;
//import app.mq.RabbitMqClient;
//import app.repository.OrderRepository;
//import app.worker.OrderWorker;
//import common.DatabaseSteps;
//import common.RabbitMqSteps;
//import common.TestcontainersSetup;
//import io.qameta.allure.*;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.Arguments;
//import org.junit.jupiter.params.provider.CsvSource;
//import org.junit.jupiter.params.provider.MethodSource;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.Duration;
//import java.util.UUID;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.stream.Stream;
//
///**
// * E2E – JUnit 5.
// */
//@DisplayName("End-to-End Test Suite: Order Processing")
//@Feature("Order Processing")
//@Story("Verify VAT calculation and MQ message.")
//@Tag("e2e")
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//public class OrderProcessingE2ETest extends TestcontainersSetup {
//
//    private static final Logger log = LoggerFactory.getLogger(OrderProcessingE2ETest.class);
//    private static final String RABBITMQ_QUEUE_NAME = "processed_orders";
//
//    /* helpers */
//    private DatabaseSteps databaseSteps;
//    private RabbitMqSteps rabbitMqSteps;
//
//    /* infra */
//    private OrderRepository orderRepository;
//    private RabbitMqClient  rabbitMqClient;
//    private OrderWorker     worker;
//    private ExecutorService pool;
//
//    /* ---------- lifecycle ---------- */
//
//    @BeforeEach
//    void setup() throws Exception {
//        orderRepository = new OrderRepository(dslContext);
//        databaseSteps   = new DatabaseSteps(orderRepository);
//
//        rabbitMqClient  = new RabbitMqClient(rabbitMqConnectionFactory);
//        rabbitMqClient.connectAndDeclareQueue(RABBITMQ_QUEUE_NAME);
//        rabbitMqClient.clearQueue();
//        rabbitMqSteps   = new RabbitMqSteps(rabbitMqClient, RABBITMQ_QUEUE_NAME);
//
//        worker = new OrderWorker(orderRepository, rabbitMqClient, RABBITMQ_QUEUE_NAME);
//        pool   = Executors.newSingleThreadExecutor();
//        pool.submit(worker);
//    }
//
//    @AfterEach
//    void tearDown() {
//        if (worker != null) worker.stop();
//        if (pool != null)   pool.shutdownNow();
//        if (rabbitMqClient != null) rabbitMqClient.close();
//        if (orderRepository != null) orderRepository.truncateOrdersTable();
//    }
//
//    /* ---------- CSV happy-path ---------- */
//
//    @ParameterizedTest(name = "{index} ⇒ {0} {1}")
//    @CsvSource({
//            "100.00, PLN",
//            "123.45, EUR",
//            "0.00,   USD"
//    })
//    @Severity(SeverityLevel.CRITICAL)
//    void csvHappyPath(BigDecimal originalAmount, String currency) throws InterruptedException {
//
//        UUID orderId = UUID.randomUUID();
//        Order order = databaseSteps.givenOrderInDatabase(
//                orderId, originalAmount.setScale(2, RoundingMode.HALF_UP), currency);
//
//        String msg = rabbitMqSteps.whenConsumeMessageFromQueue(Duration.ofSeconds(30));
//
//        rabbitMqSteps.thenReceivedMessageShouldBeValidProcessedOrder(msg, order);
//        databaseSteps.thenOrderShouldExistInDatabase(orderId);
//    }
//
//    /* ---------- edge-cases ---------- */
//
//    @ParameterizedTest(name = "{index} ⇒ {0}")
//    @MethodSource("edgeCases")
//    @Severity(SeverityLevel.NORMAL)
//    void edgeCases(UUID id, BigDecimal amt, String cur) throws InterruptedException {
//        Order order = databaseSteps.givenOrderInDatabase(
//                id, amt.setScale(2, RoundingMode.HALF_UP), cur);
//
//        String msg = rabbitMqSteps.whenConsumeMessageFromQueue(Duration.ofSeconds(30));
//
//        rabbitMqSteps.thenReceivedMessageShouldBeValidProcessedOrder(msg, order);
//        databaseSteps.thenOrderShouldExistInDatabase(id);
//    }
//
//    private static Stream<Arguments> edgeCases() {
//        return Stream.of(
//                Arguments.of(UUID.fromString("c0e1e2e3-f4f5-6a7b-8c9d-e0e1e2e3e4e5"), BigDecimal.valueOf(-50.00), "USD"),
//                Arguments.of(UUID.fromString("d0e1e2e3-f4f5-6a7b-8c9d-e0e1e2e3e4e6"), BigDecimal.valueOf(99999.99), "EUR")
//        );
//    }
//}
