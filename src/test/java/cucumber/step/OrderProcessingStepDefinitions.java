package cucumber.step;

import app.model.Order;
import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import app.worker.OrderWorker;
import common.DatabaseSteps;
import common.RabbitMqSteps;
import common.TestcontainersSetup;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderProcessingStepDefinitions {

    private static final Logger log        = LoggerFactory.getLogger(OrderProcessingStepDefinitions.class);
    private static final String QUEUE_NAME = "processed_orders";

    private DatabaseSteps   db;
    private RabbitMqSteps   rmq;
    private RabbitMqClient  rmqClient;
    private OrderWorker     worker;
    private Thread          workerThread;

    private Order           insertedOrder;
    private String          consumedMsg;
    private Exception       creationException;

    @Before
    public void beforeScenario() throws Exception {
        TestcontainersSetup.initOnce();

        OrderRepository repo = new OrderRepository(TestcontainersSetup.dslContext);
        db        = new DatabaseSteps(repo);

        rmqClient = new RabbitMqClient(TestcontainersSetup.rabbitMqCF);
        rmqClient.connectAndDeclareQueue(QUEUE_NAME);
        rmq       = new RabbitMqSteps(rmqClient, QUEUE_NAME);

        worker    = new OrderWorker(repo, rmqClient, QUEUE_NAME);
        workerThread = new Thread(worker, "order-worker");
        workerThread.start();
    }

    @After
    public void afterScenario() throws InterruptedException {
        if (worker != null)      worker.stop();
        if (workerThread != null) workerThread.join(500);
        if (rmqClient != null)    rmqClient.close();
    }

    // ---------- Common steps ----------

    @Given("a clean environment for order processing")
    public void aCleanEnvironment() {
        db.truncateOrdersTable();
        rmq.purgeQueue();
    }

    // ---------- Edge case: negative amount ----------

    @When("I attempt to insert a new order with ID {string}, amount {double} and currency {string}")
    public void iAttemptToInsertNewOrder(String id, Double amount, String currency) {
        creationException = null;
        try {
            db.givenOrderInDatabase(
                    UUID.fromString(id),
                    BigDecimal.valueOf(amount).setScale(2),
                    currency
            );
        } catch (Exception e) {
            creationException = e;
        }
    }

    @Then("order creation should fail with IllegalArgumentException")
    public void orderCreationShouldFail() {
        assertThat(creationException)
                .as("Expected IllegalArgumentException for negative amount")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    // ---------- Happy path: valid orders ----------

    @Given("a new order with ID {string}, amount {double} and currency {string}")
    public void aNewOrder(String id, Double amount, String currency) {
        insertedOrder = db.givenOrderInDatabase(
                UUID.fromString(id),
                BigDecimal.valueOf(amount).setScale(2),
                currency
        );
    }

    @When("the order is processed by the system")
    public void waitForProcessing() {
        consumedMsg = rmq.waitForMessage(Duration.ofSeconds(30));
    }

    @Then("a message should appear in the {string} queue within {int} seconds")
    public void messageShouldAppear(String queue, Integer seconds) {
        assertThat(consumedMsg)
                .as("No message in queue %s within %d seconds", queue, seconds)
                .isNotNull();
    }

    @Then("the message should contain the processed order details for {string} with VAT applied")
    public void messageContainsProcessedOrder(String orderIdStr) {
        rmq.assertProcessedOrder(consumedMsg, insertedOrder);
        db.thenOrderShouldExistInDatabase(UUID.fromString(orderIdStr));
    }

    @Then("the message JSON for {string} should contain totalAmount {double}")
    public void messageJsonShouldContainTotal(String orderId, Double expectedTotal) {
        JsonObject json = JsonParser.parseString(consumedMsg).getAsJsonObject();

        // verify id
        assertThat(json.get("id").getAsString())
                .as("id in JSON")
                .isEqualTo(orderId);

        // verify totalAmount
        BigDecimal actual   = new BigDecimal(json.get("totalAmount").getAsString());
        BigDecimal expected = BigDecimal.valueOf(expectedTotal).setScale(2);
        assertThat(actual)
                .as("totalAmount in JSON")
                .isEqualByComparingTo(expected);
    }
}
