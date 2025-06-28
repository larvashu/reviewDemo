package app.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an order before processing.
 * Using Java Records for concise, immutable data carriers.
 */
public record Order(UUID id, BigDecimal amount, String currency) {
    public Order {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Order amount cannot be negative: " + amount);
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be a 3-letter code: " + currency);
        }
    }
}
