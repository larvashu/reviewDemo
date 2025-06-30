# features/order_processing.feature

Feature: Order Processing

  Scenario: Successfully process a new order and verify basic message format
    Given a clean environment for order processing
    And a new order with ID "c5e4a8d9-2b0f-4e1b-9d7a-1f0e2c3b4a5d", amount 200.00 and currency "PLN"
    When the order is processed by the system
    Then a message should appear in the "order_queue" queue within 30 seconds
    And the message JSON for "c5e4a8d9-2b0f-4e1b-9d7a-1f0e2c3b4a5d" should be a valid JSON and contain all required keys

  Scenario Outline: Verify order processing for various valid amounts and currencies with calculations
    Given a clean environment for order processing
    And a new order with ID "<orderId>", amount <amount> and currency "<currency>"
    When the order is processed by the system
    Then a message should appear in the "order_queue" queue within 30 seconds
    And the message JSON for "<orderId>" should contain original amount <amount>, currency "<currency>", vat amount <expected_vat> and total amount <expected_total>
    And the order in database for "<orderId>" should have calculated VAT and total amounts based on original amount <amount>

    Examples:
      | orderId                              | amount | currency | expected_vat | expected_total |
      | 1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d | 150.00 | EUR      | 34.50        | 184.50         |
      | 9f8e7d6c-5b4a-3f2e-1d0c-9b8a7f6e5d4c | 75.25  | GBP      | 17.31        | 92.56          |
      | a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d | 0.00   | USD      | 0.00         | 0.00           |
      | b1c2d3e4-f5a6-b7c8-d9e0-f1a2b3c4d5e6 | 10.90  | PLN      | 2.51         | 13.41          |
      | c1d2e3f4-a5b6-c7d8-e9f0-a1b2c3d4e5f6 | 10.87  | PLN      | 2.50         | 13.37          |
      | d1e2f3a4-b5c6-d7e8-f9a0-b1c2d3e4f5a6 | 10.86  | PLN      | 2.50         | 13.36          |
      | e1f2a3b4-c5d6-e7f8-a9b0-c1d2e3f4a5b6 | 4.34   | PLN      | 1.00         | 5.34           |
      | f1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6 | 4.35   | PLN      | 1.00         | 5.35           |

  Scenario: Reject order with negative amount
    Given a clean environment for order processing
    When I attempt to insert a new order with ID "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d", amount -10.00 and currency "PLN"
    Then order creation should fail with IllegalArgumentException
