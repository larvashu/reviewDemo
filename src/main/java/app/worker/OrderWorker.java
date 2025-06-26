package app.worker;

import app.model.Order;
import app.model.ProcessedOrder;
import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import com.google.gson.Gson;                 // ← zamiast Jacksona

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderWorker implements Runnable {

    private static final BigDecimal VAT = new BigDecimal("0.23");

    private final OrderRepository repo;
    private final RabbitMqClient  mq;
    private final Gson gson = new Gson();   // ← nowy serializer
    private final String queue;
    private volatile boolean running = true;

    public OrderWorker(OrderRepository repo,
                       RabbitMqClient mq,
                       String queue) {
        this.repo   = repo;
        this.mq     = mq;
        this.queue  = queue;
    }

    @Override public void run() {
        while (running) {
            repo.findUnprocessed().forEach(this::process);
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
    }

    private void process(Order o) {
        BigDecimal vat   = o.amount().multiply(VAT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = o.amount().add(vat);

        repo.markProcessed(o.id(), vat, total);

        ProcessedOrder po = new ProcessedOrder(
                o.id(), o.amount(), o.currency(), vat, total);

        try {
            String json = gson.toJson(po);                // ← serializacja
            mq.publishMessage("", queue, json);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void stop() { running = false; }
}
