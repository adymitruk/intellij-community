/*
 * Copyright 2000-2009 JetBrains s.r.o.
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


package org.jetbrains.idea.svn17.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn17.SvnBundle;
import org.jetbrains.idea.svn17.SvnVcs17;
import org.jetbrains.idea.svn17.dialogs.SetPropertyDialog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

public class SetPropertyAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("action.name.set.property");
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs17 vcs, VirtualFile file) {
    if (file == null || project == null || vcs == null) return false;
    final FileStatus status = FileStatusManager.getInstance(project).getStatus(file);
    return (! FileStatus.IGNORED.equals(status)) && (! FileStatus.UNKNOWN.equals(status));
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(Project project, SvnVcs17 activeVcs, VirtualFile file, DataContext context)
    throws VcsException {
    batchPerform(project, activeVcs, new VirtualFile[]{file}, context);
  }

  protected void batchPerform(Project project, SvnVcs17 activeVcs, VirtualFile[] file, DataContext context)
    throws VcsException {
    File[] ioFiles = new File[file.length];
    for (int i = 0; i < ioFiles.length; i++) {
      ioFiles[i] = new File(file[i].getPath());
    }

    SetPropertyDialog dialog = new SetPropertyDialog(project, ioFiles, null, true);
    dialog.show();

    if (dialog.isOK()) {
      String name = dialog.getPropertyName();
      String value = dialog.getPropertyValue();
      boolean recursive = dialog.isRecursive();

      SVNWCClient wcClient = activeVcs.createWCClient();
      for (int i = 0; i < ioFiles.length; i++) {
        File ioFile = ioFiles[i];
        try {
          wcClient.doSetProperty(ioFile, name, SVNPropertyValue.create(value), false, recursive, null);
        }
        catch (SVNException e) {
          throw new VcsException(e);
        }
      }
      for(int i = 0; i < file.length; i++) {
        if (recursive && file[i].isDirectory()) {
          VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file[i], true);
        } else {
          VcsDirtyScopeManager.getInstance(project).fileDirty(file[i]);
        }
      }
    }
  }

  protected boolean isBatchAction() {
    return true;
  }
}