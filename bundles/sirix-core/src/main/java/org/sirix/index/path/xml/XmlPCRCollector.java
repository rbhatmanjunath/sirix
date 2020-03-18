package org.sirix.index.path.xml;

import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.path.Path;
import org.sirix.api.NodeReadOnlyTrx;
import org.sirix.api.xml.XmlNodeReadOnlyTrx;
import org.sirix.api.xml.XmlNodeTrx;
import org.sirix.index.path.AbstractPCRCollector;
import org.sirix.index.path.PCRCollector;
import org.sirix.index.path.PCRValue;
import org.sirix.index.path.summary.PathSummaryReader;

import java.util.Objects;
import java.util.Set;

public final class XmlPCRCollector extends AbstractPCRCollector implements PCRCollector {

  private final NodeReadOnlyTrx rtx;

  public XmlPCRCollector(final XmlNodeReadOnlyTrx rtx) {
    this.rtx = Objects.requireNonNull(rtx, "The transaction must not be null.");
  }

  @Override
  public PCRValue getPCRsForPaths(Set<Path<QNm>> paths) {
    final PathSummaryReader reader = rtx instanceof XmlNodeTrx
        ? ((XmlNodeTrx) rtx).getPathSummary()
        : rtx.getResourceManager().openPathSummary(rtx.getRevisionNumber());
    try {
      return getPcrValue(paths, reader);
    } finally {
      if (!(rtx instanceof XmlNodeTrx)) {
        reader.close();
      }
    }
  }
}
