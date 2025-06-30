package junit;

import app.model.Order;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        BigDecimal amount = new BigDecimal("100.00").setScale(2, RoundingMode.HALF_UP);
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
    @CsvSource({
            "150.00,EUR,34.50,184.50",
            "75.25,GBP,17.31,92.56",
            "0.00,USD,0.00,0.00",
            "10.90,PLN,2.51,13.41",
            "10.87,PLN,2.50,13.37",
            "10.86,PLN,2.50,13.36",
            "4.34,PLN,1.00,5.34",
            "4.35,PLN,1.00,5.35"
    })
    @DisplayName("Powinien przetwarzać zamówienia z różnymi kwotami i walutami, weryfikując VAT i Total")
    void shouldProcessVariousOrdersWithCalculations(String amountStr, String currency, String expectedVatStr, String expectedTotalStr) throws Exception {
        log.info("--- PARAMETERIZED TEST: shouldProcessVariousOrdersWithCalculations START for amount={}, currency={} ---", amountStr, currency);
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal(amountStr).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedVat = new BigDecimal(expectedVatStr).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedTotal = new BigDecimal(expectedTotalStr).setScale(2, RoundingMode.HALF_UP);

        Order orderToInsert = dbSteps.givenOrderInDatabase(id, amount, currency);
        orderIdsToClean.add(id.toString());

        // WHEN: Oczekujemy wiadomości w kolejce
        String msg = mqSteps.waitForMessage(defaultMessageTimeout);

        mqSteps.assertProcessedOrder(msg, id.toString(), amount, currency, expectedVat, expectedTotal);
        dbSteps.thenOrderShouldHaveVatAndTotalAmountsInDatabase(id, amount, expectedVat, expectedTotal);

        log.info("--- PARAMETERIZED TEST: shouldProcessVariousOrdersWithCalculations END ---");
    }

    @Test
    @DisplayName("Powinien odrzucić zamówienie z negatywną kwotą")
    void shouldRejectNegativeAmount() {
        log.info("--- TEST: shouldRejectNegativeAmount START ---");

        UUID id = UUID.randomUUID();
        BigDecimal neg = new BigDecimal("-10.00").setScale(2, RoundingMode.HALF_UP);
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