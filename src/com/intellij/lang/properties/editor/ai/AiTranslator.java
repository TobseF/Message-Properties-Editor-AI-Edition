// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.ai;

import org.jetbrains.annotations.NotNull;

/** Builds the prompts for translating one property value into one target language. */
public final class AiTranslator {
  private static final String SYSTEM_PROMPT = """
    You are a professional software localization translator. You translate UI texts of a Java .properties resource bundle.
    Rules:
    - Reply with the translated text only: no explanations, no quotes around it, no code fences.
    - Keep placeholders unchanged, e.g. {0}, {1,number}, %s, %d, ${name}, and keep MessageFormat quoting (doubled apostrophes) as in the source.
    - Keep HTML/XML tags, line breaks, leading and trailing whitespace and the punctuation style of the source.
    - Do not translate technical identifiers, file names, code or product names.
    - Use the terminology and tone that is common for user interfaces in the target language.""";

  private AiTranslator() {}

  public static @NotNull String translate(@NotNull AiTranslationClient.Config config,
                                          @NotNull AiTranslationPlan plan,
                                          @NotNull AiTranslationPlan.Target target,
                                          @NotNull String extraInstructions) throws AiTranslationClient.AiException {
    String system = extraInstructions.isBlank() ? SYSTEM_PROMPT : SYSTEM_PROMPT + "\nAdditional instructions from the user:\n" + extraInstructions.trim();
    String source = plan.getSourceLanguage() == null ? "unknown (detect it)" : plan.getSourceLanguage();
    String user = "Property key: " + plan.getKey() + "\n" +
                  "Source language: " + source + "\n" +
                  "Target language: " + target.languageName() + "\n\n" +
                  "Text to translate:\n" + plan.getSourceText();
    String result = AiTranslationClient.chat(config, system, user, null);
    if (result.isBlank()) {
      throw new AiTranslationClient.AiException("The model returned an empty translation for " + target.languageName());
    }
    return result;
  }
}
