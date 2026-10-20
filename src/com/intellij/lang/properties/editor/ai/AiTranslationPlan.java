// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.ai;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleEditor;
import com.intellij.lang.properties.psi.PropertiesFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides which text is used as translation source and which locales still lack a value for one property key.
 * Source preference: file of the configured main language, then the default file, then any other file with a value.
 */
public final class AiTranslationPlan {
  public record Target(@NotNull PropertiesFile file, @NotNull String languageName) {}

  private final String myKey;
  private final String mySourceText;
  private final String mySourceLanguage;
  private final List<Target> myTargets;

  private AiTranslationPlan(String key, String sourceText, @Nullable String sourceLanguage, List<Target> targets) {
    myKey = key;
    mySourceText = sourceText;
    mySourceLanguage = sourceLanguage;
    myTargets = targets;
  }

  public @NotNull String getKey() { return myKey; }
  public @NotNull String getSourceText() { return mySourceText; }
  /** Null when the source is the default file, whose language is unknown. */
  public @Nullable String getSourceLanguage() { return mySourceLanguage; }
  public @NotNull List<Target> getTargets() { return myTargets; }

  /** Returns null if no file has a non-blank value for the key. Must be called under a read action. */
  public static @Nullable AiTranslationPlan build(@NotNull ResourceBundle bundle, @NotNull String key, @NotNull String mainLanguage) {
    List<PropertiesFile> files = bundle.getPropertiesFiles();
    PropertiesFile source = null;
    String sourceText = null;

    // 1) main language, 2) default file, 3) first file that has a value
    for (int pass = 0; pass < 3 && source == null; pass++) {
      for (PropertiesFile file : files) {
        String lang = file.getLocale().getLanguage();
        boolean matches = switch (pass) {
          case 0 -> !mainLanguage.isBlank() && !lang.isEmpty() && lang.equalsIgnoreCase(mainLanguage.trim());
          case 1 -> lang.isEmpty();
          default -> true;
        };
        if (!matches) continue;
        String value = valueOf(file, key);
        if (!value.isBlank()) {
          source = file;
          sourceText = value;
          break;
        }
      }
    }
    if (source == null) return null;

    List<Target> targets = new ArrayList<>();
    for (PropertiesFile file : files) {
      Locale locale = file.getLocale();
      // The default file has no locale, its language is unknown, so it cannot be a translation target.
      if (file == source || locale.getLanguage().isEmpty()) continue;
      if (valueOf(file, key).isBlank()) targets.add(new Target(file, languageName(locale)));
    }
    String sourceLanguage = source.getLocale().getLanguage().isEmpty() ? null : languageName(source.getLocale());
    return new AiTranslationPlan(key, sourceText, sourceLanguage, targets);
  }

  private static String valueOf(PropertiesFile file, String key) {
    IProperty property = file.findPropertyByKey(key);
    return property == null ? "" : ResourceBundleEditor.getPropertyEditorValue(property);
  }

  private static String languageName(Locale locale) {
    String name = locale.getDisplayName(Locale.ENGLISH);
    return name.isBlank() ? locale.toString() : name;
  }
}
