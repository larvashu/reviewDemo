package junit;

import app.model.Order;
import junit.utils.testData.adnotations.TestData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("prodlike")
@DisplayName("Testy przetwarzania zamówień")
public class OrderProcessingTest extends BaseJUnitTest {
    private List<String> orderIdsToClean = new ArrayList<>();

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingTest.class);

    @BeforeEach
    void setupTestSpecifics() {
        orderIdsToClean.clear();
        assertThat(dbSteps).as("dbSteps powinno być zainicjalizowane").isNotNull();
        assertThat(mqSteps).as("mqSteps powinno być zainicjalizowane").isNotNull();
    }

    @Test
    @DisplayName("Powinien przetworzyć nowe zamówienie i wysłać wiadomość do kolejki")
    void shouldSendNewOrderMessageToQueue()  {
        log.info("--- TEST: shouldSendNewOrderMessageToQueue START ---");
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00").setScale(2, HALF_UP);
        String currency = "PLN";

        // GIVEN: Wstawiamy zamówienie do bazy
        Order orderToInsert = dbSteps.givenOrderInDatabase(id, amount, currency);
        orderIdsToClean.add(id.toString());

        // WHEN: Oczekujemy wiadomości w kolejce
        String msg = mqSteps.waitForMessage(defaultMessageTimeout);

        assertThat(msg).as("Wiadomość nie powinna być pusta").isNotNull().isNotEmpty();
        log.info("Odebrano wiadomość z kolejki. ID: {}", id);
        log.info("--- TEST: shouldSendNewOrderMessageToQueue END ---");
    }


    @ParameterizedTest
    @TestData("junit_testCases/orders.yaml")
    @DisplayName("Powinien przetwarzać zamówienia z różnymi kwotami i walutami, weryfikując VAT i Total")
    void shouldProcessOrders(Map<String, Object> data, Map<String, Object> expected) {
        var orderData = (Map<String, ?>) data.get("order");
        var result    = (Map<String, ?>) expected.get("order_result");
        var id        = UUID.randomUUID();
        var amount    = new BigDecimal(orderData.get("amount").toString()).setScale(2, HALF_UP);
        var currency  = orderData.get("currency").toString();
        var vat       = new BigDecimal(result.get("vat_amount").toString()).setScale(2, HALF_UP);
        var total     = new BigDecimal(result.get("total").toString()).setScale(2, HALF_UP);

        dbSteps.givenOrderInDatabase(id, amount, currency);
        orderIdsToClean.add(id.toString());

        var message = mqSteps.waitForMessage(defaultMessageTimeout);
        mqSteps.assertProcessedOrder(message, id.toString(), amount, currency, vat, total);
        dbSteps.thenOrderShouldHaveVatAndTotalAmountsInDatabase(id, amount, vat, total);
    }

    @Test
    @DisplayName("Powinien odrzucić zamówienie z negatywną kwotą")
    void shouldRejectNegativeAmount() {
        log.info("--- TEST: shouldRejectNegativeAmount START ---");

        UUID id = UUID.randomUUID();
        BigDecimal neg = new BigDecimal("-10.00").setScale(2, HALF_UP);
        String currency = "PLN";

        log.info("Test: Próba wstawienia zamówienia z negatywną kwotą: {} {}", neg, currency);
        assertThatThrownBy(() ->
                dbSteps.givenOrderInDatabase(id, neg, currency)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");

        log.info("Test: Poprawnie złapano wyjątek dla negatywnej kwoty.");
        log.info("--- TEST: shouldRejectNegativeAmount END ---");
    }

    @AfterEach
    void cleanAfterEachTest() {
        log.info("--- OrderProcessingTest @AfterEach: Rozpoczynam czyszczenie środowiska po teście ---");
        try {
            if (!orderIdsToClean.isEmpty()) {
                log.info("Czyszczenie bazy danych: usuwanie rekordów z ORDERS o ID: {}", orderIdsToClean);
                dbSteps.deleteOrdersByIds(orderIdsToClean);
            } else {
                log.info("Baza danych: Brak ID zamówień do usunięcia w tym teście.");
            }
            log.info("--- OrderProcessingTest @AfterEach: Środowisko wyczyszczone po teście pomyślnie. ---");
        } catch (Exception e) {
            log.error("Błąd podczas czyszczenia środowiska po teście: {}", e.getMessage(), e);
            throw new RuntimeException("Błąd podczas czyszczenia po teście", e);
        }
    }
}