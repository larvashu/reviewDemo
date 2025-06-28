Feature: Order Processing E2E Workflow

  As a system administrator
  I want to ensure that order data is correctly processed
  So that customers are charged correctly and messages zawierają właściwe dane

  @E2E @Critical
  Scenario: Successfully process a new order with VAT and send to RabbitMQ
    Given a clean environment for order processing
    And a new order with ID "c5e4a8d9-2b0f-4e1b-9d7a-1f0e2c3b4a5d", amount 200.00 and currency "PLN"
    When the order is processed by the system
    Then a message should appear in the "processed_orders" queue within 30 seconds
    And the message JSON for "c5e4a8d9-2b0f-4e1b-9d7a-1f0e2c3b4a5d" should contain totalAmount 246.00

  @E2E @Parameterized
  Scenario Outline: Verify order processing for various valid amounts and currencies
    Given a clean environment for order processing
    And a new order with ID "<orderId>", amount <amount> and currency "<currency>"
    When the order is processed by the system
    Then a message should appear in the "processed_orders" queue within 30 seconds
    And the message JSON for "<orderId>" should contain totalAmount <expected>

    Examples:
      | orderId                              | amount | currency | expected |
      | 1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d | 150.00 | EUR      | 184.50   |
      | 9f8e7d6c-5b4a-3f2e-1d0c-9b8a7f6e5d4c | 75.25  | GBP      | 92.56    |
      | a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d | 0.00   | USD      | 0.00     |

  @E2E @Edge
  Scenario: Reject creation of order with negative amount
    Given a clean environment for order processing
    When I attempt to insert a new order with ID "b5c6d7e8-f9a0-1b2c-3d4e-5f6a7b8c9d0e", amount -10.00 and currency "PLN"
    Then order creation should fail with IllegalArgumentException
