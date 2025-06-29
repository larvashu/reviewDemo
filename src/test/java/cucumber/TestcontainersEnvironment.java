package common;

import com.rabbitmq.client.ConnectionFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Zarządza środowiskiem testowym (Postgres + RabbitMQ) za pomocą Testcontainers.
 * W pełni automatyczne uruchamianie i zamykanie kontenerów.
 */
public class TestcontainersEnvironment {

    private static final Logger log = LoggerFactory.getLogger(TestcontainersEnvironment.class);

    // Wczytaj konfigurację z pliku test.properties
    public static final Properties TEST_PROPERTIES = loadProperties("test.properties");

    @SuppressWarnings("resource")
    private static DockerComposeContainer<?> ENV;

    public static DataSource dataSource;
    public static DSLContext dslContext;
    public static ConnectionFactory rabbitMqCF;

    private static final AtomicBoolean INITIALISED = new AtomicBoolean(false);

    /** Jawne wywołanie z pierwszego hooka testów; odpali się tylko raz. */
    public static synchronized void initOnce() {
        if (INITIALISED.get()) return;

        log.info("⏳  Uruchamiam kontenery docker-compose (tryb AUTOMATYCZNY Testcontainers)...");
        try {
            ENV = new DockerComposeContainer<>(new File("docker-compose.yml"))
                    .withExposedService(
                            "db", 5432,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
                    .withExposedService(
                            "rabbitmq", 5672,
                            Wait.forLogMessage(".*Server startup complete.*", 1)
                                    .withStartupTimeout(Duration.ofSeconds(90)));

            ENV.start();

            /* ---------- Postgres ---------- */
            String pgHost = ENV.getServiceHost("db", 5432);
            Integer pgPort = ENV.getServicePort("db", 5432);

            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + TEST_PROPERTIES.getProperty("db.name"));
            hc.setUsername(TEST_PROPERTIES.getProperty("db.user"));
            hc.setPassword(TEST_PROPERTIES.getProperty("db.pass"));
            dataSource = new HikariDataSource(hc);

            dslContext = DSL.using(dataSource, SQLDialect.POSTGRES);
            applySchema(); // Schemat aplikowany do dynamicznie przydzielonej bazy

            /* ---------- RabbitMQ ---------- */
            String rmqHost = ENV.getServiceHost("rabbitmq", 5672);
            Integer rmqPort = ENV.getServicePort("rabbitmq", 5672);

            rabbitMqCF = new ConnectionFactory();
            rabbitMqCF.setHost(rmqHost);
            rabbitMqCF.setPort(rmqPort);
            rabbitMqCF.setUsername(TEST_PROPERTIES.getProperty("rabbitmq.user"));
            rabbitMqCF.setPassword(TEST_PROPERTIES.getProperty("rabbitmq.pass"));

            log.info("✅  Środowisko Testcontainers gotowe: db {}:{}, rmq {}:{}", pgHost, pgPort, rmqHost, rmqPort);
            INITIALISED.set(true);

        } catch (Exception e) {
            log.error("Błąd podczas uruchamiania Testcontainers: " + e.getMessage(), e);
            // Ważne: Rzuć wyjątek, aby testy od razu failowały
            throw new RuntimeException("Nie udało się uruchomić Testcontainers. Sprawdź konfigurację Docker i docker-compose.yml.", e);
        }
    }

    public static void shutdown() {
        if (!INITIALISED.get()) return;

        log.info("🧹  Zatrzymuję kontenery docker-compose (tryb AUTOMATYCZNY Testcontainers).");
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
        if (ENV != null) {
            ENV.stop();
        }
        INITIALISED.set(false);
    }

    /* ==================================================================== helpers */

    /** Ładuje testowy schemat z classpath (src/test/resources/db/migration/…). */
    private static void applySchema() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            byte[] sqlBytes = TestcontainersEnvironment.class // Użyj tej klasy do wczytania zasobu
                    .getClassLoader()
                    .getResourceAsStream("db/migration/V1__create_order_table.sql")
                    .readAllBytes();

            stmt.execute(new String(sqlBytes, StandardCharsets.UTF_8));
            log.info("🗄️  Załadowano schemat bazy (V1).");

        } catch (Exception e) {
            throw new RuntimeException("Błąd przy ładowaniu schematu DB w trybie Testcontainers", e);
        }
    }

    private static Properties loadProperties(String fileName) {
        Properties props = new Properties();
        try (InputStream input = TestcontainersEnvironment.class.getClassLoader().getResourceAsStream(fileName)) { // Użyj tej klasy
            if (input == null) {
                log.warn("Nie znaleziono pliku konfiguracyjnego: " + fileName + ". Używam wartości domyślnych.");
                return props;
            }
            props.load(input);
        } catch (IOException ex) {
            log.error("Błąd podczas ładowania pliku konfiguracyjnego: " + fileName, ex);
            throw new RuntimeException("Nie można załadować konfiguracji.", ex);
        }
        return props;
    }
}