package junit.utils.testData.adnotations;

import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ArgumentsSource(YamlFileArgumentsProvider.class)
public @interface TestData {
    String value();
}