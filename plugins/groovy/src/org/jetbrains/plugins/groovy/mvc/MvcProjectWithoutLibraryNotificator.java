package org.jetbrains.plugins.groovy.mvc;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Sergey Evdokimov
 */
public class MvcProjectWithoutLibraryNotificator {

  private MvcProjectWithoutLibraryNotificator() {

  }

  public static void projectOpened(final Project project) {
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {

      @Override
      public void run() {
        Pair<Module, MvcFramework> pair = findModuleWithoutLibrary(project);

        if (pair != null) {
          final MvcFramework framework = pair.second;
          final Module module = pair.first;

          new Notification(framework.getFrameworkName() + ".Configure",
                           framework.getFrameworkName() + " SDK not found.",
                           "<html><body>Module '" +
                           module.getName() +
                           "' has no " +
                           framework.getFrameworkName() +
                           " SDK. <a href='create'>Configure SDK</a></body></html>", NotificationType.INFORMATION,
                           new NotificationListener() {
                             @Override
                             public void hyperlinkUpdate(@NotNull Notification notification,
                                                         @NotNull HyperlinkEvent event) {
                               MvcConfigureNotification.configure(framework, module);
                             }
                           }).notify(project);

        }
      }
    });
  }

  @Nullable
  private static Pair<Module, MvcFramework> findModuleWithoutLibrary(Project project) {
    MvcFramework[] frameworks = MvcFramework.EP_NAME.getExtensions();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (MvcFramework framework : frameworks) {
        VirtualFile appRoot = framework.findAppRoot(module);
        if (appRoot != null && appRoot.findChild("application.properties") != null) {
           if (!framework.hasFrameworkJar(module)) {
             return Pair.create(module, framework);
           }
        }
      }
    }

    return null;
  }
}
