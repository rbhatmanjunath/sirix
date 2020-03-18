package org.sirix.index.cas.xml;

import org.sirix.access.trx.node.xml.AbstractXmlNodeVisitor;
import org.sirix.api.visitor.VisitResult;
import org.sirix.api.xml.XmlNodeReadOnlyTrx;
import org.sirix.index.cas.CASIndexBuilder;
import org.sirix.node.immutable.xml.ImmutableAttributeNode;
import org.sirix.node.immutable.xml.ImmutableText;

/**
 * Builds a content-and-structure (CAS) index.
 *
 * @author Johannes Lichtenberger
 *
 */
final class XmlCASIndexBuilder extends AbstractXmlNodeVisitor {

  private final CASIndexBuilder indexBuilderDelegate;

  private final XmlNodeReadOnlyTrx rtx;

  XmlCASIndexBuilder(final CASIndexBuilder indexBuilderDelegate, final XmlNodeReadOnlyTrx rtx) {
    this.indexBuilderDelegate = indexBuilderDelegate;
    this.rtx = rtx;
  }

  @Override
  public VisitResult visit(ImmutableText node) {
    rtx.moveTo(node.getParentKey());
    final long PCR = rtx.isDocumentRoot()
        ? 0
        : rtx.getNameNode().getPathNodeKey();

    return indexBuilderDelegate.process(node, PCR);
  }

  @Override
  public VisitResult visit(ImmutableAttributeNode node) {
    final long PCR = rtx.isDocumentRoot()
        ? 0
        : rtx.getNameNode().getPathNodeKey();

    return indexBuilderDelegate.process(node, PCR);
  }

}
