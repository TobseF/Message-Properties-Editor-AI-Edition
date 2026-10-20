// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.ai;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.editor.ResourceBundleEditor;
import com.intellij.lang.properties.editor.ResourceBundleEditorBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/** Generates all missing translations of the whole bundle with the configured AI. */
public final class TranslateAllMissingAction extends AnAction {
  private final ResourceBundleEditor myEditor;

  public TranslateAllMissingAction(@NotNull ResourceBundleEditor editor) {
    super(ResourceBundleEditorBundle.messagePointer("ai.action.translate.all.text"),
          ResourceBundleEditorBundle.messagePointer("ai.action.translate.all.description"),
          AllIcons.Actions.Execute);
    myEditor = editor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myEditor.translateAllMissingTranslations();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myEditor.isValid());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
