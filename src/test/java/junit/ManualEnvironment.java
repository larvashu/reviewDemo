package junit;

import com.rabbitmq.client.ConnectionFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Zarządza połączeniami do ręcznie uruchomionych usług (Postgres, RabbitMQ).
 * Oczekuje, że usługi są już dostępne na stałych adresach (localhost).
 * Jeśli usługi nie są dostępne, rzuca wyjątek.
 */
public class ManualTestEnvironment {

    private static final Logger log = LoggerFactory.getLogger(ManualTestEnvironment.class);

    // Wczytaj konfigurację z pliku test.properties
    // To jest nadal to samo test.properties, które będzie używane przez oba środowiska do podstawowych configów
    public static final Properties TEST_PROPERTIES = loadProperties("test.properties");

    public static DataSource dataSource;
    public static DSLContext dslContext;
    public static ConnectionFactory rabbitMqCF;

    private static final AtomicBoolean INITIALISED = new AtomicBoolean(false);

    /**
     * Inicjuje połączenia do manualnie uruchomionych usług. Wywołuje się tylko raz.
     * Jeśli usługi nie są dostępne, rzuca RuntimeException.
     */
    public static synchronized void initOnce() {
        if (INITIALISED.get()) return;

        log.info("⏳  Inicjuję połączenia w trybie RĘCZNYM (zakładam, że usługi są już uruchomione)...");
        try {
            // Konfiguracja PostgreSQL
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:postgresql://" + TEST_PROPERTIES.getProperty("db.host") + ":" +
                           TEST_PROPERTIES.getProperty("db.port") + "/" + TEST_PROPERTIES.getProperty("db.name"));
            hc.setUsername(TEST_PROPERTIES.getProperty("db.user"));
            hc.setPassword(TEST_PROPERTIES.getProperty("db.pass"));
            dataSource = new HikariDataSource(hc);
            dslContext = DSL.using(dataSource, SQLDialect.POSTGRES);
            applySchema(); // Aplikuj schemat do ręcznie podniesionej bazy

            // Konfiguracja RabbitMQ
            rabbitMqCF = new ConnectionFactory();
            rabbitMqCF.setHost(TEST_PROPERTIES.getProperty("rabbitmq.host"));
            rabbitMqCF.setPort(Integer.parseInt(TEST_PROPERTIES.getProperty("rabbitmq.port")));
            rabbitMqCF.setUsername(TEST_PROPERTIES.getProperty("rabbitmq.user"));
            rabbitMqCF.setPassword(TEST_PROPERTIES.getProperty("rabbitmq.pass"));

            // Sprawdź, czy można się połączyć z RabbitMQ
            try (var conn = rabbitMqCF.newConnection()) {
                log.info("??? Pomyślnie połączono z RabbitMQ.");
            }

            log.info("✅  Połączenia do ręcznych usług gotowe.");
            INITIALISED.set(true);

        } catch (Exception e) {
            log.error("Błąd podczas inicjalizacji połączeń do ręcznych usług: " + e.getMessage(), e);
            // WAŻNE: Rzuć wyjątek, aby testy od razu failowały
            throw new RuntimeException("Nie udało się połączyć z ręcznie uruchomionymi usługami. Upewnij się, że Docker Compose i worker są uruchomieni.", e);
        }
    }

    /**
     * Zamyka połączenia do manualnie uruchomionych usług. Nie zatrzymuje samych usług.
     */
    public static void shutdown() {
        if (!INITIALISED.get()) return;

        log.info("🧹  Zamykam połączenia w trybie RĘCZNYM (nie zatrzymuję zewnętrznych usług).");
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
        dslContext = null;
        dataSource = null;
        rabbitMqCF = null;
        INITIALISED.set(false);
    }

    /* ==================================================================== helpers */

    /** Ładuje testowy schemat z classpath (src/test/resources/db/migration/…). */
    private static void applySchema() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            byte[] sqlBytes = ManualTestEnvironment.class // Użyj tej klasy do wczytania zasobu
                    .getClassLoader()
                    .getResourceAsStream("db/migration/V1__create_order_table.sql")
                    .readAllBytes();

            stmt.execute(new String(sqlBytes, StandardCharsets.UTF_8));
            log.info("🗄️  Załadowano schemat bazy (V1).");

        } catch (Exception e) {
            throw new RuntimeException("Błąd przy ładowaniu schematu DB w trybie manualnym", e);
        }
    }

    private static Properties loadProperties(String fileName) {
        Properties props = new Properties();
        try (InputStream input = ManualTestEnvironment.class.getClassLoader().getResourceAsStream(fileName)) { // Użyj tej klasy
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