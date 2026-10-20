// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.ai;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application-wide settings of the OpenAI compatible endpoint used to translate missing properties.
 * The API key is kept in the IDE password safe, everything else in {@code resourceBundleEditorAi.xml}.
 */
@Service(Service.Level.APP)
@State(name = "ResourceBundleEditorAiSettings", storages = @Storage("resourceBundleEditorAi.xml"))
public final class AiTranslationSettings implements PersistentStateComponent<AiTranslationSettings.State> {
  public static final int MAX_PARALLEL_REQUESTS = 16;
  public static final String LM_STUDIO_URL = "http://localhost:1234/v1";
  public static final String NANO_GPT_URL = "https://nano-gpt.com/api/v1";
  public static final String OPENAI_URL = "https://api.openai.com/v1";

  public static final class State {
    public String baseUrl = LM_STUDIO_URL;
    public String model = "";
    public String mainLanguage = "en";
    public String extraInstructions = "";
    public int timeoutSeconds = 120;
    public int parallelRequests = 1;
  }

  private State myState = new State();
  private volatile String myApiKey;

  public static @NotNull AiTranslationSettings getInstance() {
    return ApplicationManager.getApplication().getService(AiTranslationSettings.class);
  }

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public @NotNull String getBaseUrl() { return nonNull(myState.baseUrl); }
  public @NotNull String getModel() { return nonNull(myState.model); }
  public @NotNull String getMainLanguage() { return nonNull(myState.mainLanguage); }
  public @NotNull String getExtraInstructions() { return nonNull(myState.extraInstructions); }
  public int getTimeoutSeconds() { return Math.max(5, myState.timeoutSeconds); }
  public int getParallelRequests() { return Math.max(1, Math.min(MAX_PARALLEL_REQUESTS, myState.parallelRequests)); }

  public void setBaseUrl(@NotNull String value) { myState.baseUrl = value.trim(); }
  public void setModel(@NotNull String value) { myState.model = value.trim(); }
  public void setMainLanguage(@NotNull String value) { myState.mainLanguage = value.trim(); }
  public void setExtraInstructions(@NotNull String value) { myState.extraInstructions = value.trim(); }
  public void setTimeoutSeconds(int value) { myState.timeoutSeconds = value; }
  public void setParallelRequests(int value) { myState.parallelRequests = value; }

  public boolean isConfigured() {
    return !getBaseUrl().isEmpty() && !getModel().isEmpty();
  }

  /** May block (password safe access), do not call on the EDT. */
  public @NotNull String getApiKey() {
    String key = myApiKey;
    if (key == null) {
      String stored = PasswordSafe.getInstance().getPassword(credentialAttributes());
      key = stored == null ? "" : stored;
      myApiKey = key;
    }
    return key;
  }

  /** Updates the in-memory value immediately and persists asynchronously. */
  public void setApiKey(@Nullable String apiKey) {
    String value = apiKey == null ? "" : apiKey.trim();
    myApiKey = value;
    ApplicationManager.getApplication().executeOnPooledThread(
      () -> PasswordSafe.getInstance().setPassword(credentialAttributes(), value.isEmpty() ? null : value));
  }

  /** Reads the API key, so do not call on the EDT. */
  public @NotNull AiTranslationClient.Config toClientConfig() {
    return new AiTranslationClient.Config(getBaseUrl(), getApiKey(), getModel(), getTimeoutSeconds());
  }

  private static CredentialAttributes credentialAttributes() {
    return new CredentialAttributes(CredentialAttributesKt.generateServiceName("ResourceBundleEditorAI", "apiKey"));
  }

  private static @NotNull String nonNull(@Nullable String s) {
    return s == null ? "" : s;
  }
}
