package cucumber.step;

import app.model.Order;
import app.model.ProcessedOrder;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class SmokeStepDefinitions {

    private static final Logger log = LoggerFactory.getLogger(SmokeStepDefinitions.class);

    public SmokeStepDefinitions() {
        log.debug("SmokeStepDefinitions: Utworzono nową instancję kroków dla scenariusza.");
        assertThat(BaseCucumberTest.dbSteps).as("dbSteps powinno być zainicjalizowane przez BaseCucumberTest").isNotNull();
        assertThat(BaseCucumberTest.mqSteps).as("mqSteps powinno być zainicjalizowane przez BaseCucumberTest").isNotNull();
    }

    @Given("Testcontainers PostgreSQL is up")
    public void testcontainersPostgreSQLIsUp() {
        log.info("Krok GIVEN: Weryfikuję, czy Testcontainers PostgreSQL jest uruchomiony.");
        assertThat(BaseCucumberTest.testcontainersEnvironment.getDataSource())
                .as("DataSource powinno być zainicjalizowane")
                .isNotNull();
        assertThat(BaseCucumberTest.testcontainersEnvironment.getDslContext())
                .as("DSLContext powinno być zainicjalizowane")
                .isNotNull();

        try {
            BaseCucumberTest.testcontainersEnvironment.getDslContext()
                    .selectOne()
                    .fetch();
            log.info("Krok GIVEN: Połączenie z PostgreSQL nawiązane pomyślnie i baza danych odpowiada.");
        } catch (Exception e) {
            log.error("Błąd podczas próby wykonania zapytania do bazy danych PostgreSQL.", e);
            fail("Nie udało się wykonać zapytania do PostgreSQL: " + e.getMessage());
        }
    }

    @Then("I can insert and read a dummy record in the orders table")
    public void iCanInsertAndReadADummyRecordInTheTheOrdersTable() {
        log.info("Krok THEN: Sprawdzam możliwość wstawienia i odczytu rekordu z tabeli orders.");
        UUID dummyId = UUID.randomUUID();
        BigDecimal dummyAmount = new BigDecimal("1.00");
        String dummyCurrency = "TST";

        Order dummyOrderOriginal = new Order(dummyId, dummyAmount, dummyCurrency);

        BaseCucumberTest.dbSteps.givenOrderInDatabase(dummyId, dummyAmount, dummyCurrency);
        log.info("Dummy record ID: {} wstawiony przez DatabaseSteps.", dummyId);

        ProcessedOrder retrievedOrder = BaseCucumberTest.dbSteps.whenFindOrderInDatabase(dummyId);

        assertThat(retrievedOrder)
                .as("Powinienem odczytać wstawiony dummy record jako ProcessedOrder")
                .isNotNull();
        assertThat(retrievedOrder.id())
                .as("ID odczytanego rekordu powinno być zgodne")
                .isEqualTo(dummyId);
        assertThat(retrievedOrder.originalAmount())
                .as("Kwota oryginalna odczytanego rekordu powinna być zgodna")
                .isEqualTo(dummyOrderOriginal.amount());
        assertThat(retrievedOrder.currency())
                .as("Waluta odczytanego rekordu powinna być zgodna")
                .isEqualTo(dummyOrderOriginal.currency());

        assertThat(retrievedOrder.vatAmount()).as("Początkowy vatAmount powinien być null").isNull();
        assertThat(retrievedOrder.totalAmount()).as("Początkowy totalAmount powinien być null").isNull();

        log.info("Dummy record ID: {} odczytany pomyślnie jako ProcessedOrder.", dummyId);
    }

    @Given("RabbitMQ is up")
    public void rabbitMQIsUp() {
        log.info("Krok GIVEN: Weryfikuję, czy RabbitMQ jest uruchomiony.");
        assertThat(BaseCucumberTest.testcontainersEnvironment.getRabbitMqConnectionFactory())
                .as("ConnectionFactory RabbitMQ powinno być zainicjalizowane")
                .isNotNull();

        try (Connection conn = BaseCucumberTest.testcontainersEnvironment.getRabbitMqConnectionFactory().newConnection();
             Channel channel = conn.createChannel()) {
            assertThat(channel).isNotNull();
            channel.queueDeclarePassive("order_queue");
            log.info("Krok GIVEN: Połączenie z RabbitMQ nawiązane pomyślnie i kanał otwarty.");
        } catch (Exception e) {
            log.error("Błąd podczas próby połączenia z RabbitMQ.", e);
            fail("Nie udało się połączyć z RabbitMQ: " + e.getMessage());
        }
    }

    @Then("I can publish and consume a dummy message on {string}")
    public void iCanPublishAndConsumeADummyMessageOn(String queueName) throws Exception {
        log.info("Krok THEN: Sprawdzam możliwość publikacji i konsumpcji dummy message na kolejce '{}'.", queueName);
        String testMessage = "{\"testId\":\"" + UUID.randomUUID() + "\", \"message\":\"dummy_test\"}";

        BaseCucumberTest.rmqClient.publishMessage("", queueName, testMessage);
        log.info("Opublikowano dummy message: {}", testMessage);

        String consumedMessage = BaseCucumberTest.mqSteps.waitForMessage(Duration.ofSeconds(10));
        assertThat(consumedMessage)
                .as("Powinienem skonsumować dummy message z kolejki")
                .isEqualTo(testMessage);
        log.info("Skonsumowano dummy message: {}", consumedMessage);
    }

    @Given("a running OrderWorker")
    public void aRunningOrderWorker() {
        log.info("Krok GIVEN: Weryfikuję, czy OrderWorker jest uruchomiony.");
        assertThat(BaseCucumberTest.worker)
                .as("Instancja OrderWorker powinna istnieć.")
                .isNotNull();
        assertThat(BaseCucumberTest.workerThread)
                .as("Wątek OrderWorker powinien istnieć.")
                .isNotNull();
        assertThat(BaseCucumberTest.workerThread.isAlive())
                .as("Wątek OrderWorker powinien być żywy.")
                .isTrue();
        log.info("Krok GIVEN: OrderWorker jest uruchomiony i wątek jest żywy.");
    }

    @Then("worker thread is alive")
    public void workerThreadIsAlive() {
        log.info("Krok THEN: Weryfikuję, czy wątek workera jest nadal żywy.");
        assertThat(BaseCucumberTest.workerThread.isAlive())
                .as("Wątek workera powinien być żywy.")
                .isTrue();
        log.info("Krok THEN: Wątek workera jest żywy.");
    }

    @When("I stop the worker")
    public void iStopTheWorker() {
        log.info("Krok WHEN: Zatrzymuję OrderWorker.");
        BaseCucumberTest.worker.stop();
        try {
            BaseCucumberTest.workerThread.join(5000);
            if (BaseCucumberTest.workerThread.isAlive()) {
                log.warn("Wątek workera nadal żywy po oczekiwaniu na join. Może być problem z czystym zamknięciem.");
            }
        } catch (InterruptedException e) {
            log.warn("Wątek testowy przerwany podczas oczekiwania na zatrzymanie workera.", e);
            Thread.currentThread().interrupt();
            fail("Wątek testowy przerwany podczas zatrzymywania workera.");
        }
        log.info("Krok WHEN: Zatrzymano OrderWorker.");
    }

    @Then("worker thread is not alive")
    public void workerThreadIsNotAlive() {
        log.info("Krok THEN: Weryfikuję, czy wątek workera nie jest już żywy.");
        assertThat(BaseCucumberTest.workerThread.isAlive())
                .as("Wątek workera nie powinien być żywy.")
                .isFalse();
        log.info("Krok THEN: Wątek workera nie jest już żywy.");
    }
}