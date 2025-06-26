package app.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents an order after being processed (e.g., with VAT applied).
 */
public record ProcessedOrder(UUID id, BigDecimal originalAmount, String currency, BigDecimal vatAmount, BigDecimal totalAmount) {}