package com.aidebugger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Universal client for local LLM (Ollama) interaction.
 * Zero external mod dependencies: uses standard java.net.http.HttpClient and Gson.
 * Seamlessly handles Windows IPv6/IPv4 fallback between 'localhost' and '127.0.0.1'.
 */
public class OllamaClient {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public static class OllamaStatus {
        private final boolean isRunning;
        private final List<String> availableModels;
        private final String errorMessage;
        private final String workingUrl;

        public OllamaStatus(boolean isRunning, List<String> availableModels, String errorMessage, String workingUrl) {
            this.isRunning = isRunning;
            this.availableModels = availableModels != null ? availableModels : List.of();
            this.errorMessage = errorMessage;
            this.workingUrl = workingUrl;
        }

        public boolean isRunning() {
            return isRunning;
        }

        public List<String> getAvailableModels() {
            return availableModels;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getWorkingUrl() {
            return workingUrl;
        }
    }

    /**
     * Checks if Ollama is running and lists installed models.
     * Automatically attempts 127.0.0.1 fallback if localhost IPv6 is refused on Windows.
     */
    public static CompletableFuture<OllamaStatus> checkStatus(String baseUrl) {
        return checkStatusSingle(baseUrl).thenCompose(status -> {
            if (status.isRunning()) {
                return CompletableFuture.completedFuture(status);
            }
            // Auto fallback localhost <-> 127.0.0.1 (Windows IPv4/IPv6 compatibility)
            if (baseUrl != null && baseUrl.contains("localhost")) {
                String fallbackUrl = baseUrl.replace("localhost", "127.0.0.1");
                return checkStatusSingle(fallbackUrl);
            }
            if (baseUrl != null && baseUrl.contains("127.0.0.1")) {
                String fallbackUrl = baseUrl.replace("127.0.0.1", "localhost");
                return checkStatusSingle(fallbackUrl);
            }
            return CompletableFuture.completedFuture(status);
        });
    }

    private static CompletableFuture<OllamaStatus> checkStatusSingle(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return CompletableFuture.completedFuture(new OllamaStatus(false, List.of(), "Empty URL", null));
        }
        String cleanUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI tagsUri;
        try {
            tagsUri = URI.create(cleanUrl + "/api/tags");
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new OllamaStatus(false, List.of(), "Invalid URL: " + baseUrl, null));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(tagsUri)
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        List<String> models = new ArrayList<>();
                        try {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (root.has("models")) {
                                JsonArray modelArray = root.getAsJsonArray("models");
                                for (JsonElement elem : modelArray) {
                                    if (elem.isJsonObject()) {
                                        JsonObject m = elem.getAsJsonObject();
                                        if (m.has("name")) {
                                            models.add(m.get("name").getAsString());
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                        return new OllamaStatus(true, models, null, cleanUrl);
                    } else {
                        return new OllamaStatus(false, List.of(), "HTTP " + response.statusCode(), null);
                    }
                })
                .exceptionally(throwable -> new OllamaStatus(false, List.of(), throwable.getMessage(), null));
    }

    /**
     * Sends prompt generation request asynchronously.
     * Automatically attempts 127.0.0.1 fallback if localhost fails.
     */
    public static CompletableFuture<String> generate(String baseUrl, String model, String systemPrompt, String userPrompt) {
        return generateSingle(baseUrl, model, systemPrompt, userPrompt)
                .thenCompose(result -> {
                    if (result != null && !result.startsWith("Failed to communicate with Ollama") && !result.startsWith("Error communicating")) {
                        return CompletableFuture.completedFuture(result);
                    }
                    if (baseUrl != null && baseUrl.contains("localhost")) {
                        String fallback = baseUrl.replace("localhost", "127.0.0.1");
                        return generateSingle(fallback, model, systemPrompt, userPrompt);
                    }
                    if (baseUrl != null && baseUrl.contains("127.0.0.1")) {
                        String fallback = baseUrl.replace("127.0.0.1", "localhost");
                        return generateSingle(fallback, model, systemPrompt, userPrompt);
                    }
                    return CompletableFuture.completedFuture(result);
                });
    }

    private static CompletableFuture<String> generateSingle(String baseUrl, String model, String systemPrompt, String userPrompt) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return CompletableFuture.completedFuture("Ollama URL is empty.");
        }
        String cleanUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI generateUri = URI.create(cleanUrl + "/api/generate");

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("system", systemPrompt);
        requestBody.addProperty("prompt", userPrompt);
        requestBody.addProperty("stream", false);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.2);
        options.addProperty("num_predict", 512);
        requestBody.add("options", options);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(generateUri)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (root.has("response")) {
                                return root.get("response").getAsString().trim();
                            }
                        } catch (Exception e) {
                            return "Error parsing LLM response: " + e.getMessage();
                        }
                        return "Received empty response from Ollama.";
                    } else {
                        return "Ollama returned error status: " + response.statusCode() + " (" + response.body() + ")";
                    }
                })
                .exceptionally(throwable -> "Failed to communicate with Ollama: " + throwable.getMessage());
    }
}
