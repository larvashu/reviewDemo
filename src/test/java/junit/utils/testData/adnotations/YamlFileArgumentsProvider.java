package junit.utils.testData.adnotations;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.platform.commons.util.AnnotationUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap; // Używamy LinkedHashMap, aby zachować kolejność w mapach
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class YamlFileArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        Method method = context.getRequiredTestMethod();
        TestData testDataAnnotation = AnnotationUtils.findAnnotation(method, TestData.class)
                .orElseThrow(() -> new IllegalArgumentException("Missing @TestData annotation"));

        String fileName = testDataAnnotation.value();
        // Plik powinien być podany w adnotacji @TestData z poprawnymi ukośnikami i rozszerzeniem.
        // Nie wykonujemy już żadnej dodatkowej konwersji (np. replace('.', '/') czy dodawanie .yaml).
        // Przykład: @TestData("junit_testCases/orders.yaml")
        String resourcePath = fileName;

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                // Ta linia zostanie wywołana, jeśli plik nie zostanie znaleziony.
                // Upewnij się, że plik orders.yaml znajduje się w src/test/resources/junit_testCases/
                throw new IllegalArgumentException("Test data file not found: " + resourcePath + ". Make sure it's in your resources folder and the path is correct.");
            }

            Yaml yaml = new Yaml();
            // Odczytaj cały plik YAML jako Mapę, ponieważ zaczyna się od głównego klucza "testCases"
            Map<String, Object> fullYamlContent = yaml.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            // Pobierz listę przypadków testowych spod klucza "testCases"
            List<Map<String, Object>> testCases = (List<Map<String, Object>>) fullYamlContent.get("testCases");

            if (testCases == null || testCases.isEmpty()) {
                throw new IllegalArgumentException("No 'testCases' found or 'testCases' is empty in YAML file: " + resourcePath);
            }

            // Obsługa defaultValues
            Map<String, Object> defaultData;
            Map<String, Object> defaultExpected;

            // Sprawdź, czy pierwszy przypadek testowy to "defaultValues"
            if (!testCases.isEmpty() && "defaultValues".equals(testCases.get(0).get("Description"))) {
                Map<String, Object> defaultCase = testCases.remove(0); // Usuń defaultValues z listy do przetwarzania
                defaultData = (Map<String, Object>) defaultCase.get("Data");
                defaultExpected = (Map<String, Object>) defaultCase.get("Expected");

                System.out.println("--- Loaded defaultValues ---");
                System.out.println("Default Data: " + defaultData);
                System.out.println("Default Expected: " + defaultExpected);
            } else {
                defaultExpected = null;
                defaultData = null;
            }


            return testCases.stream()
                    .map(testCase -> {
                        Map<String, Object> currentData = (Map<String, Object>) testCase.get("Data");
                        Map<String, Object> currentExpected = (Map<String, Object>) testCase.get("Expected");

                        Map<String, Object> finalData = mergeMaps(defaultData, currentData);
                        Map<String, Object> finalExpected = mergeMaps(defaultExpected, currentExpected);

                        String description = (String) testCase.get("Description");

                        System.out.println("--- Processing Test Case: " + description + " ---");
                        System.out.println("Current Data (before merge): " + currentData);
                        System.out.println("Current Expected (before merge): " + currentExpected);
                        System.out.println("Final Data (merged): " + finalData);
                        System.out.println("Final Expected (merged): " + finalExpected);

                        if (finalData == null) {
                            System.err.println("ERROR: Final Data for '" + description + "' is NULL!");
                        } else if (!finalData.containsKey("order")) {
                            System.err.println("WARNING: Final Data for '" + description + "' does NOT contain 'order' key!");
                        }
                        if (finalExpected == null) {
                            System.err.println("ERROR: Final Expected for '" + description + "' is NULL!");
                        }
                        return Arguments.of(finalData, finalExpected);
                    });

        } catch (Exception e) {
            System.err.println("Error loading test data from " + resourcePath + ": " + e.getMessage());
            throw new RuntimeException("Failed to provide arguments from YAML file: " + resourcePath, e);
        }
    }

    /**
     * Pomocnicza metoda do łączenia map.
     * Łączy wartości z defaultMap z overrideMap, preferując overrideMap.
     * Obsługuje zagnieżdżone mapy, rekurencyjnie je łącząc.
     *
     * @param defaultMap Mapa domyślnych wartości. Może być null.
     * @param overrideMap Mapa nadpisujących wartości. Może być null.
     * @return Połączona mapa. Jeśli obie mapy są null, zwraca null. Jeśli tylko defaultMap jest null, zwraca overrideMap.
     * Jeśli tylko overrideMap jest null, zwraca defaultMap.
     */
    private Map<String, Object> mergeMaps(Map<String, Object> defaultMap, Map<String, Object> overrideMap) {
        if (defaultMap == null && overrideMap == null) {
            return null; // Zwróć null, jeśli nic nie ma do połączenia
        }
        if (overrideMap == null) {
            return defaultMap != null ? new LinkedHashMap<>(defaultMap) : null;
        }
        if (defaultMap == null) {
            return overrideMap != null ? new LinkedHashMap<>(overrideMap) : null;
        }

        Map<String, Object> mergedMap = new LinkedHashMap<>(defaultMap); // Rozpocznij od domyślnych wartości

        for (Map.Entry<String, Object> entry : overrideMap.entrySet()) {
            String key = entry.getKey();
            Object overrideValue = entry.getValue();
            Object defaultValue = mergedMap.get(key);

            if (defaultValue instanceof Map && overrideValue instanceof Map) {
                mergedMap.put(key, mergeMaps((Map<String, Object>) defaultValue, (Map<String, Object>) overrideValue));
            } else {
                mergedMap.put(key, overrideValue);
            }
        }
        return mergedMap;
    }
}