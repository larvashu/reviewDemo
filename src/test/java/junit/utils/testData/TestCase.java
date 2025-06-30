package junit.utils;

import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap; // Aby zachować kolejność kluczy

public class TestCase {
    private String description;
    private Map<String, Object> data; // Teraz to jest mapa!
    private Map<String, Object> expected; // I to też jest mapa!

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Map<String, Object> getExpected() {
        return expected;
    }

    public void setExpected(Map<String, Object> expected) {
        this.expected = expected;
    }

    // Metoda do głębokiego mergowania map
    // Obsługuje zagnieżdżone mapy, nadpisując wartości.
    private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }

        Map<String, Object> result = new LinkedHashMap<>(base); // Kopiuj bazę

        overlay.forEach((key, value) -> {
            if (result.containsKey(key) && result.get(key) instanceof Map && value instanceof Map) {
                // Jeśli oba są mapami, zmerguj je rekurencyjnie
                result.put(key, deepMerge((Map<String, Object>) result.get(key), (Map<String, Object>) value));
            } else {
                // W przeciwnym razie, po prostu nadpisz
                result.put(key, value);
            }
        });
        return result;
    }

    // Metoda do łączenia (mergowania) przypadków testowych z domyślnymi
    public TestCase merge(TestCase defaults) {
        TestCase merged = new TestCase();
        merged.setDescription(Optional.ofNullable(this.description).orElse(defaults.getDescription()));

        // Głębokie mergowanie dla 'data'
        merged.setData(deepMerge(defaults.getData(), this.getData()));

        // Głębokie mergowanie dla 'expected'
        merged.setExpected(deepMerge(defaults.getExpected(), this.getExpected()));

        return merged;
    }
}