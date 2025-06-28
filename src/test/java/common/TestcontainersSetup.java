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
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Jednorazowe przygotowanie środowiska (Postgres + RabbitMQ) dla testów E2E.
 */
public abstract class TestcontainersSetup {

    private static final Logger log = LoggerFactory.getLogger(TestcontainersSetup.class);

    /* ==================================================================== docker-compose */

    @SuppressWarnings("resource")
    private static final DockerComposeContainer<?> ENV =
            new DockerComposeContainer<>(new File("docker-compose.yml"))
                    .withExposedService(
                            "db", 5432,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
                    .withExposedService(
                            "rabbitmq", 5672,
                            Wait.forLogMessage(".*Server startup complete.*", 1)
                                    .withStartupTimeout(Duration.ofSeconds(90)));

    /* ==================================================================== publ. pola */

    public static DataSource         dataSource;
    public static DSLContext         dslContext;
    public static ConnectionFactory  rabbitMqCF;

    /* ==================================================================== init → tylko raz */

    private static final AtomicBoolean INITIALISED = new AtomicBoolean(false);

    /** Jawne wywołanie z pierwszego hooka testów; odpali się tylko raz. */
    public static synchronized void initOnce() {
        if (INITIALISED.get()) return;

        log.info("⏳  Uruchamiam kontenery docker-compose…");
        ENV.start();

        /* ---------- Postgres ---------- */
        String pgHost = ENV.getServiceHost("db", 5432);
        Integer pgPort = ENV.getServicePort("db", 5432);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:postgresql://" + pgHost + ":" + pgPort + "/testdb");
        hc.setUsername("testuser");
        hc.setPassword("testpass");
        dataSource = new HikariDataSource(hc);

        dslContext = DSL.using(dataSource, SQLDialect.POSTGRES);
        applySchema();

        /* ---------- RabbitMQ ---------- */
        String rmqHost = ENV.getServiceHost("rabbitmq", 5672);
        Integer rmqPort = ENV.getServicePort("rabbitmq", 5672);

        rabbitMqCF = new ConnectionFactory();
        rabbitMqCF.setHost(rmqHost);
        rabbitMqCF.setPort(rmqPort);
        rabbitMqCF.setUsername("guest");
        rabbitMqCF.setPassword("guest");

        log.info("✅  Środowisko gotowe: db {}:{}, rmq {}:{}",
                pgHost, pgPort, rmqHost, rmqPort);
        INITIALISED.set(true);

    }

    /* ==================================================================== sprzątanie */

    public static void shutdown() {
        log.info("🧹  Zatrzymuję kontenery docker-compose.");
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
        ENV.stop();
    }

    /* ==================================================================== helpers */

    /** Ładuje testowy schemat z classpath (src/test/resources/db/migration/…). */
    private static void applySchema() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            byte[] sqlBytes = TestcontainersSetup.class
                    .getClassLoader()
                    .getResourceAsStream("db/migration/V1__create_order_table.sql")
                    .readAllBytes();

            stmt.execute(new String(sqlBytes, StandardCharsets.UTF_8));
            log.info("🗄️  Załadowano schemat bazy (V1).");

        } catch (Exception e) {
            throw new RuntimeException("Błąd przy ładowaniu schematu DB", e);
        }
    }
}
