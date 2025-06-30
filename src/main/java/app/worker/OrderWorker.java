package app.worker;

import app.model.Order;
import app.model.ProcessedOrder;
import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class OrderWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OrderWorker.class);

    private final OrderRepository orderRepository;
    private final RabbitMqClient rabbitMqClient;
    private final String          queueName;

    private volatile boolean running = true;

    private static final BigDecimal VAT_RATE = new BigDecimal("0.23");
    private static final int        SCALE    = 2;

    public OrderWorker(OrderRepository orderRepository, RabbitMqClient rabbitMqClient, String queueName) {
        this.orderRepository = orderRepository;
        this.rabbitMqClient = rabbitMqClient;
        this.queueName = queueName;
    }

    @Override
    public void run() {
        log.info("OrderWorker started...");
        while (running) {
            try {
                int unprocessedCount = orderRepository.getUnprocessedCount();
                if (unprocessedCount > 0) {
                    log.info("Found {} unprocessed orders. Processing...", unprocessedCount);
                    List<Order> unprocessedOrders = orderRepository.findUnprocessed();

                    for (Order order : unprocessedOrders) {
                        processOrder(order);
                    }
                } else {
                    Thread.sleep(5000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("OrderWorker thread interrupted. Shutting down.");
                running = false;
            } catch (Exception e) {
                log.error("Error in OrderWorker: {}", e.getMessage(), e);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
        log.info("OrderWorker stopped.");
    }

    private void processOrder(Order order) {
        log.info("Processing order: {}", order.id());
        try {
            BigDecimal vatAmount = order.amount().multiply(VAT_RATE).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal totalAmount = order.amount().add(vatAmount).setScale(SCALE, RoundingMode.HALF_UP);

            ProcessedOrder processedOrder = new ProcessedOrder(
                    order.id(),
                    order.amount(),
                    order.currency(),
                    vatAmount,
                    totalAmount
            );

            orderRepository.updateOrderWithProcessedData(processedOrder);
            log.info("Order {} updated in DB with VAT: {} and Total: {}.", order.id(), vatAmount, totalAmount);

            String message = buildRabbitMqMessage(processedOrder);
            rabbitMqClient.publishMessage("", queueName, message);
            log.info("Order {} message published to RabbitMQ.", order.id());

        } catch (IOException e) {
            log.error("Failed to publish message for order {}: {}", order.id(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to process order {}: {}", order.id(), e.getMessage(), e);
        }
    }

    private String buildRabbitMqMessage(ProcessedOrder processedOrder) {
        return String.format(
                "{\"id\":\"%s\",\"originalAmount\":%s,\"currency\":\"%s\",\"vatAmount\":%s,\"totalAmount\":%s}",
                processedOrder.id(),
                processedOrder.originalAmount().toPlainString(), // ZMIANA TUTAJ
                processedOrder.currency(),
                processedOrder.vatAmount().toPlainString(),     // ZMIANA TUTAJ
                processedOrder.totalAmount().toPlainString()    // ZMIANA TUTAJ
        );
    }

    public void stop() {
        log.info("Stopping OrderWorker...");
        running = false;
    }
}