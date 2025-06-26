package worker;

import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import app.worker.OrderWorker;
import common.TestcontainersSetup;

public class OrderWorkerMain {

    private static final String QUEUE = "processed_orders";

    public static void main(String[] args) throws Exception {

        OrderRepository repo = new OrderRepository(TestcontainersSetup.dslContext);
        RabbitMqClient  mq   = new RabbitMqClient(TestcontainersSetup.rabbitMqCF);
        mq.connectAndDeclareQueue(QUEUE);

        OrderWorker worker = new OrderWorker(repo, mq, QUEUE);
        Thread t = new Thread(worker, "order-worker");
        t.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            worker.stop();
            try { t.join(500); } catch (InterruptedException ignored) {}
            mq.close();
        }));

        System.out.println("OrderWorker running – CTRL-C aby zakończyć.");
        t.join();
    }
}
