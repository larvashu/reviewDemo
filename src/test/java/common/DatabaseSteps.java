package common;

import app.model.Order;
import app.model.ProcessedOrder; // Dodaj import
import app.repository.OrderRepository;
import io.qameta.allure.Step;
import org.assertj.core.api.SoftAssertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseSteps {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSteps.class);

    private final OrderRepository orderRepository;
    private static final BigDecimal VAT_RATE = new BigDecimal("0.23");
    private static final int SCALE = 2;

    public DatabaseSteps(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Step("Given the ORDERS table is empty")
    public void truncateOrdersTable() {
        log.debug("Database step: Truncating ORDERS table...");
        orderRepository.truncateOrdersTable();
        log.info("Database step: ORDERS table truncated successfully.");
    }

    @Step("Given an order with ID '{id}', amount '{amount}', and currency '{currency}' is inserted into the database")
    public Order givenOrderInDatabase(UUID id, BigDecimal amount, String currency) {
        Order order = new Order(id, amount, currency);
        orderRepository.insertOrder(order);
        log.info("Database step: Order {} inserted into DB.", order.id());
        return order;
    }

    @Step("When finding the order with ID '{orderId}' in the database")
    public ProcessedOrder whenFindOrderInDatabase(UUID orderId) {
        log.debug("Database step: Attempting to find order with ID {}.", orderId);
        ProcessedOrder foundOrder = orderRepository.findOrderById(orderId); // Wywołanie zwróci ProcessedOrder
        if (foundOrder == null) {
            log.warn("Database step: Order with ID {} not found in DB.", orderId);
        } else {
            log.info("Database step: Order with ID {} found in DB.", orderId);
        }
        return foundOrder;
    }

    @Step("And the order with ID '{orderId}' should have VAT amount '{expectedVatAmount}' and total amount '{expectedTotalAmount}' in the database")
    public void thenOrderShouldHaveVatAndTotalAmountsInDatabase(
            UUID orderId, BigDecimal originalAmount) {

        ProcessedOrder foundOrder = orderRepository.findOrderById(orderId);
        assertThat(foundOrder)
                .as("Order with ID %s should exist in the database for VAT and total verification", orderId)
                .isNotNull();

        BigDecimal expectedVat = originalAmount.multiply(VAT_RATE).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal expectedTotal = originalAmount.add(expectedVat);

        assertThat(foundOrder.vatAmount())
                .as("VAT amount for order ID %s should be %s", orderId, expectedVat)
                .isEqualByComparingTo(expectedVat);
        assertThat(foundOrder.totalAmount())
                .as("Total amount for order ID %s should be %s", orderId, expectedTotal)
                .isEqualByComparingTo(expectedTotal);

        log.info("Database assertion: Order ID {} VAT ({}) and Total ({}) amounts verified in DB.",
                orderId, foundOrder.vatAmount(), foundOrder.totalAmount());
    }

    public void thenOrderShouldHaveVatAndTotalAmountsInDatabase(UUID orderId, BigDecimal originalAmount, BigDecimal expectedVat, BigDecimal expectedTotal) {
        log.info("Weryfikuję VAT i kwotę całkowitą dla zamówienia ID {} w bazie danych: Original={}, Expected VAT={}, Expected Total={}",
                orderId, originalAmount, expectedVat, expectedTotal);
        ProcessedOrder order = whenFindOrderInDatabase(orderId);

        assertThat(order)
                .as("Zamówienie z ID %s powinno istnieć w bazie danych.", orderId)
                .isNotNull();

        // Używamy scale(2, RoundingMode.HALF_UP) dla porównania BigDecimal, aby uniknąć problemów z precyzją
        assertThat(order.originalAmount().setScale(2, RoundingMode.HALF_UP))
                .as("Oryginalna kwota w bazie danych powinna być zgodna dla ID %s.", orderId)
                .isEqualByComparingTo(originalAmount.setScale(2, RoundingMode.HALF_UP));
        assertThat(order.vatAmount())
                .as("Kwota VAT w bazie danych powinna być zgodna dla ID %s.", orderId)
                .isEqualByComparingTo(expectedVat.setScale(2, RoundingMode.HALF_UP));
        assertThat(order.totalAmount())
                .as("Kwota całkowita w bazie danych powinna być zgodna dla ID %s.", orderId)
                .isEqualByComparingTo(expectedTotal.setScale(2, RoundingMode.HALF_UP));

        log.info("Weryfikacja VAT i kwoty całkowitej w bazie danych dla zamówienia ID {} zakończona pomyślnie.", orderId);
    }

    public void deleteOrdersByIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            log.info("Database step: No specific order IDs to delete.");
            return;
        }
        try {
            orderRepository.deleteOrdersByIds(orderIds); // Metoda do zaimplementowania w OrderRepository
            log.info("Database step: Deleted {} orders with IDs: {}", orderIds.size(), orderIds);
        } catch (Exception e) {
            log.error("Failed to delete orders by IDs {}: {}", orderIds, e.getMessage(), e);
            throw new RuntimeException("Failed to delete orders by IDs", e);
        }
    }


}