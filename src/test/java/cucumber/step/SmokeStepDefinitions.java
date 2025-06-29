package cucumber.step;

import app.model.Order;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail; // Import dla fail()

/**
 * Klasa implementująca definicje kroków BDD dla scenariuszy "Smoke Tests".
 * Skupia się na weryfikacji dostępności i podstawowej funkcjonalności kluczowych komponentów.
 */
public class SmokeStepDefinitions {

    private static final Logger log = LoggerFactory.getLogger(SmokeStepDefinitions.class);

    public SmokeStepDefinitions() {
        log.debug("SmokeStepDefinitions: Utworzono nową instancję kroków dla scenariusza.");
    }

    // --- Database Smoke Tests ---
    @Given("Testcontainers PostgreSQL is up")
    public void testcontainersPostgreSQLIsUp() {
        log.info("Krok GIVEN: Weryfikuję, czy Testcontainers PostgreSQL jest uruchomiony.");
        // Sprawdzenie, czy DataSource i DSLContext są zainicjalizowane
        assertThat(CucumberHooks.testcontainersEnvironment.getDataSource())
                .as("DataSource powinno być zainicjalizowane")
                .isNotNull();
        assertThat(CucumberHooks.testcontainersEnvironment.getDslContext())
                .as("DSLContext powinno być zainicjalizowane")
                .isNotNull();

        // Próba wykonania prostego zapytania w celu weryfikacji aktywnego połączenia
        try {
            CucumberHooks.testcontainersEnvironment.getDslContext()
                    .selectOne()
                    .fetch();
            log.info("Krok GIVEN: Połączenie z PostgreSQL nawiązane pomyślnie i baza danych odpowiada.");
        } catch (Exception e) {
            log.error("Błąd podczas próby wykonania zapytania do bazy danych PostgreSQL.", e);
            fail("Nie udało się wykonać zapytania do PostgreSQL: " + e.getMessage());
        }
    }

    @Then("I can insert and read a dummy record in the orders table")
    public void iCanInsertAndReadADummyRecordInTheOrdersTable() {
        log.info("Krok THEN: Sprawdzam możliwość wstawienia i odczytu rekordu z tabeli orders.");
        UUID dummyId = UUID.randomUUID();
        Order dummyOrder = new Order(dummyId, new BigDecimal("1.00"), "TST"); // Dostosowano konstruktor Order

        // Wstawienie rekordu
        CucumberHooks.repo.insertOrder(dummyOrder);
        log.info("Dummy record ID: {} wstawiony.", dummyId);

        // Odczyt rekordu - UŻYWAMY TERAZ findOrderById()
        Order retrievedOrder = CucumberHooks.repo.findOrderById(dummyId);
        assertThat(retrievedOrder)
                .as("Powinienem odczytać wstawiony dummy record")
                .isNotNull();
        assertThat(retrievedOrder.id())
                .as("ID odczytanego rekordu powinno być zgodne")
                .isEqualTo(dummyId);
        assertThat(retrievedOrder.amount())
                .as("Kwota odczytanego rekordu powinna być zgodna")
                .isEqualTo(dummyOrder.amount());
        assertThat(retrievedOrder.currency())
                .as("Waluta odczytanego rekordu powinna być zgodna")
                .isEqualTo(dummyOrder.currency());
        log.info("Dummy record ID: {} odczytany pomyślnie.", dummyId);
    }

    // --- RabbitMQ Smoke Tests ---
    @Given("RabbitMQ is up")
    public void rabbitMQIsUp() {
        log.info("Krok GIVEN: Weryfikuję, czy RabbitMQ jest uruchomiony.");
        assertThat(CucumberHooks.testcontainersEnvironment.getRabbitMqConnectionFactory())
                .as("ConnectionFactory RabbitMQ powinno być zainicjalizowane")
                .isNotNull();

        try (Connection conn = CucumberHooks.testcontainersEnvironment.getRabbitMqConnectionFactory().newConnection();
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

        CucumberHooks.rmqClient.publishMessage("", queueName, testMessage);
        log.info("Opublikowano dummy message: {}", testMessage);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> consumedMessageRef = new AtomicReference<>();

        try (Connection tempConn = CucumberHooks.testcontainersEnvironment.getRabbitMqConnectionFactory().newConnection();
             Channel tempChannel = tempConn.createChannel()) {

            tempChannel.queueDeclare(queueName, true, false, false, null);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                log.debug("Odebrano wiadomość: {}", message);
                consumedMessageRef.set(message);
                latch.countDown();
            };

            // BasicConsume zwróci consumerTag, możesz go użyć do basicCancel jeśli potrzebujesz
            String consumerTag = tempChannel.basicConsume(queueName, true, deliverCallback, consumer -> {});

            // Oczekiwanie na wiadomość
            boolean received = latch.await(10, TimeUnit.SECONDS); // Timeout 10 sekund

            // Anuluj konsumenta, żeby nie odbierał więcej wiadomości, jeśli test się zakończy
            tempChannel.basicCancel(consumerTag);

            if (!received) {
                fail("Nie skonsumowano wiadomości z kolejki w określonym czasie (" + 10 + "s).");
            }
        }

        assertThat(consumedMessageRef.get())
                .as("Powinienem skonsumować dummy message z kolejki")
                .isEqualTo(testMessage);
        log.info("Skonsumowano dummy message: {}", consumedMessageRef.get());
    }

    // --- Worker Smoke Tests ---
    @Given("a running OrderWorker")
    public void aRunningOrderWorker() {
        log.info("Krok GIVEN: Weryfikuję, czy OrderWorker jest uruchomiony.");
        // Worker jest uruchamiany w CucumberHooks.@BeforeAll
        assertThat(CucumberHooks.worker)
                .as("Instancja OrderWorker powinna istnieć.")
                .isNotNull();
        assertThat(CucumberHooks.workerThread)
                .as("Wątek OrderWorker powinien istnieć.")
                .isNotNull();
        assertThat(CucumberHooks.workerThread.isAlive())
                .as("Wątek OrderWorker powinien być żywy.")
                .isTrue();
        log.info("Krok GIVEN: OrderWorker jest uruchomiony i wątek jest żywy.");
    }

    @Then("worker thread is alive")
    public void workerThreadIsAlive() {
        log.info("Krok THEN: Weryfikuję, czy wątek workera jest nadal żywy.");
        assertThat(CucumberHooks.workerThread.isAlive())
                .as("Wątek workera powinien być żywy.")
                .isTrue();
        log.info("Krok THEN: Wątek workera jest żywy.");
    }

    @When("I stop the worker")
    public void iStopTheWorker() {
        log.info("Krok WHEN: Zatrzymuję OrderWorker.");
        CucumberHooks.worker.stop(); // Wywołanie metody stop na instancji workera
        try {
            CucumberHooks.workerThread.join(5000); // Oczekiwanie na zakończenie wątku, maks. 5 sekund
            if (CucumberHooks.workerThread.isAlive()) {
                log.warn("Wątek workera nadal żywy po oczekiwaniu na join. Może być problem z czystym zamknięciem.");
            }
        } catch (InterruptedException e) {
            log.warn("Wątek testowy przerwany podczas oczekiwania na zatrzymanie workera.", e);
            Thread.currentThread().interrupt(); // Przywrócenie statusu przerwania
            fail("Wątek testowy przerwany podczas zatrzymywania workera.");
        }
        log.info("Krok WHEN: Zatrzymano OrderWorker.");
    }

    @Then("worker thread is not alive")
    public void workerThreadIsNotAlive() {
        log.info("Krok THEN: Weryfikuję, czy wątek workera nie jest już żywy.");
        assertThat(CucumberHooks.workerThread.isAlive())
                .as("Wątek workera nie powinien być żywy.")
                .isFalse();
        log.info("Krok THEN: Wątek workera nie jest już żywy.");
    }
}