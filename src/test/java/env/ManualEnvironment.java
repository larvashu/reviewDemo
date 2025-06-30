package env;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import common.AbstractTestEnvironment;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class ManualEnvironment extends AbstractTestEnvironment {

    private static final String CONFIG_FILE_NAME = "test.properties";

    public ManualEnvironment() {
        this.testProperties = loadProperties(CONFIG_FILE_NAME);
    }

    @Override
    protected void doInit() throws Exception {
        log.info("Uruchamiam środowisko testowe (tryb MANUALNY)...");

        String dbHost = testProperties.getProperty("db.host");
        String dbPort = testProperties.getProperty("db.port");
        String dbName = testProperties.getProperty("db.name");
        String dbUser = testProperties.getProperty("db.user");
        String dbPass = testProperties.getProperty("db.pass");

        String dbUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(dbUrl);
        hc.setUsername(dbUser);
        hc.setPassword(dbPass);
        this.dataSourceInstance = new HikariDataSource(hc);
        this.dslContextInstance = DSL.using(this.dataSourceInstance, SQLDialect.POSTGRES);
        applySchema(this.dataSourceInstance);

        log.info("✅  Połączono z bazą danych (ManualEnv): {}", dbUrl);

        String rmqHost = testProperties.getProperty("rabbitmq.host");
        int rmqPort = Integer.parseInt(testProperties.getProperty("rabbitmq.port"));
        String rmqUser = testProperties.getProperty("rabbitmq.user");
        String rmqPass = testProperties.getProperty("rabbitmq.pass");

        this.rabbitMqCFInstance = createRabbitMqConnectionFactory(
                rmqHost, rmqPort, rmqUser, rmqPass);

        log.info("✅  Połączono z RabbitMQ (ManualEnv): {}:{}", rmqHost, rmqPort);
    }

    @Override
    protected void doShutdown() {
        //wylaczanie manualne tak cyz siak
    }
}