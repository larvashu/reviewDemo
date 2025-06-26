Feature: Order Processing E2E Workflow

  As a system administrator
  I want to ensure that order data is correctly processed
  So that customers are charged correctly and notifications are sent

  @E2E @Critical
  Scenario: Successfully process a new order with VAT calculation and send to RabbitMQ
    Given a clean environment for order processing
    And a new order with ID "c5e4a8d9-2b0f-4e1b-9d7a-1f0e2c3b4a5d", amount 200.00 and currency "PLN"
    When the order is processed by the system
    Then a message should appear in the "processed_orders" queue within 30 seconds
    And the message should contain the processed order details for "c5e4a8d9-2b0f-4e1b-9d7a-1f0e2c3b4a5d" with VAT applied

  @E2E @Parameterized
  Scenario Outline: Verify order processing for various amounts and currencies
    Given a clean environment for order processing
    And a new order with ID "<orderId>", amount <amount> and currency "<currency>"
    When the order is processed by the system
    Then a message should appear in the "processed_orders" queue within 30 seconds
    And the message should contain the processed order details for "<orderId>" with VAT applied

    Examples:
      | orderId                            | amount | currency |
      | 1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d | 150.00 | EUR      |
      | 9f8e7d6c-5b4a-3f2e-1d0c-9b8a7f6e5d4c | 75.25  | GBP      |
      | a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d | 0.00   | USD      |
      | b5c6d7e8-f9a0-1b2c-3d4e-5f6a7b8c9d0e | -10.00 | PLN      | # Edge case: Negative amount