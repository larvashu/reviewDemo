package common;

import app.model.Order;
import app.model.ProcessedOrder;
import app.mq.RabbitMqClient;
import com.jayway.jsonpath.JsonPath;
import io.qameta.allure.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMqSteps {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqSteps.class);

    private final RabbitMqClient rabbitMqClient;
    private final String         queueName;

    private static final BigDecimal VAT_RATE = new BigDecimal("0.23");
    private static final int        SCALE    = 2;

    public RabbitMqSteps(RabbitMqClient rabbitMqClient, String queueName) {
        this.rabbitMqClient = rabbitMqClient;
        this.queueName      = queueName;
    }

    /* ------------------------------------------------------------------  GIVEN  */

    @Step("RabbitMQ: czyszczę kolejkę '{queueName}'")
    public void purgeQueue() {
        try {
            rabbitMqClient.clearQueue();
        } catch (IOException e) {
            throw new RuntimeException("Nie mogę wyczyścić kolejki RabbitMQ", e);
        }
    }

    /* ------------------------------------------------------------------  WHEN  */

    @Step("Czekam na wiadomość z kolejki '{queueName}' (timeout {timeout.seconds}s)")
    public String waitForMessage(Duration timeout) {
        try {
            String msg = rabbitMqClient.getMessageFromQueue(timeout);
            assertThat(msg)
                    .as("Oczekiwano wiadomości w ciągu %d s", timeout.getSeconds())
                    .isNotNull();
            log.info("RabbitMQ: wiadomość odebrana.");
            return msg;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Wątek przerwany podczas oczekiwania na wiadomość", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* ------------------------------------------------------------------  THEN  */

    @Step("Waliduję, że wiadomość zawiera prawidłowe dane przetworzonego zamówienia")
    public ProcessedOrder assertProcessedOrder(String message, Order originalOrder) {

        // ── parsowanie JSON ──
        UUID       id              = UUID.fromString(JsonPath.read(message, "$.id"));
        BigDecimal originalAmount  = new BigDecimal(JsonPath.read(message, "$.originalAmount").toString());
        String     currency        = JsonPath.read(message, "$.currency");
        BigDecimal vatAmount       = new BigDecimal(JsonPath.read(message, "$.vatAmount").toString());
        BigDecimal totalAmount     = new BigDecimal(JsonPath.read(message, "$.totalAmount").toString());

        ProcessedOrder po = new ProcessedOrder(id, originalAmount, currency, vatAmount, totalAmount);
        log.info("✉  Parsed processed-order: {}", po);

        // ── asercje zgodności z oryginałem ──
        assertThat(id).isEqualTo(originalOrder.id());
        assertThat(originalAmount).isEqualByComparingTo(originalOrder.amount());
        assertThat(currency).isEqualTo(originalOrder.currency());

        // ── obliczenie VAT & total ──
        BigDecimal expVat   = originalOrder.amount().multiply(VAT_RATE).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal expTotal = originalOrder.amount().add(expVat).setScale(SCALE, RoundingMode.HALF_UP);

        assertThat(vatAmount)  .isEqualByComparingTo(expVat);
        assertThat(totalAmount).isEqualByComparingTo(expTotal);

        log.info("✅  Walidacja VAT/Total zakończona sukcesem.");
        return po;
    }
}
