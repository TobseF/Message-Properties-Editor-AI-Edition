// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Minimal client for OpenAI compatible endpoints ({@code /models} and {@code /chat/completions}), e.g. NanoGPT or LM Studio. */
public final class AiTranslationClient {
  public record Config(@NotNull String baseUrl, @NotNull String apiKey, @NotNull String model, int timeoutSeconds) {}

  public static final class AiException extends Exception {
    public AiException(String message) { super(message); }
    public AiException(String message, Throwable cause) { super(message, cause); }
  }

  private static final Gson GSON = new Gson();
  private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");
  private static final Pattern CODE_FENCE = Pattern.compile("(?s)^```[a-zA-Z0-9_-]*\\R(.*?)\\R?```$");
  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private AiTranslationClient() {}

  public static @NotNull List<String> listModels(@NotNull Config config) throws AiException {
    String body = send(config, HttpRequest.newBuilder(uri(config, "/models")).GET());
    List<String> result = new ArrayList<>();
    try {
      JsonElement data = JsonParser.parseString(body).getAsJsonObject().get("data");
      if (data != null && data.isJsonArray()) {
        for (JsonElement e : data.getAsJsonArray()) {
          JsonElement id = e.isJsonObject() ? e.getAsJsonObject().get("id") : null;
          if (id != null && id.isJsonPrimitive()) result.add(id.getAsString());
        }
      }
    }
    catch (RuntimeException e) {
      throw new AiException("Unexpected response from /models: " + abbreviate(body), e);
    }
    return result;
  }

  public static @NotNull String chat(@NotNull Config config,
                                     @Nullable String systemPrompt,
                                     @NotNull String userPrompt,
                                     @Nullable Integer maxTokens) throws AiException {
    JsonObject request = new JsonObject();
    request.addProperty("model", config.model());
    request.addProperty("stream", false);
    if (maxTokens != null) request.addProperty("max_tokens", maxTokens);
    JsonArray messages = new JsonArray();
    if (systemPrompt != null) messages.add(message("system", systemPrompt));
    messages.add(message("user", userPrompt));
    request.add("messages", messages);

    HttpRequest.Builder builder = HttpRequest.newBuilder(uri(config, "/chat/completions"))
      .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request), StandardCharsets.UTF_8));
    String body = send(config, builder);
    try {
      JsonObject choice = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
      JsonElement content = choice.getAsJsonObject("message").get("content");
      return content == null || content.isJsonNull() ? "" : cleanup(content.getAsString());
    }
    catch (RuntimeException e) {
      throw new AiException("Unexpected response from /chat/completions: " + abbreviate(body), e);
    }
  }

  /** Strips reasoning blocks and surrounding code fences that some models add around the translation. */
  static @NotNull String cleanup(@NotNull String text) {
    String result = THINK_BLOCK.matcher(text).replaceAll("").trim();
    var fence = CODE_FENCE.matcher(result);
    if (fence.matches()) result = fence.group(1).trim();
    return result;
  }

  private static JsonObject message(String role, String content) {
    JsonObject o = new JsonObject();
    o.addProperty("role", role);
    o.addProperty("content", content);
    return o;
  }

  private static URI uri(Config config, String path) throws AiException {
    String base = config.baseUrl().trim();
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    if (base.isEmpty()) throw new AiException("No base URL configured");
    try {
      return URI.create(base + path);
    }
    catch (IllegalArgumentException e) {
      throw new AiException("Invalid base URL: " + base, e);
    }
  }

  private static String send(Config config, HttpRequest.Builder builder) throws AiException {
    builder.timeout(Duration.ofSeconds(Math.max(5, config.timeoutSeconds())))
      .header("Accept", "application/json")
      .header("Content-Type", "application/json");
    if (!config.apiKey().isBlank()) builder.header("Authorization", "Bearer " + config.apiKey().trim());

    CompletableFuture<HttpResponse<String>> future =
      HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    try {
      while (true) {
        try {
          HttpResponse<String> response = future.get(200, TimeUnit.MILLISECONDS);
          if (response.statusCode() / 100 != 2) {
            throw new AiException("HTTP " + response.statusCode() + ": " + errorMessage(response.body()));
          }
          return response.body();
        }
        catch (TimeoutException ignored) {
          ProgressManager.checkCanceled();
        }
      }
    }
    catch (ProcessCanceledException e) {
      future.cancel(true);
      throw e;
    }
    catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new AiException("Interrupted", e);
    }
    catch (ExecutionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      String msg = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
      throw new AiException("Request failed: " + msg, cause);
    }
  }

  private static String errorMessage(String body) {
    try {
      JsonElement error = JsonParser.parseString(body).getAsJsonObject().get("error");
      if (error != null && error.isJsonObject() && error.getAsJsonObject().has("message")) {
        return error.getAsJsonObject().get("message").getAsString();
      }
      if (error != null && error.isJsonPrimitive()) return error.getAsString();
    }
    catch (RuntimeException ignored) {
    }
    return abbreviate(body);
  }

  private static String abbreviate(String s) {
    String t = s == null ? "" : s.trim();
    return t.length() > 300 ? t.substring(0, 300) + "…" : t;
  }
}
