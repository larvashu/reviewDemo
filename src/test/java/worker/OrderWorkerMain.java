// src/main/java/worker/OrderWorkerMain.java
package worker;

import app.mq.RabbitMqClient;
import app.repository.OrderRepository;
import app.worker.OrderWorker;
import com.rabbitmq.client.ConnectionFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import common.AbstractTestEnvironment;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class OrderWorkerMain {

    private static final Logger log = LoggerFactory.getLogger(OrderWorkerMain.class);
    private static final String CONFIG_FILE_NAME = "test.properties";
    private static DataSource currentDataSource;

    public static void main(String[] args) throws Exception {
        Properties appProps = loadApplicationProperties(CONFIG_FILE_NAME);
        log.info("Loaded properties from {}:", CONFIG_FILE_NAME);
        appProps.forEach((k, v) -> log.info("  {} = {}", k, v));

        String dbHost = appProps.getProperty("db.host");
        String dbPort = appProps.getProperty("db.port");
        String dbName = appProps.getProperty("db.name");
        String dbUser = appProps.getProperty("db.user");
        String dbPass = appProps.getProperty("db.pass");

        String dbUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        log.info("Constructed JDBC URL: {}", dbUrl);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(dbUrl);
        hc.setUsername(dbUser);
        hc.setPassword(dbPass);
        currentDataSource = new HikariDataSource(hc);

        AbstractTestEnvironment.applySchema(currentDataSource);

        DSLContext dslContext = DSL.using(currentDataSource, SQLDialect.POSTGRES);
        OrderRepository repo = new OrderRepository(dslContext);
        log.info("OrderWorkerMain: Połączono z bazą danych i załadowano schemat: {}", dbUrl);

        String rmqHost = appProps.getProperty("rabbitmq.host");
        int rmqPort = Integer.parseInt(appProps.getProperty("rabbitmq.port"));
        String rmqUser = appProps.getProperty("rabbitmq.user");
        String rmqPass = appProps.getProperty("rabbitmq.pass");
        String queueName = appProps.getProperty("app.queue.name");

        RabbitMqClient mq = new RabbitMqClient(createRabbitMqConnectionFactory(rmqHost, rmqPort, rmqUser, rmqPass));
        mq.connectAndDeclareQueue(queueName);
        log.info("OrderWorkerMain: Połączono z RabbitMQ: {}:{} dla kolejki {}", rmqHost, rmqPort, queueName);

        OrderWorker worker = new OrderWorker(repo, mq, queueName);
        Thread t = new Thread(worker, "order-worker");
        t.start();
        log.info("OrderWorker running – CTRL-C aby zakończyć.");

        // --- Shutdown Hook ---
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("OrderWorkerMain: Zamykanie workera...");
            worker.stop();
            try { t.join(5000); } catch (InterruptedException ignored) {}
            mq.close();
            if (currentDataSource instanceof HikariDataSource hikari) {
                hikari.close();
            }
            log.info("OrderWorkerMain: Worker został zatrzymany i zasoby zwolnione.");
        }));

        t.join();
    }

    private static Properties loadApplicationProperties(String fileName) {
        Properties props = new Properties();
        try (InputStream input = OrderWorkerMain.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                log.error("Nie znaleziono pliku konfiguracyjnego: " + fileName);
                throw new IOException("Brak pliku konfiguracyjnego: " + fileName);
            }
            props.load(input);
        } catch (IOException ex) {
            log.error("Błąd podczas ładowania pliku konfiguracyjnego: {}", fileName, ex);
            throw new RuntimeException("Nie można załadować konfiguracji dla Workera.", ex);
        }
        return props;
    }

    private static ConnectionFactory createRabbitMqConnectionFactory(String host, int port, String user, String pass) {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setHost(host);
        cf.setPort(port);
        cf.setUsername(user);
        cf.setPassword(pass);
        return cf;
    }
}
