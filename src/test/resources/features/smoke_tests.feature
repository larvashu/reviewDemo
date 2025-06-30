Feature: Smoke Tests

  As a developer or operator
  I want to verify that all core components (database, message queue, worker) are up and running
  So that I know the system is healthy before launching full E2E tests

  @Smoke
  Scenario: Database is reachable and orders table exists
    Given Testcontainers PostgreSQL is up
    Then I can insert and read a dummy record in the orders table

  @Smoke
  Scenario: RabbitMQ is reachable and queue exists
    Given RabbitMQ is up
    Then I can publish and consume a dummy message on "order_queue"

  @Smoke
  Scenario: Worker can start and stop cleanly
    Given a running OrderWorker
    Then worker thread is alive
    When I stop the worker
    Then worker thread is not alive
##Tu dalbym testy sprawdzania wersji itd.