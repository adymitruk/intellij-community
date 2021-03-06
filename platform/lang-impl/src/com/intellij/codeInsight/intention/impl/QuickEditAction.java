/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageFacadeImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * "Quick Edit Language" intention action that provides an editor which shows an injected language
 * fragment's complete prefix and suffix in non-editable areas and allows to edit the fragment
 * without having to consider any additional escaping rules (e.g. when editing regexes in String
 * literals).
 * 
 * @author Gregory Shrago
 * @author Konstantin Bulenkov
 */
public class QuickEditAction implements IntentionAction, LowPriorityAction {
  public static final Key<QuickEditHandler> QUICK_EDIT_HANDLER = Key.create("QUICK_EDIT_HANDLER");
  private String myLastLanguageName;

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return getRangePair(file, editor) != null;
  }

  @Nullable
  protected Pair<PsiElement, TextRange> getRangePair(final PsiFile file, final Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiLanguageInjectionHost host =
      PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false);
    if (host == null) return null;
    final List<Pair<PsiElement, TextRange>> injections = InjectedLanguageFacadeImpl.getInstance().getInjectedPsiFiles(host);
    if (injections == null || injections.isEmpty()) return null;
    final int offsetInElement = offset - host.getTextRange().getStartOffset();
    final Pair<PsiElement, TextRange> rangePair = ContainerUtil.find(injections, new Condition<Pair<PsiElement, TextRange>>() {
      public boolean value(final Pair<PsiElement, TextRange> pair) {
        return pair.second.containsRange(offsetInElement, offsetInElement);
      }
    });
    if (rangePair != null) {
      myLastLanguageName = rangePair.first.getContainingFile().getLanguage().getDisplayName();
    }
    return rangePair;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    final Pair<PsiElement, TextRange> pair = getRangePair(file, editor);
    assert pair != null;
    final PsiFile injectedFile = (PsiFile)pair.first;
    final int injectedOffset = ((DocumentWindow)PsiDocumentManager.getInstance(project).getDocument(injectedFile)).hostToInjected(offset);
    getHandler(project, injectedFile, editor, file).navigate(injectedOffset);
  }

  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  private QuickEditHandler getHandler(Project project, PsiFile injectedFile, Editor editor, PsiFile origFile) {
    QuickEditHandler handler = injectedFile.getUserData(QUICK_EDIT_HANDLER);
    if (handler != null && handler.isValid()) {
      return handler;
    }
    handler = new QuickEditHandler(project, injectedFile, origFile, editor, this);
    injectedFile.putUserData(QUICK_EDIT_HANDLER, handler);
    return handler;
  }
  
  protected boolean isShowInBalloon() {
    return false;
  }
  
  @Nullable
  protected JComponent createBalloonComponent(PsiFile file, Ref<Balloon> ref) {
    return null;
  }

  @NotNull
  public String getText() {
    return "Edit "+ StringUtil.notNullize(myLastLanguageName, "Injected")+" Fragment";
  }

  @NotNull
  public String getFamilyName() {
    return "Edit Injected Fragment";
  }

  public static Balloon.Position getBalloonPosition(Editor editor) {
    final int line = editor.getCaretModel().getVisualPosition().line;
    final Rectangle area = editor.getScrollingModel().getVisibleArea();
    int startLine  = area.y / editor.getLineHeight() + 1;
    return (line - startLine) * editor.getLineHeight() < 200 ? Balloon.Position.below : Balloon.Position.above;
  }
}
