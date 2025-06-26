package cucumber.step;

import app.model.Order;
import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import app.worker.OrderWorker;
import common.DatabaseSteps;
import common.RabbitMqSteps;
import common.TestcontainersSetup;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step-definitions dla feature ‟order_processing”.
 */
public class OrderProcessingStepDefinitions {

    private static final Logger log        = LoggerFactory.getLogger(OrderProcessingStepDefinitions.class);
    private static final String QUEUE_NAME = "processed_orders";

    /* helpery */
    private DatabaseSteps db;
    private RabbitMqSteps rmq;
    private RabbitMqClient rmqClient;

    /* worker */
    private OrderWorker worker;
    private Thread      workerThread;

    /* kontekst */
    private Order  insertedOrder;
    private String consumedMsg;

    /* ============================================================= HOOKI */

    @Before
    public void beforeScenario() throws Exception {

        /* ── 1. Gwarantujemy, że środowisko Testcontainers już stoi ── */
        TestcontainersSetup.initOnce();

        /* ── 2. Postgres helpery ── */
        OrderRepository repo = new OrderRepository(TestcontainersSetup.dslContext);
        db  = new DatabaseSteps(repo);

        /* ── 3. RabbitMQ helpery ── */
        rmqClient = new RabbitMqClient(TestcontainersSetup.rabbitMqCF);
        rmqClient.connectAndDeclareQueue(QUEUE_NAME);
        rmq = new RabbitMqSteps(rmqClient, QUEUE_NAME);

        /* ── 4. Worker w osobnym wątku ── */
        worker = new OrderWorker(repo, rmqClient, QUEUE_NAME);
        workerThread = new Thread(worker, "order-worker");
        workerThread.start();
    }

    @After
    public void afterScenario() throws InterruptedException {
        if (worker != null) worker.stop();
        if (workerThread != null) workerThread.join(500);
        if (rmqClient != null) rmqClient.close();
    }

    /* ============================================================= GIVEN */

    @Given("a clean environment for order processing")
    public void aCleanEnvironment() {
        db.truncateOrdersTable();
        rmq.purgeQueue();
    }

    @Given("a new order with ID {string}, amount {double} and currency {string}")
    public void aNewOrder(String id, Double amount, String currency) {
        insertedOrder = db.givenOrderInDatabase(
                UUID.fromString(id),
                BigDecimal.valueOf(amount).setScale(2),
                currency
        );
    }

    /* ============================================================= WHEN */

    @When("the order is processed by the system")
    public void waitForProcessing() {
        consumedMsg = rmq.waitForMessage(Duration.ofSeconds(30));
    }

    /* ============================================================= THEN */

    @Then("a message should appear in the {string} queue within {int} seconds")
    public void messageShouldAppear(String queue, Integer seconds) {
        assertThat(consumedMsg)
                .as("Brak wiadomości w kolejce %s w ciągu %d s", queue, seconds)
                .isNotNull();
    }

    @Then("the message should contain the processed order details for {string} with VAT applied")
    public void messageContainsProcessedOrder(String orderIdStr) {
        rmq.assertProcessedOrder(consumedMsg, insertedOrder);
        db.thenOrderShouldExistInDatabase(UUID.fromString(orderIdStr));
    }
}
