/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class OrderEnumerationHandler {
  public static final ExtensionPointName<Factory> EP_NAME =
    ExtensionPointName.create("com.intellij.orderEnumerationHandlerFactory");

  public static abstract class Factory {

    public abstract boolean isApplicable(@NotNull Project project);

    public abstract boolean isApplicable(@NotNull Module module);

    public abstract OrderEnumerationHandler createHandler(@Nullable Module module);
  }


  public static enum AddDependencyType {ADD, DO_NOT_ADD, DEFAULT}

  @NotNull
  public AddDependencyType shouldAddDependency(@NotNull OrderEntry orderEntry,
                                               @NotNull OrderEnumeratorSettings settings) {
    return AddDependencyType.DEFAULT;
  }

  public boolean shouldProcessRecursively(@NotNull ModuleOrderEntry dependency) {
    return true;
  }

  public boolean addCustomOutput(@NotNull Module forModule,
                                 @NotNull ModuleRootModel orderEntryRootModel,
                                 @NotNull OrderRootType type,
                                 @NotNull OrderEnumeratorSettings settings,
                                 @NotNull Collection<String> urls) {
    return false;
  }

  public boolean addCustomRootsForLibrary(@NotNull OrderEntry forOrderEntry,
                                          @NotNull OrderRootType type,
                                          @NotNull Collection<String> urls) {
    return false;
  }

  public void addAdditionalRoots(@NotNull Module forModule,
                                 @NotNull OrderEnumeratorSettings settings,
                                 @NotNull Collection<String> urls) {
  }
}
