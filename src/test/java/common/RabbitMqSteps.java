package common;

import app.model.Order;
import app.model.ProcessedOrder;
import app.mq.RabbitMqClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jayway.jsonpath.JsonPath;
import io.qameta.allure.Step;
import org.assertj.core.api.SoftAssertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provides RabbitMQ-related steps for E2E tests,
 * enhancing readability and reusability.
 * Integrated with Allure for detailed reporting.
 */
public class RabbitMqSteps {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqSteps.class);

    private final RabbitMqClient rabbitMqClient;
    private final String         queueName;

    private static final BigDecimal VAT_RATE = new BigDecimal("0.23");
    private static final int        SCALE    = 2; // Precyzja dla obliczeń BigDecimal

    // --------------------------------------------------------------------- //
    // CONSTRUCTOR                                                           //
    // --------------------------------------------------------------------- //

    public RabbitMqSteps(RabbitMqClient rabbitMqClient, String queueName) {
        this.rabbitMqClient = rabbitMqClient;
        this.queueName      = queueName;
    }

    // --------------------------------------------------------------------- //
    // GIVEN                                                                 //
    // --------------------------------------------------------------------- //

    @Step("Given the RabbitMQ queue '{queueName}' is purged")
    public void purgeQueue() {
        log.debug("RabbitMQ step: Purging queue '{}'...", queueName);
        try {
            rabbitMqClient.clearQueue();
            log.info("RabbitMQ step: Queue '{}' purged successfully.", queueName);
        } catch (IOException e) {
            log.error("RabbitMQ step: Failed to purge queue '{}'.", queueName, e);
            throw new RuntimeException("Could not purge RabbitMQ queue: " + queueName, e);
        }
    }

    // --------------------------------------------------------------------- //
    // WHEN                                                                  //
    // --------------------------------------------------------------------- //

    @Step("When waiting for a message from queue '{queueName}' (timeout {timeout.seconds}s)")
    public String waitForMessage(Duration timeout) {
        log.debug("RabbitMQ step: Waiting for message from queue '{}' with timeout {}s...", queueName, timeout.getSeconds());
        String msg;
        try {
            msg = rabbitMqClient.getMessageFromQueue(timeout);
            assertThat(msg)
                    .as("Expected a message within %d seconds from queue '%s'", timeout.getSeconds(), queueName)
                    .isNotNull();
            log.info("RabbitMQ step: Message received from queue '{}'.", queueName);
            return msg;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("RabbitMQ step: Thread interrupted while waiting for message from queue '{}'.", queueName, e);
            throw new RuntimeException("Thread interrupted while waiting for message from queue: " + queueName, e);
        } catch (IOException e) {
            log.error("RabbitMQ step: IOException while waiting for message from queue '{}'.", queueName, e);
            throw new RuntimeException("IOException while waiting for message from queue: " + queueName, e);
        }
    }

    // --------------------------------------------------------------------- //
    // THEN / ASSERTIONS                                                     //
    // --------------------------------------------------------------------- //

    @Step("Then the received message should contain valid processed order data for original order ID '{originalOrder.id}'")
    public ProcessedOrder assertProcessedOrder(String message, Order originalOrder) {
        log.debug("RabbitMQ assertion: Validating processed order message for original order ID {}.", originalOrder.id());

        // ── JSON Parsing ──
        UUID       id              = UUID.fromString(JsonPath.read(message, "$.id"));
        BigDecimal originalAmount  = new BigDecimal(JsonPath.read(message, "$.originalAmount").toString());
        String     currency        = JsonPath.read(message, "$.currency");
        BigDecimal vatAmount       = new BigDecimal(JsonPath.read(message, "$.vatAmount").toString());
        BigDecimal totalAmount     = new BigDecimal(JsonPath.read(message, "$.totalAmount").toString());

        ProcessedOrder po = new ProcessedOrder(id, originalAmount, currency, vatAmount, totalAmount);
        log.info("RabbitMQ assertion: Parsed processed order message: {}", po);

        // ── Assertions against original order ──
        assertThat(id)
                .as("Processed order ID should match original order ID")
                .isEqualTo(originalOrder.id());
        assertThat(originalAmount)
                .as("Processed order original amount should match original order amount")
                .isEqualByComparingTo(originalOrder.amount());
        assertThat(currency)
                .as("Processed order currency should match original order currency")
                .isEqualTo(originalOrder.currency());

        // ── VAT & Total Calculation and Assertion ──
        BigDecimal expVat   = originalOrder.amount().multiply(VAT_RATE).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal expTotal = originalOrder.amount().add(expVat).setScale(SCALE, RoundingMode.HALF_UP);

        assertThat(vatAmount)
                .as("VAT amount in processed message should be %s for original amount %s", expVat, originalOrder.amount())
                .isEqualByComparingTo(expVat);
        assertThat(totalAmount)
                .as("Total amount in processed message should be %s for original amount %s", expTotal, originalOrder.amount())
                .isEqualByComparingTo(expTotal);

        log.info("RabbitMQ assertion: VAT and Total amount validation successful for order ID {}.", originalOrder.id());
        return po;
    }

    @Step("Then the received message should contain valid processed order data for original order ID '{originalOrder.id}'")
    public void assertProcessedOrder(String messageJson, String expectedId, BigDecimal expectedOriginalAmount,
                                     String expectedCurrency, BigDecimal expectedVatAmount, BigDecimal expectedTotalAmount) {
        log.info("Asserting processed order: ID={}, OriginalAmount={}, Currency={}, VAT={}, Total={}",
                expectedId, expectedOriginalAmount, expectedCurrency, expectedVatAmount, expectedTotalAmount);

        assertThat(messageJson).as("Message JSON should not be null or empty").isNotNull().isNotEmpty();

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(messageJson, JsonObject.class);

        SoftAssertions softly = new SoftAssertions();

        // 1. Sprawdzenie obecności kluczy
        softly.assertThat(jsonObject.has("id")).as("JSON message should contain 'id' key").isTrue();
        softly.assertThat(jsonObject.has("originalAmount")).as("JSON message should contain 'originalAmount' key").isTrue();
        softly.assertThat(jsonObject.has("currency")).as("JSON message should contain 'currency' key").isTrue();
        softly.assertThat(jsonObject.has("vatAmount")).as("JSON message should contain 'vatAmount' key").isTrue();
        softly.assertThat(jsonObject.has("totalAmount")).as("JSON message should contain 'totalAmount' key").isTrue();

        // 2. Weryfikacja wartości
        if (jsonObject.has("id")) {
            softly.assertThat(jsonObject.get("id").getAsString())
                    .as("Order ID in message should match expected")
                    .isEqualTo(expectedId);
        }
        if (jsonObject.has("originalAmount")) {
            softly.assertThat(jsonObject.get("originalAmount").getAsBigDecimal().setScale(2, RoundingMode.HALF_UP))
                    .as("Original amount in message should match expected")
                    .isEqualTo(expectedOriginalAmount.setScale(2, RoundingMode.HALF_UP));
        }
        if (jsonObject.has("currency")) {
            softly.assertThat(jsonObject.get("currency").getAsString())
                    .as("Currency in message should match expected")
                    .isEqualTo(expectedCurrency);
        }
        if (jsonObject.has("vatAmount")) {
            softly.assertThat(jsonObject.get("vatAmount").getAsBigDecimal().setScale(2, RoundingMode.HALF_UP))
                    .as("VAT amount in message should match expected")
                    .isEqualTo(expectedVatAmount.setScale(2, RoundingMode.HALF_UP));
        }
        if (jsonObject.has("totalAmount")) {
            softly.assertThat(jsonObject.get("totalAmount").getAsBigDecimal().setScale(2, RoundingMode.HALF_UP))
                    .as("Total amount in message should match expected")
                    .isEqualTo(expectedTotalAmount.setScale(2, RoundingMode.HALF_UP));
        }

        softly.assertAll(); // Zgłoś wszystkie zebrane błędy
    }

}