package common;

import com.rabbitmq.client.ConnectionFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractTestEnvironment {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected DataSource dataSourceInstance;
    protected DSLContext dslContextInstance;
    protected ConnectionFactory rabbitMqCFInstance;
    protected Properties testProperties;
    protected final AtomicBoolean INITIALISED = new AtomicBoolean(false);

    public DataSource getDataSource() { return dataSourceInstance; }
    public DSLContext getDslContext() { return dslContextInstance; }
    public ConnectionFactory getRabbitMqConnectionFactory() { return rabbitMqCFInstance; }
    public Properties getTestProperties() { return testProperties; }

    public synchronized void initOnce() throws Exception {
        if (INITIALISED.get()) {
            log.info("{} już zainicjalizowany. Pomijam inicjalizację.", getClass().getSimpleName());
            return;
        }

        log.info("⏳  Rozpoczynam inicjalizację środowiska: {}", getClass().getSimpleName());
        doInit();
        INITIALISED.set(true);
        log.info("✅  Środowisko {} gotowe.", getClass().getSimpleName());
    }

    public synchronized void shutdown() {
        if (!INITIALISED.get()) {
            log.info("{} nie był zainicjalizowany. Pomijam zamykanie.", getClass().getSimpleName());
            return;
        }

        log.info("🧹  Rozpoczynam zamykanie środowiska: {}", getClass().getSimpleName());
        doShutdown();
        if (dataSourceInstance instanceof HikariDataSource hikari) {
            hikari.close();
        }
        INITIALISED.set(false);
        log.info("🗑️  Środowisko {} zamknięte.", getClass().getSimpleName());
    }

    protected abstract void doInit() throws Exception;
    protected abstract void doShutdown();

    /**
     * Wspólna metoda do załadowania schematu bazy (V1) z resources/db/migration.
     */
    public static void applySchema(DataSource dataSource) {
        Logger logger = LoggerFactory.getLogger(AbstractTestEnvironment.class);
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             InputStream in = AbstractTestEnvironment.class
                     .getClassLoader()
                     .getResourceAsStream("db/migration/V1__create_order_table.sql")) {

            if (in == null) {
                throw new IllegalStateException("Nie znaleziono zasobu db/migration/V1__create_order_table.sql");
            }
            byte[] sqlBytes = in.readAllBytes();
            stmt.execute(new String(sqlBytes, StandardCharsets.UTF_8));
            logger.info("🗄️  Załadowano schemat bazy (V1).");

            try (var checkStmt = conn.createStatement()) {
                var rs = checkStmt.executeQuery("SELECT to_regclass('public.\"ORDERS\"');"); // Sprawdź, czy tabela istnieje w schemacie public
                if (rs.next() && rs.getObject(1) != null) {
                    logger.info("✅ Tabela \"ORDERS\" istnieje w schemacie public.");
                } else {
                    logger.error("❌ Tabela \"ORDERS\" NIE ZNALEZIONA w schemacie public po migracji!");
                    throw new RuntimeException("Tabela \"ORDERS\" nie została utworzona poprawnie.");
                }
            }

        } catch (IOException | SQLException e) {
            throw new RuntimeException("Błąd przy ładowaniu schematu DB", e);
        }
    }

    protected Properties loadProperties(String fileName) {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                log.warn("Nie znaleziono pliku konfiguracyjnego: {}. Wartości domyślne będą użyte.", fileName);
                return props;
            }
            props.load(input);
        } catch (IOException ex) {
            log.error("Błąd podczas ładowania pliku konfiguracyjnego: {}", fileName, ex);
            throw new RuntimeException("Nie można załadować konfiguracji.", ex);
        }
        return props;
    }

    protected ConnectionFactory createRabbitMqConnectionFactory(String host, int port, String user, String pass) {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setHost(host);
        cf.setPort(port);
        cf.setUsername(user);
        cf.setPassword(pass);
        return cf;
    }
}
