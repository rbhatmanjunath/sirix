package org.sirix.index.cas.xml;

import org.sirix.api.PageTrx;
import org.sirix.api.xml.XmlNodeReadOnlyTrx;
import org.sirix.index.IndexDef;
import org.sirix.index.cas.CASIndexBuilderFactory;
import org.sirix.index.cas.CASIndexListenerFactory;
import org.sirix.index.path.summary.PathSummaryReader;
import org.sirix.node.interfaces.DataRecord;
import org.sirix.page.UnorderedKeyValuePage;

public final class XmlCASIndexImpl implements XmlCASIndex {

  private final CASIndexBuilderFactory casIndexBuilderFactory;

  private final CASIndexListenerFactory casIndexListenerFactory;

  public XmlCASIndexImpl() {
    casIndexBuilderFactory = new CASIndexBuilderFactory();
    casIndexListenerFactory = new CASIndexListenerFactory();
  }

  @Override
  public XmlCASIndexBuilder createBuilder(XmlNodeReadOnlyTrx rtx,
      PageTrx<Long, DataRecord, UnorderedKeyValuePage> pageWriteTrx, PathSummaryReader pathSummaryReader,
      IndexDef indexDef) {
    final var indexBuilderDelegate = casIndexBuilderFactory.create(pageWriteTrx, pathSummaryReader, indexDef);
    return new XmlCASIndexBuilder(indexBuilderDelegate, rtx);
  }

  @Override
  public XmlCASIndexListener createListener(PageTrx<Long, DataRecord, UnorderedKeyValuePage> pageWriteTrx,
      PathSummaryReader pathSummaryReader, IndexDef indexDef) {
    final var indexListenerDelegate = casIndexListenerFactory.create(pageWriteTrx, pathSummaryReader, indexDef);
    return new XmlCASIndexListener(indexListenerDelegate);
  }
}
