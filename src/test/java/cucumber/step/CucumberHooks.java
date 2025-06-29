package cucumber.step;

import app.jooq.tables.Orders;
import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import cucumber.TestcontainersEnvironment;
import app.worker.OrderWorker;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Before;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static app.jooq.Tables.ORDERS;

public class CucumberHooks {

    private static final Logger log = LoggerFactory.getLogger(CucumberHooks.class);

    static TestcontainersEnvironment testcontainersEnvironment;

    public static OrderRepository repo;
    public static RabbitMqClient  rmqClient;
    public static Thread workerThread;
    public static OrderWorker worker;
    private static String queueName;

    @BeforeAll
    public static void setup() throws Exception {
        log.info("--- Cucumber @BeforeAll: Rozpoczynam inicjalizację środowiska testowego (Testcontainers) ---");

        testcontainersEnvironment = new TestcontainersEnvironment();
        testcontainersEnvironment.initOnce();

        // repo i klient RabbitMQ
        repo = new OrderRepository(testcontainersEnvironment.getDslContext());
        rmqClient = new RabbitMqClient(testcontainersEnvironment.getRabbitMqConnectionFactory());

        // Pobranie nazwy kolejki z właściwości i natychmiastowe połączenie/deklaracja
        queueName = testcontainersEnvironment.getTestProperties().getProperty("app.queue.name");
        rmqClient.connectAndDeclareQueue(queueName);

        // Uruchomienie workera
        worker = new OrderWorker(repo, rmqClient, queueName);
        workerThread = new Thread(worker, "test-order-worker-thread");
        workerThread.start();
        log.info("--- Cucumber @BeforeAll: OrderWorker uruchomiony w wątku testowym. ---");

        log.info("--- Cucumber @BeforeAll: Środowisko Testcontainers, repo, rmqClient oraz worker zainicjalizowane. ---");
    }

    @Before
    public void beforeScenario(Scenario scenario) throws Exception {
        log.info("--- Cucumber @Before: Rozpoczynam scenariusz: {} ---", scenario.getName());

        // Czyszczenie bazy
        log.info("--- Cucumber @Before: Czyszczę tabelę 'ORDERS' w bazie danych.");
        testcontainersEnvironment.getDslContext()
                .truncateTable(ORDERS)
                .cascade()
                .execute();
        log.info("--- Cucumber @Before: Tabela 'ORDERS' wyczyszczona.");

        // Czyszczenie kolejki
        log.info("--- Cucumber @Before: Czyszczę kolejkę RabbitMQ '{}'.", queueName);
        rmqClient.clearQueue();
        log.info("--- Cucumber @Before: Kolejka '{}' wyczyszczona.", queueName);

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
                workerThread.join(5000);
                if (workerThread.isAlive()) {
                    workerThread.interrupt();
                    log.warn("Wątek workera nie zakończył się poprawnie, wymuszam przerwanie.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
