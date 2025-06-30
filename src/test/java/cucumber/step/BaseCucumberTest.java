package cucumber.step;

import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import env.TestcontainersEnvironment;
import app.worker.OrderWorker;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Before;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import common.DatabaseSteps; // Import nowej klasy
import common.RabbitMqSteps; // Import nowej klasy

import static app.jooq.Tables.ORDERS;

public class CucumberHooks {

    private static final Logger log = LoggerFactory.getLogger(CucumberHooks.class);

    // Te pola nadal muszą być, bo to na nich bazują nasze klasy Steps
    static TestcontainersEnvironment testcontainersEnvironment;
    private static OrderRepository repo;
    static RabbitMqClient rmqClient;
    private static String queueName;

    // Instancje klas kroków - statyczne, bo Cucumber Hooks są statyczne
    public static DatabaseSteps dbSteps;
    public static RabbitMqSteps mqSteps;

    // Worker i jego wątek
    public static Thread workerThread;
    public static OrderWorker worker;


    @BeforeAll
    public static void setup() throws Exception {
        log.info("--- Cucumber @BeforeAll: Rozpoczynam inicjalizację środowiska testowego (Testcontainers) ---");

        testcontainersEnvironment = new TestcontainersEnvironment();
        testcontainersEnvironment.initOnce();

        // Inicjalizacja repo i klienta RabbitMQ
        repo = new OrderRepository(testcontainersEnvironment.getDslContext());
        rmqClient = new RabbitMqClient(testcontainersEnvironment.getRabbitMqConnectionFactory());

        // Pobranie nazwy kolejki
        queueName = testcontainersEnvironment.getTestProperties().getProperty("app.queue.name");
        rmqClient.connectAndDeclareQueue(queueName); // Połącz i zadeklaruj kolejkę raz

        // Inicjalizacja klas kroków z zależnościami
        dbSteps = new DatabaseSteps(repo);
        mqSteps = new RabbitMqSteps(rmqClient, queueName);

        // Uruchomienie workera
        worker = new OrderWorker(repo, rmqClient, queueName);
        workerThread = new Thread(worker, "test-order-worker-thread");
        workerThread.start();
        log.info("--- Cucumber @BeforeAll: OrderWorker uruchomiony w wątku testowym. ---");

        log.info("--- Cucumber @BeforeAll: Środowisko Testcontainers, repo, rmqClient, klasy kroków oraz worker zainicjalizowane. ---");
    }

    @Before
    public void beforeScenario(Scenario scenario) throws Exception {
        log.info("--- Cucumber @Before: Rozpoczynam scenariusz: {} ---", scenario.getName());

        // Używamy DatabaseSteps i RabbitMqSteps do czyszczenia
        dbSteps.truncateOrdersTable();
        mqSteps.purgeQueue();

        log.info("--- Cucumber @Before: Środowisko wyczyszczone przed scenariuszem. ---");
    }

    @AfterAll
    public static void teardown() {
        log.info("--- Cucumber @AfterAll: Zamykam środowisko testowe (Testcontainers) ---");

        if (worker != null) {
            log.info("--- Cucumber @AfterAll: Zatrzymuję OrderWorker. ---");
            worker.stop();
        }
        if (workerThread != null && workerThread.isAlive()) {
            try {
                workerThread.join(5000); // Czekaj na zakończenie workera
                if (workerThread.isAlive()) {
                    workerThread.interrupt();
                    log.warn("Wątek workera nie zakończył się poprawnie, wymuszam przerwanie.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Wątek testowy przerwany podczas oczekiwania na zatrzymanie workera.", e);
            }
        }

        if (rmqClient != null) {
            log.info("--- Cucumber @AfterAll: Zamykam połączenie z RabbitMQ ---");
            rmqClient.close();
        }

        if (testcontainersEnvironment != null) {
            testcontainersEnvironment.shutdown();
        }
        log.info("--- Cucumber @AfterAll: Środowisko Testcontainers zamknięte. ---");
    }

    @After
    public void afterScenario(Scenario scenario) {
        log.info("--- Cucumber @After: Zakończono scenariusz: {} - Status: {} ---",
                scenario.getName(), scenario.getStatus());
    }
}