package cucumber.step;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.cucumber.java.After;
import io.cucumber.java.en.*;
import org.assertj.core.api.SoftAssertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cucumber.step.BaseCucumberTest.*;
import static org.assertj.core.api.Assertions.*;

public class OrderProcessingStepDefinitions {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingStepDefinitions.class);

    private UUID currentOrderId;
    private Exception caughtException;
    private JsonObject lastMessage;
    private List<String> createdOrderIds = new ArrayList<>();


    public OrderProcessingStepDefinitions() {
        log.info("OrderProcessingStepDefinitions: new instance.");
    }

    @Given("a new order with ID {string}, amount {bigdecimal} and currency {string}")
    public void a_new_order_with_id_amount_and_currency(String orderId, BigDecimal amount, String currency) {
        this.currentOrderId = UUID.fromString(orderId);
        dbSteps.givenOrderInDatabase(currentOrderId, amount.setScale(2, RoundingMode.HALF_UP), currency);
        createdOrderIds.add(currentOrderId.toString());
        log.info("Order inserted into DB via DatabaseSteps: ID={}, Amount={}, Currency={}", currentOrderId, amount, currency);
    }

    @Then("a message should appear in the {string} queue within {int} seconds")
    public void a_message_should_appear_in_the_queue_within_seconds(String queue, Integer seconds) {
        log.info("THEN: Expecting message in queue '{}' within {}s", queue, seconds);
        String rawMessage = mqSteps.waitForMessage(Duration.ofSeconds(seconds));

        assertThat(rawMessage)
                .as("Received message should not be null or empty from queue '%s'", queue)
                .isNotNull()
                .isNotEmpty();

        this.lastMessage = JsonParser.parseString(rawMessage).getAsJsonObject();

        assertThat(this.lastMessage)
                .as("Received message should be a valid JSON object")
                .isNotNull();
        log.info("Received and parsed message: {}", rawMessage);
    }

    @And("the message JSON for {string} should be a valid JSON and contain all required keys")
    public void the_message_json_should_be_valid_and_contain_required_keys(String orderId) {
        log.info("Verifying basic JSON format and required keys for order ID: {}", orderId);
        assertThat(lastMessage).as("Last received message JSON should not be null").isNotNull();

        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(lastMessage.has("id")).as("JSON message should contain 'id' key").isTrue();
        softly.assertThat(lastMessage.has("originalAmount")).as("JSON message should contain 'originalAmount' key").isTrue();
        softly.assertThat(lastMessage.has("currency")).as("JSON message should contain 'currency' key").isTrue();
        softly.assertThat(lastMessage.has("vatAmount")).as("JSON message should contain 'vatAmount' key").isTrue();
        softly.assertThat(lastMessage.has("totalAmount")).as("JSON message should contain 'totalAmount' key").isTrue();

        if (lastMessage.has("id")) {
            softly.assertThat(lastMessage.get("id").getAsString())
                    .as("Order ID in message should match expected")
                    .isEqualTo(orderId);
        }

        softly.assertAll();
        log.info("JSON message for order {} contains all expected keys (softly asserted).", orderId);
    }

    @And("the message JSON for {string} should contain original amount {bigdecimal}, currency {string}, vat amount {bigdecimal} and total amount {bigdecimal}")
    public void the_message_json_for_should_contain_details(String orderId, BigDecimal expectedOriginalAmount,
                                                            String expectedCurrency, BigDecimal expectedVat, BigDecimal expectedTotal) {
        log.info("Verifying detailed JSON content for order ID: {}", orderId);
        assertThat(lastMessage).as("Last received message JSON should not be null").isNotNull();

        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(lastMessage.get("id").getAsString())
                .as("Order ID in message should match expected")
                .isEqualTo(orderId);

        BigDecimal actualOriginalAmount = lastMessage.get("originalAmount").getAsBigDecimal().setScale(2, RoundingMode.HALF_UP);
        softly.assertThat(actualOriginalAmount)
                .as("Original amount in message")
                .isEqualByComparingTo(expectedOriginalAmount.setScale(2, RoundingMode.HALF_UP));

        softly.assertThat(lastMessage.get("currency").getAsString())
                .as("Currency in message")
                .isEqualTo(expectedCurrency);

        BigDecimal actualVat = lastMessage.get("vatAmount").getAsBigDecimal().setScale(2, RoundingMode.HALF_UP);
        softly.assertThat(actualVat)
                .as("VAT amount in message")
                .isEqualByComparingTo(expectedVat.setScale(2, RoundingMode.HALF_UP));

        BigDecimal actualTotal = lastMessage.get("totalAmount").getAsBigDecimal().setScale(2, RoundingMode.HALF_UP);
        softly.assertThat(actualTotal)
                .as("Total amount in message")
                .isEqualByComparingTo(expectedTotal.setScale(2, RoundingMode.HALF_UP));

        softly.assertAll();
        log.info("Verified all amounts and currency for order {}: Original={}, VAT={}, Total={}",
                orderId, actualOriginalAmount, actualVat, actualTotal);
    }

    @And("the order in database for {string} should have calculated VAT and total amounts based on original amount {bigdecimal}")
    public void the_order_in_database_should_have_calculated_details(String orderIdStr, BigDecimal originalAmount) {
        log.info("Verifying order {} details in database based on original amount {}.",
                orderIdStr, originalAmount);

        dbSteps.thenOrderShouldHaveVatAndTotalAmountsInDatabase(
                UUID.fromString(orderIdStr), originalAmount
        );
        log.info("Order {} successfully verified in database using calculated amounts.", orderIdStr);
    }

    @When("I attempt to insert a new order with ID {string}, amount {bigdecimal} and currency {string}")
    public void i_attempt_to_insert_a_new_order_with_id_amount_and_currency(String orderIdStr, BigDecimal amount, String currency) {
        log.info("WHEN (negative case): Attempting to insert order ID={}, amount={} {}", orderIdStr, amount, currency);
        try {
            UUID parsedOrderId = UUID.fromString(orderIdStr);
            this.currentOrderId = parsedOrderId;

            dbSteps.givenOrderInDatabase(parsedOrderId, amount.setScale(2, RoundingMode.HALF_UP), currency);
            createdOrderIds.add(orderIdStr);
        } catch (IllegalArgumentException e) {
            caughtException = e;
            log.error("Caught expected IllegalArgumentException during order insertion attempt: {}", e.getMessage());
        } catch (Exception e) {
            caughtException = e;
            log.error("Caught unexpected exception during order insertion attempt: {}", e.getMessage(), e);
        }
    }

    @Then("order creation should fail with IllegalArgumentException")
    public void order_creation_should_fail_with_illegal_argument_exception() {
        assertThat(caughtException)
                .as("Expected IllegalArgumentException to be thrown")
                .isInstanceOf(IllegalArgumentException.class);
        log.info("Correctly caught exception: {}", caughtException.getClass().getSimpleName());
    }

    @Given("a clean environment for order processing")
    public void a_clean_environment_for_order_processing() {
        log.info("Clean environment preparation is handled by BaseCucumberTest @BeforeAll or specific hooks.");
    }

    @When("the order is processed by the system")
    public void the_order_is_processed_by_the_system() {
        log.info("WHEN: Worker processes order in background (worker is assumed to be running and listening to DB changes)");
    }

    @After
    public void cleanUpAfterScenario() {
        log.info("--- @After (Cucumber): Starting scenario cleanup ---");
        try {
            if (!createdOrderIds.isEmpty()) {
                log.info("Database cleanup: Deleting order records with IDs: {}", createdOrderIds);
                dbSteps.deleteOrdersByIds(createdOrderIds);
                createdOrderIds.clear();
            } else {
                log.info("Database cleanup: No order IDs to delete in this scenario.");
            }

            // Czyszczenie kolejki RabbitMQ - odpuszczam sobie implementacje czyszczenia pojedynczych kolejek - K.G.
            log.info("RabbitMQ cleanup: Purging queue '{}' of any remaining messages...", queueName);
            mqSteps.purgeQueue();

            log.info("--- @After (Cucumber): Scenario cleanup completed successfully. ---");
        } catch (Exception e) {
            log.error("Error during scenario cleanup: {}", e.getMessage(), e);
        }
    }
}