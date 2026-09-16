package org.maverick.classes.practice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pokemon {

    private static final HttpClient client = HttpClient.newHttpClient();

    // Паттерны для поиска числовых полей в JSON
    private static final Pattern ID_PATTERN     = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("\"weight\"\\s*:\\s*(\\d+)");

    private final String json;

    public Pokemon(String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://pokeapi.co/api/v2/pokemon/" + name))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            this.json = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при запросе к PokeAPI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстанавливаем флаг прерывания
            throw new RuntimeException("Запрос был прерван", e);
        }
    }

    public int getPokemonId() {
        return extractInt(ID_PATTERN, "id");
    }

    public int getPokemonWeight() {
        return extractInt(WEIGHT_PATTERN, "weight");
    }

    private int extractInt(Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new IllegalStateException("Поле '" + fieldName + "' не найдено в ответе JSON");
    }
}