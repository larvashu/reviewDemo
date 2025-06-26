package common;

import app.model.Order;
import app.repository.OrderRepository;
import io.qameta.allure.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provides database-related steps for E2E tests,
 * enhancing readability and reusability.
 * Integrated with Allure for detailed reporting.
 */
public class DatabaseSteps {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSteps.class);

    private final OrderRepository orderRepository;

    // --------------------------------------------------------------------- //
    // CONSTRUCTOR                                                           //
    // --------------------------------------------------------------------- //

    public DatabaseSteps(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // --------------------------------------------------------------------- //
    // GIVEN                                                                 //
    // --------------------------------------------------------------------- //

    @Step("Given the ORDERS table is empty")
    public void truncateOrdersTable() {
        orderRepository.truncateOrdersTable();
        log.info("Database step: ORDERS table truncated.");
    }

    @Step("Given an order with ID '{id}', amount '{amount}', and currency '{currency}' is inserted into the database")
    public Order givenOrderInDatabase(UUID id, BigDecimal amount, String currency) {
        Order order = new Order(id, amount, currency);
        orderRepository.insertOrder(order);
        log.info("Database step: Order {} inserted.", order.id());
        return order;
    }

    // --------------------------------------------------------------------- //
    // WHEN                                                                  //
    // --------------------------------------------------------------------- //

    @Step("When finding the order with ID '{orderId}' in the database")
    public Order whenFindOrderInDatabase(UUID orderId) {
        log.info("Database step: Attempting to find order with ID {}.", orderId);
        Order foundOrder = orderRepository.findOrderById(orderId);
        if (foundOrder == null) {
            log.warn("Database step: Order with ID {} not found.", orderId);
        } else {
            log.info("Database step: Order with ID {} found: {}.", orderId, foundOrder);
        }
        return foundOrder;
    }

    // --------------------------------------------------------------------- //
    // THEN / ASSERTIONS                                                     //
    // --------------------------------------------------------------------- //

    @Step("Then the order with ID '{orderId}' should exist in the database")
    public void thenOrderShouldExistInDatabase(UUID orderId) {
        Order foundOrder = orderRepository.findOrderById(orderId);
        assertThat(foundOrder)
                .as("Order with ID %s should exist in the database", orderId)
                .isNotNull();
        log.info("Database assertion: Order with ID {} successfully verified to exist.", orderId);
    }

    @Step("And the order with ID '{orderId}' should have amount '{expectedAmount}' and currency '{expectedCurrency}'")
    public void thenOrderShouldHaveDetailsInDatabase(UUID orderId,
                                                     BigDecimal expectedAmount,
                                                     String expectedCurrency) {

        Order foundOrder = orderRepository.findOrderById(orderId);
        assertThat(foundOrder)
                .as("Order with ID %s should exist in the database for detail verification", orderId)
                .isNotNull();

        assertThat(foundOrder.amount())
                .as("Amount for order ID %s should be %s", orderId, expectedAmount)
                .isEqualByComparingTo(expectedAmount);

        assertThat(foundOrder.currency())
                .as("Currency for order ID %s should be %s", orderId, expectedCurrency)
                .isEqualTo(expectedCurrency);

        log.info("Database assertion: Order ID {} details (amount, currency) verified.", orderId);
    }
}
