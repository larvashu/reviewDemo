// src/test/java/cucumber/step/SmokeStepDefinitions.java
package cucumber.step;

import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import app.worker.OrderWorker;
import common.DatabaseSteps;
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

public class SmokeStepDefinitions {

    private static final Logger log        = LoggerFactory.getLogger(SmokeStepDefinitions.class);
    private static final String QUEUE_NAME = "processed_orders";

    private DatabaseSteps   db;
    private RabbitMqClient  rmqClient;
    private OrderWorker     worker;
    private Thread          workerThread;

    @Before
    public void beforeScenario() throws Exception {
        TestcontainersSetup.initOnce();

        OrderRepository repo = new OrderRepository(TestcontainersSetup.dslContext);
        db        = new DatabaseSteps(repo);

        rmqClient = new RabbitMqClient(TestcontainersSetup.rabbitMqCF);
        rmqClient.connectAndDeclareQueue(QUEUE_NAME);

        worker    = new OrderWorker(repo, rmqClient, QUEUE_NAME);
        workerThread = new Thread(worker, "smoke-order-worker");
        workerThread.start();
    }

    @After
    public void afterScenario() throws InterruptedException {
        if (worker != null)      worker.stop();
        if (workerThread != null) workerThread.join(500);
        if (rmqClient != null)    rmqClient.close();
    }

    @Given("Testcontainers PostgreSQL is up")
    public void postgresIsUp() {
        assertThat(TestcontainersSetup.dslContext)
                .as("dslContext powinien być nie-null")
                .isNotNull();
    }

    @Then("I can insert and read a dummy record in the orders table")
    public void canRoundtripDummyRecord() {
        UUID tmp = UUID.randomUUID();
        db.truncateOrdersTable();
        db.givenOrderInDatabase(tmp, BigDecimal.valueOf(1.23).setScale(2), "PLN");
        db.thenOrderShouldExistInDatabase(tmp);
    }

    @Given("RabbitMQ is up")
    public void rabbitMqIsUp() {
        assertThat(rmqClient)
                .as("RabbitMqClient powinien być zainicjalizowany")
                .isNotNull();
    }

    @Then("I can publish and consume a dummy message on {string}")
    public void canPublishAndConsumeDummy(String queue) throws Exception {
        // oczyść kolejkę przed publikacją
        rmqClient.clearQueue();

        String body = "{\"ping\":\"pong\"}";
        rmqClient.publishMessage("", queue, body);

        String got = rmqClient.getMessageFromQueue(Duration.ofSeconds(5));
        assertThat(got)
                .as("Powinien zwrócić ping/pong")
                .isEqualTo(body);
    }

    @Given("a running OrderWorker")
    public void aRunningWorker() {
        assertThat(workerThread.isAlive())
                .as("Worker powinien działać po starcie")
                .isTrue();
    }

    @Then("worker thread is alive")
    public void workerThreadIsAlive() {
        assertThat(workerThread.isAlive()).isTrue();
    }

    @When("I stop the worker")
    public void iStopWorker() throws InterruptedException {
        worker.stop();
        workerThread.join(500);
    }

    @Then("worker thread is not alive")
    public void workerThreadNotAlive() {
        assertThat(workerThread.isAlive())
                .as("Worker powinien być zatrzymany")
                .isFalse();
    }
}
