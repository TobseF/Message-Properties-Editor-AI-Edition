// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.ai;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.editor.ResourceBundleEditorBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Opens the AI translation settings page. */
public final class OpenAiSettingsAction extends AnAction {
  private final Project myProject;

  public OpenAiSettingsAction(@NotNull Project project) {
    super(ResourceBundleEditorBundle.messagePointer("ai.action.open.settings.text"),
          ResourceBundleEditorBundle.messagePointer("ai.action.open.settings.description"),
          AllIcons.General.Settings);
    myProject = project;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ShowSettingsUtil.getInstance().showSettingsDialog(myProject, AiTranslationConfigurable.class);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
