package cucumber.step;

import app.model.Order;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.cucumber.java.en.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class OrderProcessingStepDefinitions {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingStepDefinitions.class);

    private UUID currentOrderId;
    private Exception caughtException;
    private JsonObject lastMessage;        // <-- tutaj trzymamy ostatni JSON

    public OrderProcessingStepDefinitions() {
        log.info("OrderProcessingStepDefinitions: new instance");
    }

    @Given("a clean environment for order processing")
    public void a_clean_environment_for_order_processing() {
        log.info("Clean environment ready");
        // baza i kolejka czyszczone są już w CucumberHooks
    }

    @Given("a new order with ID {string}, amount {bigdecimal} and currency {string}")
    public void a_new_order_with_id_amount_and_currency(String orderId, BigDecimal amount, String currency) {
        log.info("GIVEN: insert order ID={}, amount={} {}", orderId, amount, currency);
        this.currentOrderId = UUID.fromString(orderId);
        Order order = new Order(currentOrderId, amount.setScale(2, RoundingMode.HALF_UP), currency);
        CucumberHooks.repo.insertOrder(order);
        log.info("Order inserted into DB");
    }

    @When("the order is processed by the system")
    public void the_order_is_processed_by_the_system() {
        log.info("WHEN: worker processes order ID={} in background", currentOrderId);
        // worker działa asynchronicznie
    }

    @Then("a message should appear in the {string} queue within {int} seconds")
    public void a_message_should_appear_in_the_queue_within_seconds(String queue, Integer seconds) throws Exception {
        log.info("THEN: expect message in queue '{}' within {}s", queue, seconds);
        String raw = CucumberHooks.rmqClient.getMessageFromQueue(Duration.ofSeconds(seconds));
        assertThat(raw)
                .as("Expected a message in queue '%s'", queue)
                .isNotNull();
        log.info("Received raw message: {}", raw);

        // Parsujemy i zapisujemy w polu
        this.lastMessage = JsonParser.parseString(raw).getAsJsonObject();
        assertThat(this.lastMessage).as("Valid JSON").isNotNull();
    }

    @Then("the message JSON for {string} should contain totalAmount {bigdecimal}")
    public void the_message_json_for_should_contain_total_amount(String orderId, BigDecimal expectedTotal) {
        assertThat(lastMessage.get("id").getAsString())
                .as("Order ID in message")
                .isEqualTo(orderId);

        BigDecimal actual = new BigDecimal(lastMessage.get("totalAmount").getAsString())
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(actual)
                .as("totalAmount")
                .isEqualByComparingTo(expectedTotal.setScale(2, RoundingMode.HALF_UP));

        log.info("Verified totalAmount {} for order {}", actual, orderId);
    }

    // --- negatywny scenariusz ---

    @When("I attempt to insert a new order with ID {string}, amount {bigdecimal} and currency {string}")
    public void i_attempt_to_insert_a_new_order_with_id_amount_and_currency(String orderId, BigDecimal amount, String currency) {
        log.info("WHEN (neg): attempt insert order ID={}, amount={} {}", orderId, amount, currency);
        this.currentOrderId = UUID.fromString(orderId);
        try {
            Order order = new Order(currentOrderId, amount.setScale(2, RoundingMode.HALF_UP), currency);
            CucumberHooks.repo.insertOrder(order);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("order creation should fail with IllegalArgumentException")
    public void order_creation_should_fail_with_illegal_argument_exception() {
        assertThat(caughtException)
                .as("Expected IllegalArgumentException")
                .isInstanceOf(IllegalArgumentException.class);
        log.info("Correctly caught exception: {}", caughtException.getClass().getSimpleName());
    }
}
