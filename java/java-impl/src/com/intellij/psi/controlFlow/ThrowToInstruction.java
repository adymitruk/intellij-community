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
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;


public class ThrowToInstruction extends BranchingInstruction {

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ThrowToInstruction");

  public ThrowToInstruction(int offset) {
    super(offset, Role.END);
  }

  public String toString() {
    return "THROW_TO " + offset;
  }

  public int nNext() { return 1; }

  public int getNext(int index, int no) {
    LOG.assertTrue(no == 0);
    return offset;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitThrowToInstruction(this, offset, nextOffset);
  }
}