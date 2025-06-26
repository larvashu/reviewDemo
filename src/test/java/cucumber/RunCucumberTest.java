package cucumber;

import io.cucumber.junit.platform.engine.Cucumber;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * JUnit Platform Test Suite to run Cucumber BDD features.
 * Configures the feature file location, step definitions package,
 * and reporting plugins (including Allure).
 */
@Suite
@IncludeEngines("cucumber") // Indicate that this suite runs Cucumber tests
@SelectClasspathResource("features") // Specify the directory where .feature files are located
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "cucumber.step")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty," +                                      // Console output
                "html:target/cucumber-reports/cucumber.html," +  // HTML report
                "json:target/cucumber-reports/cucumber.json," +  // JSON report (for external tools)
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm" // Allure integration for Cucumber
)
public class RunCucumberTest {
    // This class serves as the entry point for JUnit to discover and run Cucumber features.
    // No code is needed inside the class itself.
}