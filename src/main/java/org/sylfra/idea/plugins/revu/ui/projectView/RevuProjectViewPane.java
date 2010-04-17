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

package org.sylfra.idea.plugins.revu.ui.projectView;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.scopeView.ScopeTreeViewPanel;
import com.intellij.ide.scopeView.nodes.BasePsiNode;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sylfra.idea.plugins.revu.RevuBundle;
import org.sylfra.idea.plugins.revu.business.IReviewListener;
import org.sylfra.idea.plugins.revu.business.ReviewManager;
import org.sylfra.idea.plugins.revu.model.Review;
import org.sylfra.idea.plugins.revu.model.ReviewStatus;
import org.sylfra.idea.plugins.revu.utils.RevuUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

/**
 * @author <a href="mailto:syllant@gmail.com">Sylvain FRANCOIS</a>
 * @version $Id$
 */
public class RevuProjectViewPane extends AbstractProjectViewPane implements IReviewListener
{
  private final static ReviewStatus[] VISIBLE_STATUSES
    = {ReviewStatus.DRAFT, ReviewStatus.FIXING, ReviewStatus.REVIEWING};

  @NonNls
  public static final String ID = "Revu";
  public static final Icon ICON = IconLoader.getIcon("/org/sylfra/idea/plugins/revu/resources/icons/revu.png");

  private final ProjectView myProjectView;
  private ScopeTreeViewPanel myViewPanel;
  private final Map<Review, NamedScope> scopes;

  public RevuProjectViewPane(Project project, ProjectView projectView)
  {
    super(project);
    myProjectView = projectView;
    scopes = new IdentityHashMap<Review, NamedScope>();

    ReviewManager reviewManager = project.getComponent(ReviewManager.class);
    reviewManager.addReviewListener(this);
    for (Review review : reviewManager.getReviews(RevuUtils.getCurrentUserLogin(), VISIBLE_STATUSES))
    {
      reviewAdded(review);
    }
  }

  public String getTitle()
  {
    return RevuBundle.message("general.plugin.title");
  }

  public Icon getIcon()
  {
    return ICON;
  }

  @NotNull
  public String getId()
  {
    return ID;
  }


  public JComponent createComponent()
  {
    myViewPanel = new ScopeTreeViewPanel(myProject);
    Disposer.register(this, myViewPanel);
    myViewPanel.initListeners();
    updateFromRoot(true);

    myTree = myViewPanel.getTree();
    PopupHandler.installPopupHandler(myTree, "RevuProjectViewPopupMenu", "RevuProjectViewPopup");
    enableDnD();

    myTree.setCellRenderer(new CustomTreeCellRenderer(myProject, myViewPanel));

    return myViewPanel.getPanel();
  }

  public void dispose()
  {
    myViewPanel = null;
    super.dispose();
  }

  @NotNull
  public String[] getSubIds()
  {
    String[] ids = new String[scopes.size()];
    int i = 0;
    for (NamedScope namedScope : scopes.values())
    {
      ids[i++] = namedScope.getName();
    }

    return ids;
  }

  @NotNull
  public String getPresentableSubIdName(@NotNull final String subId)
  {
    return subId;
  }

  public void addToolbarActions(DefaultActionGroup actionGroup)
  {
    actionGroup.add(ActionManager.getInstance().getAction("revu.ProjectView.ToggleFilterIssues"));
    actionGroup.add(ActionManager.getInstance().getAction("revu.ShowProjectSettings"));
  }

  public ActionCallback updateFromRoot(boolean restoreExpandedPaths)
  {
    for (NamedScope namedScope : scopes.values())
    {
      if (namedScope.getName().equals(getSubId()))
      {
        myViewPanel.selectScope(namedScope);
        break;
      }
    }

    return new ActionCallback.Done();
  }

  public void select(Object element, VirtualFile file, boolean requestFocus)
  {
    if (file == null)
    {
      return;
    }
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null)
    {
      return;
    }
    if (!(element instanceof PsiElement))
    {
      return;
    }

    List<NamedScope> allScopes = new ArrayList<NamedScope>(scopes.values());
    for (int i = 0; i < allScopes.size(); i++)
    {
      final NamedScope scope = allScopes.get(i);
      String name = scope.getName();
      if (name.equals(getSubId()))
      {
        allScopes.set(i, allScopes.get(0));
        allScopes.set(0, scope);
        break;
      }
    }
    for (NamedScope scope : allScopes)
    {
      String name = scope.getName();
      PackageSet packageSet = scope.getValue();
      if (packageSet == null)
      {
        continue;
      }
      if (changeView(packageSet, ((PsiElement) element), psiFile, name, requestFocus))
      {
        break;
      }
    }
  }

  private boolean changeView(final PackageSet packageSet, final PsiElement element, final PsiFile psiFile,
    final String name, boolean requestFocus)
  {
    if (packageSet.contains(psiFile, NamedScopesHolder.getHolder(myProject, name, null)))
    {
      if (!name.equals(getSubId()))
      {
        myProjectView.changeView(getId(), name);
      }
      myViewPanel.selectNode(element, psiFile, requestFocus);
      return true;
    }

    return false;
  }

  public int getWeight()
  {
    return Integer.MAX_VALUE;
  }

  public void installComparator()
  {
    myViewPanel.setSortByType();
  }

  public SelectInTarget createSelectInTarget()
  {
    return new RevuPaneSelectInTarget(myProject);
  }

  protected Object exhumeElementFromNode(final DefaultMutableTreeNode node)
  {
    if (node instanceof PackageDependenciesNode)
    {
      return ((PackageDependenciesNode) node).getPsiElement();
    }
    return super.exhumeElementFromNode(node);
  }

  public Object getData(final String dataId)
  {
    final Object data = super.getData(dataId);
    if (data != null)
    {
      return data;
    }
    return myViewPanel != null ? myViewPanel.getData(dataId) : null;
  }

  public void reviewChanged(Review review)
  {
    reviewAdded(review);
  }

  public void reviewAdded(Review review)
  {
    if (!Arrays.asList(VISIBLE_STATUSES).contains(review.getStatus()))
    {
      return;
    }
    scopes.put(review, new NamedScope(getScopeName(review), new RevuPackageSet(myProject, review)));
    refreshView();
  }

  public void reviewDeleted(Review review)
  {
    scopes.remove(review);
    refreshView();
  }

  private void refreshView()
  {
    Alarm refreshProjectViewAlarm = new Alarm();
    // amortize batch scope changes
    refreshProjectViewAlarm.cancelAllRequests();
    refreshProjectViewAlarm.addRequest(new Runnable()
    {
      public void run()
      {
        if (myProject.isDisposed())
        {
          return;
        }
        myProjectView.removeProjectPane(RevuProjectViewPane.this);
        myProjectView.addProjectPane(RevuProjectViewPane.this);
      }
    }, 10);
  }

  private String getScopeName(Review review)
  {
    return review.getName();
  }

  // See com.intellij.ide.scopeView.ScopeTreeViewPanel.MyTreeCellRenderer

  private class CustomTreeCellRenderer extends ColoredTreeCellRenderer
  {
    private final Project project;
    private final ScopeTreeViewPanel scopeTreeViewPanel;

    public CustomTreeCellRenderer(Project project, ScopeTreeViewPanel scopeTreeViewPanel)
    {
      this.project = project;
      this.scopeTreeViewPanel = scopeTreeViewPanel;
    }

    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf,
      int row, boolean hasFocus)
    {
      if (value instanceof PackageDependenciesNode)
      {
        PackageDependenciesNode node = (PackageDependenciesNode) value;
        try
        {
          setIcon(expanded ? node.getOpenIcon() : node.getClosedIcon());
        }
        catch (IndexNotReadyException ignore)
        {
        }
        final SimpleTextAttributes regularAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        TextAttributes textAttributes = regularAttributes.toTextAttributes();
        if (node instanceof BasePsiNode && ((BasePsiNode) node).isDeprecated())
        {
          textAttributes =
            EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.DEPRECATED_ATTRIBUTES)
              .clone();
        }
        final PsiElement psiElement = node.getPsiElement();
        textAttributes.setForegroundColor(
          CopyPasteManager.getInstance().isCutElement(psiElement) ? CopyPasteManager.CUT_COLOR :
            node.getStatus().getColor());
        append(node.toString(), SimpleTextAttributes.fromTextAttributes(textAttributes));

        String oldToString = toString();
        for (ProjectViewNodeDecorator decorator : Extensions.getExtensions(ProjectViewNodeDecorator.EP_NAME, myProject))
        {
          decorator.decorate(node, this);
        }

        int issueCount = retrieveIssueCount(psiElement);
        if (issueCount > 0)
        {
          append(" [" + RevuBundle.message("projectView.issueCount.text", issueCount) + "]",
            SimpleTextAttributes.GRAY_ATTRIBUTES);
        }

        if (toString().equals(oldToString))
        {   // nothing was decorated
          final String locationString = node.getComment();
          if (locationString != null && locationString.length() > 0)
          {
            append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
      }
    }

    private int retrieveIssueCount(PsiElement psiElement)
    {
      if ((psiElement == null) || (psiElement.getContainingFile() == null))
      {
        return 0;
      }

      String reviewName = scopeTreeViewPanel.CURRENT_SCOPE_NAME;
      Review review = project.getComponent(ReviewManager.class).getReviewByName(reviewName);

      return (review == null) ? 0 : review.getIssues(psiElement.getContainingFile().getVirtualFile()).size();
    }
  }
}
