package org.sylfra.idea.plugins.revu.externalizing.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.sylfra.idea.plugins.revu.business.ReviewManager;
import org.sylfra.idea.plugins.revu.model.*;

import java.util.*;

/**
 * @author <a href="mailto:syllant@gmail.com">Sylvain FRANCOIS</a>
 * @version $Id$
 */
class ReviewConverter extends AbstractConverter
{
  private static final String REVU_SCHEMA_ID = "http://plugins.intellij.net/revu";
  private static final String REVU_SCHEMA_LOCATION = "http://plugins.intellij.net/revu/ns/revu_1_0.xsd";

  public boolean canConvert(Class type)
  {
    return Review.class.equals(type);
  }

  public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context)
  {
    Review review = (Review) source;

    writer.addAttribute("xmlns", REVU_SCHEMA_ID);
    writer.addAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    writer.addAttribute("xsi:schemaLocation", REVU_SCHEMA_ID + " " + REVU_SCHEMA_LOCATION);

    writer.addAttribute("name", review.getName());
    writer.addAttribute("status", review.getStatus().toString().toLowerCase());
    writer.addAttribute("shared", String.valueOf(review.isShared()));

    if (review.getExtendedReview() != null)
    {
      writer.addAttribute("extends", review.getExtendedReview().getName());
    }

    // History
    writer.startNode("history");
    context.convertAnother(review.getHistory());
    writer.endNode();

    // Desc
    if (review.getGoal() != null)
    {
      writer.startNode("goal");
      writer.setValue(review.getGoal());
      writer.endNode();
    }

    // Referential
    writer.startNode("referential");
    context.convertAnother(review.getDataReferential());
    writer.endNode();

    // FileScope
    writer.startNode("filescope");
    context.convertAnother(review.getFileScope());
    writer.endNode();

    // Issues
    writer.startNode("issues");
    SortedMap<VirtualFile, List<Issue>> issuesByFiles
      = new TreeMap<VirtualFile, List<Issue>>(new VirtualFileComparator());
    issuesByFiles.putAll(review.getIssuesByFiles());
    for (List<Issue> issues : issuesByFiles.values())
    {
      for (Issue issue : issues)
      {
        writer.startNode("issue");
        context.convertAnother(issue);
        writer.endNode();
      }
    }
    writer.endNode();
  }

  public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context)
  {
    Review review = getReview(context);

    String name = reader.getAttribute("name");
    String extendedReviewName = reader.getAttribute("extends");

    review.setName(name);

    if (Boolean.TRUE.equals(context.get(ReviewExternalizerXmlImpl.CONTEXT_KEY_PREPARE_MODE)))
    {
      if (extendedReviewName == null)
      {
        review.setExtendedReview(null);
      }
      else
      {
        review.setExtendedReview(new Review(extendedReviewName));
      }
      return review;
    }
    else
    {
      if (extendedReviewName == null)
      {
        review.setExtendedReview(null);
      }
      else
      {
        ReviewManager reviewManager = getProject(context).getComponent(ReviewManager.class);
        review.setExtendedReview(reviewManager.getReviewByName(extendedReviewName));
      }
    }

    String status = reader.getAttribute("status");
    String shared = reader.getAttribute("shared");

    review.setStatus(ReviewStatus.valueOf(status.toUpperCase()));
    review.setShared("true".equals(shared));

    while (reader.hasMoreChildren())
    {
      reader.moveDown();
      if ("issues".equals(reader.getNodeName()))
      {
        List<Issue> issues = new ArrayList<Issue>();
        while (reader.hasMoreChildren())
        {
          reader.moveDown();
          issues.add((Issue) context.convertAnother(issues, Issue.class));
          reader.moveUp();
        }
        review.setIssues(issues);
      }
      else if ("history".equals(reader.getNodeName()))
      {
        review.setHistory((History) context.convertAnother(review, History.class));
      }
      else if ("goal".equals(reader.getNodeName()))
      {
        review.setGoal(reader.getValue());
      }
      else if ("referential".equals(reader.getNodeName()))
      {
        review.setDataReferential((DataReferential) context.convertAnother(review, DataReferential.class));
      }
      else if ("filescope".equals(reader.getNodeName()))
      {
        review.setFileScope((FileScope) context.convertAnother(review, FileScope.class));
      }
      reader.moveUp();
    }

    return review;
  }

  private class VirtualFileComparator implements Comparator<VirtualFile>
  {
    public int compare(VirtualFile o1, VirtualFile o2)
    {
      return (o1 == null) ? -1 : ((o2 == null) ? -1 : o1.getPath().compareTo(o2.getPath()));
    }
  }
}