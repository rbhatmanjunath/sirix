package org.sirix.index.path.xml;

import org.sirix.access.trx.node.xml.AbstractXmlNodeVisitor;
import org.sirix.api.visitor.VisitResult;
import org.sirix.index.path.PathIndexBuilder;
import org.sirix.node.immutable.xml.ImmutableAttributeNode;
import org.sirix.node.immutable.xml.ImmutableElement;

public final class XmlPathIndexBuilder extends AbstractXmlNodeVisitor {

  private final PathIndexBuilder pathIndexBuilder;

  public XmlPathIndexBuilder(final PathIndexBuilder pathIndexBuilderDelegate) {
    pathIndexBuilder = pathIndexBuilderDelegate;
  }

  @Override
  public VisitResult visit(ImmutableElement node) {
    return pathIndexBuilder.process(node, node.getPathNodeKey());
  }

  @Override
  public VisitResult visit(ImmutableAttributeNode node) {
    return pathIndexBuilder.process(node, node.getPathNodeKey());
  }

}
