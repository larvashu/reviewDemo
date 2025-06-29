package junit;

import app.model.Order;
import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

import common.ManualEnvironment; // Bezpośredni import ManualEnvironment

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("prodlike") // Oznaczenie, że to test integracyjny/prodlike
public class OrderProcessingTest {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingTest.class);

    private OrderRepository repo;
    private RabbitMqClient  rmqClient;
    private String          queueName;

    // Bezpośrednio referencja do ManualEnvironment
    private static ManualEnvironment manualEnvironment;

    @BeforeAll // Metoda statyczna dla @BeforeAll w TestInstance.Lifecycle.PER_CLASS
    static void setupManualTestEnvironment() throws Exception {
        log.info("--- JUnit @BeforeAll: Rozpoczynam inicjalizację dedykowanego środowiska MANUALNEGO ---");

        manualEnvironment = new ManualEnvironment(); // Tworzymy instancję ManualEnvironment
        manualEnvironment.initOnce(); // Inicjalizujemy środowisko manualne

        log.info("--- JUnit @BeforeAll: Środowisko MANUALNE zostało zainicjalizowane. ---");
    }

    @BeforeAll // Ta metoda nie jest static, bo operuje na niestatycznych polach repo, rmqClient
    void setupConnectionsForManualEnv() throws Exception {
        log.info("--- JUnit @BeforeAll: Ustawiam połączenia z środowiska MANUALNEGO ---");
        repo      = new OrderRepository(manualEnvironment.getDslContext());
        rmqClient = new RabbitMqClient(manualEnvironment.getRabbitMqConnectionFactory());
        queueName = manualEnvironment.getTestProperties().getProperty("app.queue.name", "processed_orders");

        log.info("--- JUnit @BeforeAll: Łączę się z RabbitMQ i deklaruję kolejkę '{}' ---", queueName);
        rmqClient.connectAndDeclareQueue(queueName);
        log.info("--- JUnit @BeforeAll: Połączono z bazą danych i RabbitMQ dla kolejki: {} ---", queueName);
    }

    @AfterAll
    void teardown() throws Exception {
        log.info("--- JUnit @AfterAll: Rozpoczynam zamykanie środowiska MANUALNEGO ---");
        if (rmqClient != null) {
            log.info("--- JUnit @AfterAll: Zamykam połączenie z RabbitMQ ---");
            rmqClient.close();
        }
        if (manualEnvironment != null) {
            manualEnvironment.shutdown(); // Zamykamy środowisko manualne
        }
        log.info("--- JUnit @AfterAll: Środowisko MANUALNE zamknięte ---");
    }

    @BeforeEach
    void cleanBefore() throws Exception {
        log.info("--- JUnit @BeforeEach: Rozpoczynam czyszczenie środowiska przed testem ---");
        repo.truncateOrdersTable();
        log.info("--- JUnit @BeforeEach: Tabela 'ORDERS' wyczyszczona. ---");
        rmqClient.clearQueue();
        log.info("--- JUnit @BeforeEach: Kolejka RabbitMQ '{}' wyczyszczona. ---", queueName);
        log.info("--- JUnit @BeforeEach: Środowisko wyczyszczone pomyślnie. ---");
    }

    @Test
    void shouldProcessNewOrder() throws Exception {
        log.info("--- TEST: shouldProcessNewOrder START ---");
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("200.00")
                .setScale(2, RoundingMode.HALF_UP);

        Order orderToInsert = new Order(id, amount, "PLN");
        repo.insertOrder(orderToInsert);
        log.info("Test: Wstawiono zamówienie do DB: {}", orderToInsert);

        log.info("Test: Oczekuję na wiadomość w kolejce '{}' przez {} sekund...", queueName, Duration.ofSeconds(30).getSeconds());
        String msg = rmqClient.getMessageFromQueue(Duration.ofSeconds(30));
        assertThat(msg).as("Oczekiwano przetworzonej wiadomości od workera w kolejce.").isNotNull();
        log.info("Test: Wiadomość odebrana z kolejki: {}", msg);

        JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
        assertThat(json.get("id").getAsString()).isEqualTo(id.toString());

        BigDecimal expectedVat = amount.multiply(new BigDecimal("0.23")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedTotal = amount.add(expectedVat);

        assertThat(json.has("vatAmount")).as("Wiadomość powinna zawierać vatAmount").isTrue();
        assertThat(json.has("totalAmount")).as("Wiadomość powinna zawierać totalAmount").isTrue();

        assertThat(new BigDecimal(json.get("vatAmount").getAsString()))
                .as("Kwota VAT w wiadomości")
                .isEqualByComparingTo(expectedVat);
        assertThat(new BigDecimal(json.get("totalAmount").getAsString()))
                .as("Całkowita kwota po VAT w wiadomości")
                .isEqualByComparingTo(expectedTotal);

        log.info("Test: Wiadomość dla zamówienia ID: {} poprawnie przetworzona i zweryfikowana.", id);
        log.info("--- TEST: shouldProcessNewOrder END ---");
    }

    @ParameterizedTest
    @CsvSource({
            "150.00,EUR,184.50",
            "75.25,GBP,92.56",
            "0.00,USD,0.00"
    })
    void shouldProcessVariousOrders(String amountStr, String currency, String expectedTotalStr) throws Exception {
        log.info("--- PARAMETERIZED TEST: shouldProcessVariousOrders START for amount={}, currency={} ---", amountStr, currency);
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal(amountStr)
                .setScale(2, RoundingMode.HALF_UP);

        Order orderToInsert = new Order(id, amount, currency);
        repo.insertOrder(orderToInsert);
        log.info("Test: Wstawiono zamówienie do DB: {}", orderToInsert);

        log.info("Test: Oczekuję na wiadomość w kolejce '{}' dla zamówienia ID: {} przez {} sekund...", queueName, id, Duration.ofSeconds(30).getSeconds());
        String msg = rmqClient.getMessageFromQueue(Duration.ofSeconds(30));
        assertThat(msg).as("Oczekiwano przetworzonej wiadomości od workera w kolejce.").isNotNull();
        log.info("Test: Wiadomość odebrana z kolejki: {}", msg);

        JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
        assertThat(json.get("id").getAsString()).isEqualTo(id.toString());
        assertThat(json.has("totalAmount")).as("Wiadomość powinna zawierać totalAmount").isTrue();
        assertThat(new BigDecimal(json.get("totalAmount").getAsString()))
                .as("Całkowita kwota po VAT w wiadomości")
                .isEqualByComparingTo(new BigDecimal(expectedTotalStr));
        log.info("Test: Wiadomość dla zamówienia ID: {} poprawnie przetworzona i zweryfikowana.", id);
        log.info("--- PARAMETERIZED TEST: shouldProcessVariousOrders END ---");
    }

    @Test
    void shouldRejectNegativeAmount() {
        log.info("--- TEST: shouldRejectNegativeAmount START ---");
        UUID id = UUID.randomUUID();
        BigDecimal neg = new BigDecimal("-10.00")
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Test: Próba wstawienia zamówienia z negatywną kwotą: {} {}", neg, "PLN");
        assertThatThrownBy(() ->
                repo.insertOrder(new Order(id, neg, "PLN"))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
        log.info("Test: Poprawnie złapano wyjątek dla negatywnej kwoty.");
        log.info("--- TEST: shouldRejectNegativeAmount END ---");
    }
}