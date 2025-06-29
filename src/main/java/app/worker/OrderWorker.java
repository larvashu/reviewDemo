package app.worker; // Upewnij się, że to jest app.worker, nie worker

import app.model.Order;
import app.model.ProcessedOrder;
import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import com.google.gson.Gson;
import org.slf4j.Logger; // Dodaj import Logger
import org.slf4j.LoggerFactory; // Dodaj import LoggerFactory

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OrderWorker.class); // Inicjalizacja loggera

    private static final BigDecimal VAT = new BigDecimal("0.23");

    private final OrderRepository repo;
    private final RabbitMqClient  mq;
    private final Gson gson = new Gson();
    private final String queue;
    private volatile boolean running = true;

    public OrderWorker(OrderRepository repo,
                       RabbitMqClient mq,
                       String queue) {
        this.repo   = repo;
        this.mq     = mq;
        this.queue  = queue;
        log.info("OrderWorker: Inicjalizacja workera dla kolejki '{}'", queue);
    }

    @Override public void run() {
        log.info("OrderWorker: Uruchamiam pętlę przetwarzania zamówień...");
        while (running) {
            // Logujemy, ile zamówień zostało znalezionych do przetworzenia
            long unprocessedCount = repo.getUnprocessedCount(); // Załóżmy, że repozytorium ma taką metodę
            if (unprocessedCount > 0) {
                log.info("OrderWorker: Znaleziono {} nieprzetworzonych zamówień.", unprocessedCount);
            }
            repo.findUnprocessed().forEach(this::process);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.warn("OrderWorker: Wątek przetwarzania przerwany podczas snu.", e);
                Thread.currentThread().interrupt(); // Przywracamy status przerwania
                running = false; // Zatrzymujemy pętlę
            }
        }
        log.info("OrderWorker: Pętla przetwarzania zamówień zakończona.");
    }

    private void process(Order o) {
        log.info("OrderWorker: Rozpoczynam przetwarzanie zamówienia ID: {}, Kwota: {} {}", o.id(), o.amount(), o.currency());

        BigDecimal vat   = o.amount().multiply(VAT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = o.amount().add(vat);

        repo.markProcessed(o.id(), vat, total);
        log.info("OrderWorker: Zamówienie ID: {} przetworzone w bazie danych. VAT: {}, Total: {}", o.id(), vat, total);

        ProcessedOrder po = new ProcessedOrder(
                o.id(), o.amount(), o.currency(), vat, total);

        try {
            String json = gson.toJson(po);
            mq.publishMessage("", queue, json);
            log.info("OrderWorker: Przetworzona wiadomość dla zamówienia ID: {} opublikowana w kolejce '{}': {}", o.id(), queue, json);
        } catch (IOException ex) {
            log.error("OrderWorker: Błąd podczas publikowania wiadomości dla zamówienia ID: {} w kolejce '{}'", o.id(), queue, ex);
            throw new RuntimeException(ex); // Nadal rzucamy, aby wskazać błąd krytyczny
        }
    }

    public void stop() {
        log.info("OrderWorker: Otrzymano sygnał zatrzymania. Worker zakończy działanie.");
        running = false;
    }
}