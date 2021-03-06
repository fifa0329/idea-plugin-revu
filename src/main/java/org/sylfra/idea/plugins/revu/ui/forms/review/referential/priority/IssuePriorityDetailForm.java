package org.sylfra.idea.plugins.revu.ui.forms.review.referential.priority;

import com.intellij.ui.table.TableView;
import org.sylfra.idea.plugins.revu.model.IssuePriority;
import org.sylfra.idea.plugins.revu.ui.forms.review.referential.AbstractNameHolderDetailForm;

/**
 * @author <a href="mailto:syllant@gmail.com">Sylvain FRANCOIS</a>
 * @version $Id$
 */
public class IssuePriorityDetailForm extends AbstractNameHolderDetailForm<IssuePriority>
{
  protected IssuePriorityDetailForm(TableView<IssuePriority> table)
  {
    super(table);
  }
}