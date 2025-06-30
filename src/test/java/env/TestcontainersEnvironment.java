package env;

import common.AbstractTestEnvironment;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

public class TestcontainersEnvironment extends AbstractTestEnvironment {

    @SuppressWarnings("resource")
    private DockerComposeContainer<?> ENV;

    public TestcontainersEnvironment() {
        this.testProperties = loadProperties("test.properties");
    }

    @Override
    protected void doInit() throws Exception {
        log.info("Uruchamiam kontenery docker-compose (tryb AUTOMATYCZNY Testcontainers)...");
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
            hc.setJdbcUrl("jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + testProperties.getProperty("db.name"));
            hc.setUsername(testProperties.getProperty("db.user"));
            hc.setPassword(testProperties.getProperty("db.pass"));
            this.dataSourceInstance = new HikariDataSource(hc);
            this.dslContextInstance = DSL.using(this.dataSourceInstance, SQLDialect.POSTGRES);
            applySchema(this.dataSourceInstance);

            /* ---------- RabbitMQ ---------- */
            String rmqHost = ENV.getServiceHost("rabbitmq", 5672);
            Integer rmqPort = ENV.getServicePort("rabbitmq", 5672);

            this.rabbitMqCFInstance = createRabbitMqConnectionFactory(
                    rmqHost, rmqPort,
                    testProperties.getProperty("rabbitmq.user"),
                    testProperties.getProperty("rabbitmq.pass"));

            log.info("✅  Środowisko Testcontainers gotowe: db {}:{}, rmq {}:{}", pgHost, pgPort, rmqHost, rmqPort);

        } catch (Exception e) {
            log.error("Błąd podczas uruchamiania Testcontainers: " + e.getMessage(), e);
            throw new RuntimeException("Nie udało się uruchomić Testcontainers. Sprawdź konfigurację Docker i docker-compose.yml.", e);
        }
    }

    @Override
    protected void doShutdown() {
        if (ENV != null) {
            ENV.stop();
        }
    }
}