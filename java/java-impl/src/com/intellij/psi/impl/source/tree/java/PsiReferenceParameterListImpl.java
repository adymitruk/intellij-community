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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class PsiReferenceParameterListImpl extends CompositePsiElement implements PsiReferenceParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiReferenceParameterListImpl");

  public PsiReferenceParameterListImpl() {
    super(JavaElementType.REFERENCE_PARAMETER_LIST);
  }

  @NotNull
  public PsiTypeElement[] getTypeParameterElements() {
    return getChildrenAsPsiElements(ElementType.TYPES_BIT_SET, ElementType.PSI_TYPE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  @NotNull
  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByReferenceParameterList(this);
  }

  public int getChildRole(ASTNode child) {
    IElementType i = child.getElementType();
    if (i == JavaElementType.TYPE) {
      return ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST;
    }
    else if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaTokenType.LT) {
      return ChildRole.LT_IN_TYPE_LIST;
    }
    else if (i == JavaTokenType.GT) {
      return ChildRole.GT_IN_TYPE_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LT_IN_TYPE_LIST:
        if (getFirstChildNode() != null && getFirstChildNode().getElementType() == JavaTokenType.LT){
          return getFirstChildNode();
        }
        else{
          return null;
        }

      case ChildRole.GT_IN_TYPE_LIST:
        if (getLastChildNode() != null && getLastChildNode().getElementType() == JavaTokenType.GT){
          return getLastChildNode();
        }
        else{
          return null;
        }
    }
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before){
    if (first == last && first.getElementType() == JavaElementType.TYPE){
      if (getLastChildNode() != null && getLastChildNode().getElementType() == TokenType.ERROR_ELEMENT){
        super.deleteChildInternal(getLastChildNode());
      }
    }

    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    if (getFirstChildNode()== null || getFirstChildNode().getElementType() != JavaTokenType.LT){
      TreeElement lt = Factory.createSingleLeafElement(JavaTokenType.LT, "<", 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, getFirstChildNode(), Boolean.TRUE);
    }
    if (getLastChildNode() == null || getLastChildNode().getElementType() != JavaTokenType.GT){
      TreeElement gt = Factory.createSingleLeafElement(JavaTokenType.GT, ">", 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, getLastChildNode(), Boolean.FALSE);
    }

    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && first.getElementType() == JavaElementType.TYPE){
      ASTNode element = first;
      for(ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (child.getElementType() == JavaElementType.TYPE){
          TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (child.getElementType() == JavaElementType.TYPE){
          TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    return firstAdded;
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.TYPE){
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == JavaTokenType.COMMA){
        deleteChildInternal(next);
      }
      else{
        ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null && prev.getElementType() == JavaTokenType.COMMA){
          deleteChildInternal(prev);
        }
      }
    }

    super.deleteChildInternal(child);

    if (getTypeParameterElements().length == 0){
      ASTNode lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      if (lt != null){
        deleteChildInternal(lt);
      }

      ASTNode gt = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
      if (gt != null){
        deleteChildInternal(gt);
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReferenceParameterList";
  }
}