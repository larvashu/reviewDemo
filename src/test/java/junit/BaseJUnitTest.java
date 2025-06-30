package junit; // Lub common.test.base

import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import common.DatabaseSteps; // Import nowej klasy
import common.RabbitMqSteps; // Import nowej klasy
import env.ManualEnvironment;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseJUnitTest {

    private static final Logger log = LoggerFactory.getLogger(BaseJUnitTest.class);
    protected OrderRepository repo; // Nadal potrzebne do inicjalizacji DatabaseSteps
    protected RabbitMqClient rmqClient; // Nadal potrzebne do inicjalizacji RabbitMqSteps
    protected String queueName;
    protected Duration defaultMessageTimeout = Duration.ofSeconds(30);
    protected DatabaseSteps dbSteps;
    protected RabbitMqSteps mqSteps;

    private static ManualEnvironment manualEnvironment;

    @BeforeAll
    static void setupManualTestEnvironment() throws Exception {
        log.info("--- BaseIntegrationTest @BeforeAll (static): Rozpoczynam inicjalizację dedykowanego środowiska MANUALNEGO ---");
        manualEnvironment = new ManualEnvironment();
        manualEnvironment.initOnce();
        log.info("--- BaseIntegrationTest @BeforeAll (static): Środowisko MANUALNE zostało zainicjalizowane. ---");
    }

    @BeforeAll
    void setupConnectionsAndStepsForManualEnv() throws Exception { // Zmieniona nazwa metody
        log.info("--- BaseIntegrationTest @BeforeAll: Ustawiam połączenia i inicjalizuję klasy kroków z środowiska MANUALNEGO ---");
        // Inicjalizacja repo i rmqClient
        repo = new OrderRepository(manualEnvironment.getDslContext());
        rmqClient = new RabbitMqClient(manualEnvironment.getRabbitMqConnectionFactory());
        queueName = manualEnvironment.getTestProperties().getProperty("app.queue.name", "processed_orders");

        log.info("--- BaseIntegrationTest @BeforeAll: Łączę się z RabbitMQ i deklaruję kolejkę '{}' ---", queueName);
        rmqClient.connectAndDeclareQueue(queueName);
        log.info("--- BaseIntegrationTest @BeforeAll: Połączono z bazą danych i RabbitMQ dla kolejki: {}. ---", queueName);

        // Inicjalizacja klas kroków
        dbSteps = new DatabaseSteps(repo); // Przekazujemy zainicjalizowane OrderRepository
        mqSteps = new RabbitMqSteps(rmqClient, queueName); // Przekazujemy RabbitMqClient i nazwę kolejki
        log.info("--- BaseIntegrationTest @BeforeAll: Klasy DatabaseSteps i RabbitMqSteps zainicjalizowane. ---");
    }


    @AfterAll
    void teardownConnections() throws Exception {
        log.info("--- BaseIntegrationTest @AfterAll: Rozpoczynam zamykanie połączeń z środowiska MANUALNEGO ---");
        if (rmqClient != null) {
            log.info("--- BaseIntegrationTest @AfterAll: Zamykam połączenie z RabbitMQ ---");
            rmqClient.close();
        }
        log.info("--- BaseIntegrationTest @AfterAll: Połączenia zamknięte. ---");
    }

    @AfterAll
    static void teardownManualTestEnvironment() {
        log.info("--- BaseIntegrationTest @AfterAll (static): Rozpoczynam zamykanie środowiska MANUALNEGO ---");
        if (manualEnvironment != null) {
            manualEnvironment.shutdown();
        }
        log.info("--- BaseIntegrationTest @AfterAll (static): Środowisko MANUALNE zamknięte ---");
    }
}