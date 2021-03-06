/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.propertyTable.editors;

import com.android.resources.ResourceType;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.componentTree.TreeNodeDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResourceDialog extends DialogWrapper implements TreeSelectionListener {
  private static final String ANDROID = "@android:";

  private static final Icon RESOURCE_ITEM_ICON = AllIcons.Css.Property;

  private final JBTabbedPane myContentPanel;
  private final ResourcePanel myProjectPanel;
  private final ResourcePanel mySystemPanel;
  private ColorPicker myColorPicker;
  private final Action myNewResourceAction = new AbstractAction("New Resource...") {
    @Override
    public void actionPerformed(ActionEvent e) {
      // TODO: Auto-generated method stub
    }
  };
  private String myResultResourceName;

  public ResourceDialog(Module module, ResourceType[] types, String value) {
    super(module.getProject());

    setTitle("Resources");

    AndroidFacet facet = AndroidFacet.getInstance(module);
    myProjectPanel = new ResourcePanel(facet, types, false);
    mySystemPanel = new ResourcePanel(facet, types, true);

    myContentPanel = new JBTabbedPane();
    myContentPanel.setPreferredSize(new Dimension(500, 400));
    myContentPanel.addTab("Project", myProjectPanel.myComponent);
    myContentPanel.addTab("System", mySystemPanel.myComponent);

    myProjectPanel.myTreeBuilder.expandAll(null);
    mySystemPanel.myTreeBuilder.expandAll(null);

    boolean doSelection = value != null;

    if (types == ResourceEditor.COLOR_TYPES) {
      Color color = ResourceRenderer.parseColor(value);
      myColorPicker = new ColorPicker(myDisposable, color, true);
      myContentPanel.addTab("Color", myColorPicker);
      if (color != null) {
        myContentPanel.setSelectedIndex(2);
        doSelection = false;
      }
    }
    if (doSelection && value.startsWith("@")) {
      ResourcePanel panel;
      String type;
      int index = value.indexOf('/');
      String name = value.substring(index + 1);
      if (value.startsWith(ANDROID)) {
        panel = mySystemPanel;
        type = value.substring(ANDROID.length(), index);
      }
      else {
        panel = myProjectPanel;
        type = value.substring(1, index);
      }
      myContentPanel.setSelectedComponent(panel.myComponent);
      panel.select(type, name);
    }

    myContentPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myNewResourceAction.setEnabled(myContentPanel.getSelectedComponent() == myProjectPanel.myComponent);
        valueChanged(null);
      }
    });

    init();
    valueChanged(null);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProjectPanel.myTree;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @Override
  protected Action[] createLeftSideActions() {
    return super.createLeftSideActions();//new Action[]{myNewResourceAction};
  }

  @Override
  protected void dispose() {
    super.dispose();
    Disposer.dispose(myProjectPanel.myTreeBuilder);
    Disposer.dispose(mySystemPanel.myTreeBuilder);
  }

  public String getResourceName() {
    return myResultResourceName;
  }

  @Override
  protected void doOKAction() {
    valueChanged(null);
    super.doOKAction();
  }

  @Override
  public void valueChanged(@Nullable TreeSelectionEvent e) {
    Component selectedComponent = myContentPanel.getSelectedComponent();

    if (selectedComponent == myColorPicker) {
      Color color = myColorPicker.getColor();
      getOKAction().setEnabled(color != null);
      myResultResourceName = color == null ? null : "#" + toHex(color.getRed()) + toHex(color.getGreen()) + toHex(color.getBlue());
    }
    else {
      ResourcePanel panel = selectedComponent == myProjectPanel.myComponent ? myProjectPanel : mySystemPanel;
      Set<ResourceItem> elements = panel.myTreeBuilder.getSelectedElements(ResourceItem.class);
      getOKAction().setEnabled(!elements.isEmpty());

      if (elements.isEmpty()) {
        myResultResourceName = null;
      }
      else {
        String prefix = panel == myProjectPanel ? "@" : ANDROID;
        myResultResourceName = prefix + elements.iterator().next().getName();
      }
    }
  }

  private static String toHex(int value) {
    String hex = Integer.toString(value, 16);
    return hex.length() == 1 ? "0" + hex : hex;
  }

  private class ResourcePanel {
    public final Tree myTree;
    public final AbstractTreeBuilder myTreeBuilder;
    public final JScrollPane myComponent;
    private final ResourceGroup[] myGroups;

    public ResourcePanel(AndroidFacet facet, ResourceType[] types, boolean system) {
      myTree = new Tree();
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
      myTree.setScrollsOnExpand(true);
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          if (!myTreeBuilder.getSelectedElements(ResourceItem.class).isEmpty()) {
            close(OK_EXIT_CODE);
            return true;
          }
          return false;
        }
      }.installOn(myTree);

      ToolTipManager.sharedInstance().registerComponent(myTree);
      TreeUtil.installActions(myTree);

      ResourceManager manager = facet.getResourceManager(system ? AndroidUtils.SYSTEM_RESOURCE_PACKAGE : null);
      myGroups = new ResourceGroup[types.length];

      for (int i = 0; i < types.length; i++) {
        myGroups[i] = new ResourceGroup(types[i], manager);
      }

      myTreeBuilder =
        new AbstractTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), new TreeContentProvider(myGroups), null);
      myTreeBuilder.initRootNode();

      TreeSelectionModel selectionModel = myTree.getSelectionModel();
      selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      selectionModel.addTreeSelectionListener(ResourceDialog.this);

      myTree.setCellRenderer(new NodeRenderer() {
        @Override
        protected void doAppend(@NotNull @Nls String fragment,
                                @NotNull SimpleTextAttributes attributes,
                                boolean isMainText,
                                boolean selected) {
          SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, attributes, selected, this);
        }

        @Override
        public void doAppend(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, boolean selected) {
          SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, attributes, selected, this);
        }

        @Override
        public void doAppend(String fragment, boolean selected) {
          SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES, selected, this);
        }
      });
      new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);

      myComponent = ScrollPaneFactory.createScrollPane(myTree);
    }

    private void select(String type, String name) {
      for (ResourceGroup group : myGroups) {
        if (type.equalsIgnoreCase(group.getName())) {
          for (ResourceItem item : group.getItems()) {
            if (name.equals(item.toString())) {
              myTreeBuilder.select(item);
              return;
            }
          }
          return;
        }
      }
    }
  }

  private static class ResourceGroup {
    private List<ResourceItem> myItems = new ArrayList<ResourceItem>();
    private final ResourceType myType;

    public ResourceGroup(ResourceType type, ResourceManager manager) {
      myType = type;

      String resourceType = type.getName();

      Collection<String> resourceNames = manager.getValueResourceNames(resourceType);
      for (String resourceName : resourceNames) {
        myItems.add(new ResourceItem(this, resourceName, RESOURCE_ITEM_ICON));
      }

      Set<String> fileNames = new HashSet<String>();
      List<VirtualFile> dirs = manager.getResourceSubdirs(resourceType);
      for (VirtualFile dir : dirs) {
        for (VirtualFile resourceFile : dir.getChildren()) {
          if (!resourceFile.isDirectory()) {
            String fileName = AndroidCommonUtils.getResourceName(resourceType, resourceFile.getName());
            if (fileNames.add(fileName)) {
              myItems.add(new ResourceItem(this, fileName, resourceFile.getFileType().getIcon()));
            }
          }
        }
      }

      if (type == ResourceType.ID) {
        for (String id : manager.getIds()) {
          if (!resourceNames.contains(id)) {
            myItems.add(new ResourceItem(this, id, RESOURCE_ITEM_ICON));
          }
        }
      }

      Collections.sort(myItems, new Comparator<ResourceItem>() {
        @Override
        public int compare(ResourceItem resource1, ResourceItem resource2) {
          return resource1.toString().compareTo(resource2.toString());
        }
      });
    }

    public String getName() {
      return myType.getName();
    }

    public List<ResourceItem> getItems() {
      return myItems;
    }

    @Override
    public String toString() {
      return myType.getDisplayName();
    }
  }

  private static class ResourceItem {
    private final ResourceGroup myGroup;
    private final String myName;
    private final Icon myIcon;

    public ResourceItem(@NotNull ResourceGroup group, @NotNull String name, Icon icon) {
      myGroup = group;
      myName = name;
      myIcon = icon;
    }

    public ResourceGroup getGroup() {
      return myGroup;
    }

    public String getName() {
      return myGroup.getName() + "/" + myName;
    }

    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  private static class TreeContentProvider extends AbstractTreeStructure {
    private final Object myTreeRoot = new Object();
    private final ResourceGroup[] myGroups;

    public TreeContentProvider(ResourceGroup[] groups) {
      myGroups = groups;
    }

    @Override
    public Object getRootElement() {
      return myTreeRoot;
    }

    @Override
    public Object[] getChildElements(Object element) {
      if (element == myTreeRoot) {
        return myGroups;
      }
      if (element instanceof ResourceGroup) {
        ResourceGroup group = (ResourceGroup)element;
        return group.getItems().toArray();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public Object getParentElement(Object element) {
      if (element instanceof ResourceItem) {
        ResourceItem resource = (ResourceItem)element;
        return resource.getGroup();
      }
      return null;
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      TreeNodeDescriptor descriptor = new TreeNodeDescriptor(parentDescriptor, element, element == null ? null : element.toString());
      if (element instanceof ResourceGroup) {
        descriptor.setIcon(AllIcons.Nodes.TreeOpen, AllIcons.Nodes.TreeClosed);
      }
      else if (element instanceof ResourceItem) {
        descriptor.setIcon(((ResourceItem)element).getIcon());
      }
      return descriptor;
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public void commit() {
    }
  }
}
