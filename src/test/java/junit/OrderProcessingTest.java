package junit;

import app.model.Order;
import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import app.worker.OrderWorker;
import common.TestcontainersSetup;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("prodlike")
public class OrderProcessingTest {

    private OrderRepository repo;
    private RabbitMqClient  rmqClient;
    private OrderWorker     worker;
    private Thread          workerThread;

    @BeforeAll
    void setupEnvironment() throws Exception {
        // startuje Postgres + RabbitMQ
        TestcontainersSetup.initOnce();

        // przygotuj repo, klienta kolejki i worker
        repo      = new OrderRepository(TestcontainersSetup.dslContext);
        rmqClient = new RabbitMqClient(TestcontainersSetup.rabbitMqCF);
        rmqClient.connectAndDeclareQueue("processed_orders");

        // uruchom worker w tle
        worker = new OrderWorker(repo, rmqClient, "processed_orders");
        workerThread = new Thread(worker, "order-worker");
        workerThread.start();
    }

    @AfterAll
    void teardown() throws Exception {
        worker.stop();
        workerThread.join(500);
        rmqClient.close();
        TestcontainersSetup.shutdown();
    }

    @BeforeEach
    void cleanBefore() throws Exception {
        repo.truncateOrdersTable();
        rmqClient.clearQueue();
    }

    @Test
    void shouldProcessNewOrder() throws Exception {
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("200.00")
                .setScale(2, RoundingMode.HALF_UP);

        repo.insertOrder(new Order(id, amount, "PLN"));

        String msg = rmqClient.getMessageFromQueue(Duration.ofSeconds(30));
        assertThat(msg).as("Expect a processed message").isNotNull();

        JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
        assertThat(json.get("id").getAsString()).isEqualTo(id.toString());

        BigDecimal expectedTotal = amount
                .multiply(new BigDecimal("1.23"))
                .setScale(2, RoundingMode.HALF_UP);

        assertThat(new BigDecimal(json.get("totalAmount").getAsString()))
                .as("Total amount after VAT")
                .isEqualByComparingTo(expectedTotal);
    }

    @ParameterizedTest
    @CsvSource({
            "150.00,EUR,184.50",
            "75.25,GBP,92.56",
            "0.00,USD,0.00"
    })
    void shouldProcessVariousOrders(String amountStr, String currency, String expectedTotalStr) throws Exception {
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal(amountStr)
                .setScale(2, RoundingMode.HALF_UP);

        repo.insertOrder(new Order(id, amount, currency));

        String msg = rmqClient.getMessageFromQueue(Duration.ofSeconds(30));
        assertThat(msg).isNotNull();

        JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
        assertThat(json.get("id").getAsString()).isEqualTo(id.toString());
        assertThat(new BigDecimal(json.get("totalAmount").getAsString()))
                .isEqualByComparingTo(new BigDecimal(expectedTotalStr));
    }

    @Test
    void shouldRejectNegativeAmount() {
        UUID id = UUID.randomUUID();
        BigDecimal neg = new BigDecimal("-10.00")
                .setScale(2, RoundingMode.HALF_UP);

        assertThatThrownBy(() ->
                repo.insertOrder(new Order(id, neg, "PLN"))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }
}
