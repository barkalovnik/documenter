package org.maverick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Pokemon {

    private static final HttpClient client = HttpClient.newHttpClient();

    private static final ObjectMapper mapper = new ObjectMapper();

    private final JsonNode root;

    public Pokemon(String _name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://pokeapi.co/api/v2/pokemon/" + _name))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            String json = String.valueOf(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
            root = mapper.readTree(json);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public int getPokemonId() {

        return root.get("id").asInt();

    }

    public int getPokemonWeight() {

        return root.get("weight").asInt();

    }

}
