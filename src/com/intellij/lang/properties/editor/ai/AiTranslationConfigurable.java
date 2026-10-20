// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.ai;

import com.intellij.lang.properties.editor.ResourceBundleEditorBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;

/** Settings page "Tools | Resource Bundle Editor AI Translation". */
public final class AiTranslationConfigurable implements Configurable {
  private static final String PRESET_LM_STUDIO = "LM Studio (local)";
  private static final String PRESET_NANO_GPT = "NanoGPT";
  private static final String PRESET_OPENAI = "OpenAI";
  private static final String PRESET_CUSTOM = "Custom";
  private static final String[] COMMON_LANGUAGES = {"en", "de", "fr", "es", "it", "pt", "nl", "pl", "cs", "ru", "uk", "tr", "ja", "zh", "ko"};

  private JPanel myPanel;
  private ComboBox<String> myPreset;
  private JBTextField myBaseUrl;
  private JBPasswordField myApiKey;
  private ComboBox<String> myModel;
  private ComboBox<String> myMainLanguage;
  private JSpinner myTimeout;
  private JSpinner myParallel;
  private JBTextArea myExtraInstructions;
  private JBLabel myStatus;
  private JButton myTestButton;
  private JButton myLoadModelsButton;

  private boolean myUpdatingPreset;
  /** Null until the key has been read from the password safe in the background. */
  private volatile String myLoadedApiKey;

  @Override
  public @Nls String getDisplayName() {
    return ResourceBundleEditorBundle.message("ai.settings.display.name");
  }

  @Override
  public @Nullable JComponent createComponent() {
    myPreset = new ComboBox<>(new String[]{PRESET_LM_STUDIO, PRESET_NANO_GPT, PRESET_OPENAI, PRESET_CUSTOM});
    myBaseUrl = new JBTextField();
    myApiKey = new JBPasswordField();
    myModel = new ComboBox<>(new DefaultComboBoxModel<>());
    myModel.setEditable(true);
    myMainLanguage = new ComboBox<>(COMMON_LANGUAGES);
    myMainLanguage.setEditable(true);
    myTimeout = new JSpinner(new SpinnerNumberModel(120, 5, 3600, 5));
    myParallel = new JSpinner(new SpinnerNumberModel(1, 1, AiTranslationSettings.MAX_PARALLEL_REQUESTS, 1));
    myExtraInstructions = new JBTextArea(4, 40);
    myExtraInstructions.setLineWrap(true);
    myExtraInstructions.setWrapStyleWord(true);
    myStatus = new JBLabel();
    myTestButton = new JButton(ResourceBundleEditorBundle.message("ai.settings.test.button"));
    myLoadModelsButton = new JButton(ResourceBundleEditorBundle.message("ai.settings.load.models.button"));

    myPreset.addActionListener(e -> {
      if (myUpdatingPreset) return;
      String url = urlForPreset((String)myPreset.getSelectedItem());
      if (url != null) myBaseUrl.setText(url);
    });
    myTestButton.addActionListener(e -> testConnection());
    myLoadModelsButton.addActionListener(e -> loadModels());

    JPanel modelRow = new JPanel(new BorderLayout(JBUI.scale(6), 0));
    modelRow.add(myModel, BorderLayout.CENTER);
    modelRow.add(myLoadModelsButton, BorderLayout.EAST);

    JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    testRow.add(myTestButton);

    myPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(ResourceBundleEditorBundle.message("ai.settings.preset"), myPreset)
      .addLabeledComponent(ResourceBundleEditorBundle.message("ai.settings.base.url"), myBaseUrl)
      .addLabeledComponent(ResourceBundleEditorBundle.message("ai.settings.api.key"), myApiKey)
      .addTooltip(ResourceBundleEditorBundle.message("ai.settings.api.key.hint"))
      .addLabeledComponent(ResourceBundleEditorBundle.message("ai.settings.model"), modelRow)
      .addLabeledComponent(ResourceBundleEditorBundle.message("ai.settings.main.language"), myMainLanguage)
      .addTooltip(ResourceBundleEditorBundle.message("ai.settings.main.language.hint"))
      .addLabeledComponent(ResourceBundleEditorBundle.message("ai.settings.timeout"), myTimeout)
      .addLabeledComponent(ResourceBundleEditorBundle.message("ai.settings.parallel"), myParallel)
      .addTooltip(ResourceBundleEditorBundle.message("ai.settings.parallel.hint"))
      .addLabeledComponentFillVertically(ResourceBundleEditorBundle.message("ai.settings.extra.instructions"),
                                         new JBScrollPane(myExtraInstructions))
      .addComponent(testRow)
      .addComponent(myStatus)
      .addComponentFillVertically(new JPanel(), 0)
      .getPanel();
    return myPanel;
  }

  @Override
  public boolean isModified() {
    AiTranslationSettings s = AiTranslationSettings.getInstance();
    return !myBaseUrl.getText().trim().equals(s.getBaseUrl())
           || !comboText(myModel).equals(s.getModel())
           || !comboText(myMainLanguage).equals(s.getMainLanguage())
           || ((Integer)myTimeout.getValue()) != s.getTimeoutSeconds()
           || ((Integer)myParallel.getValue()) != s.getParallelRequests()
           || !myExtraInstructions.getText().trim().equals(s.getExtraInstructions())
           || (myLoadedApiKey != null && !currentApiKey().equals(myLoadedApiKey));
  }

  @Override
  public void apply() throws ConfigurationException {
    String url = myBaseUrl.getText().trim();
    if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
      throw new ConfigurationException(ResourceBundleEditorBundle.message("ai.settings.invalid.url"));
    }
    AiTranslationSettings s = AiTranslationSettings.getInstance();
    s.setBaseUrl(url);
    s.setModel(comboText(myModel));
    s.setMainLanguage(comboText(myMainLanguage));
    s.setTimeoutSeconds((Integer)myTimeout.getValue());
    s.setParallelRequests((Integer)myParallel.getValue());
    s.setExtraInstructions(myExtraInstructions.getText());
    if (myLoadedApiKey != null) {
      String key = currentApiKey();
      s.setApiKey(key);
      myLoadedApiKey = key;
    }
  }

  @Override
  public void reset() {
    AiTranslationSettings s = AiTranslationSettings.getInstance();
    myBaseUrl.setText(s.getBaseUrl());
    selectPresetForUrl(s.getBaseUrl());
    myModel.setSelectedItem(s.getModel());
    myMainLanguage.setSelectedItem(s.getMainLanguage());
    myTimeout.setValue(s.getTimeoutSeconds());
    myParallel.setValue(s.getParallelRequests());
    myExtraInstructions.setText(s.getExtraInstructions());
    myStatus.setText("");
    myApiKey.setText("");
    myLoadedApiKey = null;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      String key = s.getApiKey();
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myPanel == null) return;
        // do not overwrite what the user typed in the meantime
        if (myApiKey.getPassword().length == 0) myApiKey.setText(key);
        myLoadedApiKey = key;
      }, ModalityState.any());
    });
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  private void testConnection() {
    AiTranslationClient.Config config = currentConfig();
    setStatus(ResourceBundleEditorBundle.message("ai.settings.testing"), null);
    myTestButton.setEnabled(false);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      String message;
      boolean ok = false;
      try {
        List<String> models = AiTranslationClient.listModels(config);
        StringBuilder sb = new StringBuilder(ResourceBundleEditorBundle.message("ai.settings.test.connected", models.size()));
        if (config.model().isEmpty()) {
          sb.append(' ').append(ResourceBundleEditorBundle.message("ai.settings.test.no.model"));
        }
        else {
          if (!models.isEmpty() && !models.contains(config.model())) {
            sb.append(' ').append(ResourceBundleEditorBundle.message("ai.settings.test.model.not.listed", config.model()));
          }
          String reply = AiTranslationClient.chat(config, null, "Reply with the single word: OK", 16);
          sb.append(' ').append(ResourceBundleEditorBundle.message("ai.settings.test.chat.ok", StringUtil.shortenTextWithEllipsis(reply, 40, 0)));
        }
        message = sb.toString();
        ok = true;
      }
      catch (AiTranslationClient.AiException e) {
        message = ResourceBundleEditorBundle.message("ai.settings.test.failed", e.getMessage());
      }
      String finalMessage = message;
      boolean finalOk = ok;
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myPanel == null) return;
        myTestButton.setEnabled(true);
        setStatus(finalMessage, finalOk);
      }, ModalityState.any());
    });
  }

  private void loadModels() {
    AiTranslationClient.Config config = currentConfig();
    setStatus(ResourceBundleEditorBundle.message("ai.settings.testing"), null);
    myLoadModelsButton.setEnabled(false);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<String> models = null;
      String error = null;
      try {
        models = AiTranslationClient.listModels(config);
      }
      catch (AiTranslationClient.AiException e) {
        error = e.getMessage();
      }
      List<String> finalModels = models;
      String finalError = error;
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myPanel == null) return;
        myLoadModelsButton.setEnabled(true);
        if (finalModels == null) {
          setStatus(ResourceBundleEditorBundle.message("ai.settings.test.failed", finalError), false);
          return;
        }
        String current = comboText(myModel);
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(finalModels.toArray(new String[0]));
        myModel.setModel(model);
        myModel.setSelectedItem(current);
        setStatus(ResourceBundleEditorBundle.message("ai.settings.models.loaded", finalModels.size()), true);
      }, ModalityState.any());
    });
  }

  private AiTranslationClient.Config currentConfig() {
    return new AiTranslationClient.Config(myBaseUrl.getText().trim(), currentApiKey(), comboText(myModel),
                                          (Integer)myTimeout.getValue());
  }

  private String currentApiKey() {
    return new String(myApiKey.getPassword()).trim();
  }

  private void setStatus(@NlsContexts.Label String text, @Nullable Boolean success) {
    myStatus.setForeground(success == null ? JBColor.foreground()
                           : success ? new JBColor(0x2E7D32, 0x62B543) : JBColor.RED);
    myStatus.setText("<html><body style='width:420px'>" + StringUtil.escapeXmlEntities(text) + "</body></html>");
  }

  private void selectPresetForUrl(String url) {
    myUpdatingPreset = true;
    try {
      String preset = PRESET_CUSTOM;
      for (String candidate : new String[]{PRESET_LM_STUDIO, PRESET_NANO_GPT, PRESET_OPENAI}) {
        if (Objects.equals(urlForPreset(candidate), url)) preset = candidate;
      }
      myPreset.setSelectedItem(preset);
    }
    finally {
      myUpdatingPreset = false;
    }
  }

  private static @Nullable String urlForPreset(@Nullable String preset) {
    if (PRESET_LM_STUDIO.equals(preset)) return AiTranslationSettings.LM_STUDIO_URL;
    if (PRESET_NANO_GPT.equals(preset)) return AiTranslationSettings.NANO_GPT_URL;
    if (PRESET_OPENAI.equals(preset)) return AiTranslationSettings.OPENAI_URL;
    return null;
  }

  private static @NotNull String comboText(ComboBox<String> combo) {
    Object item = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
    return item == null ? "" : item.toString().trim();
  }
}
