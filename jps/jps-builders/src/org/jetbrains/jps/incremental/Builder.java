package org.jetbrains.jps.incremental;

/**
 * @see ModuleLevelBuilder
 * @see ProjectLevelBuilder
 *
 * @author nik
 */
public abstract class Builder {
  public abstract String getName();

  public abstract String getDescription();

  public void buildStarted(CompileContext compileContext) {
  }

  public void buildFinished(CompileContext compileContext) {
  }
}
