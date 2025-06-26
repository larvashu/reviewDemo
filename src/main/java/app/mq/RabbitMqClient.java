package app.mq;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Prosty klient RabbitMQ używany w E2E.
 */
public class RabbitMqClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqClient.class);

    private final ConnectionFactory connectionFactory;
    private Connection connection;
    private Channel    channel;
    private String     queueName;

    public RabbitMqClient(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /* ------------------------------------------------ konfiguracja / lifecycle */

    public void connectAndDeclareQueue(String queueName) throws Exception {
        this.queueName = queueName;

        connection = connectionFactory.newConnection();
        channel    = connection.createChannel();
        channel.queueDeclare(queueName, false, false, false, null);

        log.info("Connected to RabbitMQ and queue '{}' declared.", queueName);
    }

    @Override public void close() {
        try {
            if (channel    != null && channel.isOpen())    channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (IOException | TimeoutException e) {
            log.warn("Problem while closing RabbitMQ resources", e);
        }
    }

    /* ------------------------------------------------ operacje na kolejce */

    public void clearQueue() throws IOException {
        channel.queuePurge(queueName);
        log.info("Queue '{}' purged.", queueName);
    }

    public String getMessageFromQueue(Duration timeout) throws InterruptedException, IOException {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            GetResponse resp = channel.basicGet(queueName, true);
            if (resp != null) {
                return new String(resp.getBody(), StandardCharsets.UTF_8);
            }
            Thread.sleep(200);
        }
        return null;    // timeout
    }

    /** Uniwersalna publikacja – wymagana przez OrderWorker. */
    public void publishMessage(String exchange,
                               String routingKey,
                               String body) throws IOException {
        channel.basicPublish(exchange, routingKey, null, body.getBytes(StandardCharsets.UTF_8));
        log.info("Message published to '{}'.", routingKey);
    }
}
