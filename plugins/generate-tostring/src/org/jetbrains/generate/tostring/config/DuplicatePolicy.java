/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring.config;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * This policy is to create a duplicate <code>toString</code> method.
 */
public class DuplicatePolicy implements ConflictResolutionPolicy {

  private static final DuplicatePolicy instance = new DuplicatePolicy();
  private static InsertNewMethodStrategy newMethodStrategy = InsertAtCaretStrategy.getInstance();

  private DuplicatePolicy() {
  }

  public static DuplicatePolicy getInstance() {
    return instance;
  }

  public void setNewMethodStrategy(InsertNewMethodStrategy strategy) {
    newMethodStrategy = strategy;
  }

  public PsiMethod applyMethod(PsiClass clazz, PsiMethod existingMethod, @NotNull PsiMethod newMethod, Editor editor)
    throws IncorrectOperationException {
    return newMethodStrategy.insertNewMethod(clazz, newMethod, editor);
  }

  public String toString() {
    return "Duplicate";
  }

}
