package app.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents an order before processing.
 * Using Java Records for concise, immutable data carriers.
 */
public record Order(UUID id, BigDecimal amount, String currency) {}