package org.sirix.index.path.json;

import org.sirix.api.PageTrx;
import org.sirix.index.IndexDef;
import org.sirix.index.path.PathIndexBuilderFactory;
import org.sirix.index.path.PathIndexListenerFactory;
import org.sirix.index.path.summary.PathSummaryReader;
import org.sirix.node.interfaces.Record;
import org.sirix.page.UnorderedKeyValuePage;

public final class JsonPathIndexImpl implements JsonPathIndex {

  private final PathIndexBuilderFactory pathIndexBuilderFactory;

  private final PathIndexListenerFactory pathIndexListenerFactory;

  public JsonPathIndexImpl() {
    pathIndexBuilderFactory = new PathIndexBuilderFactory();
    pathIndexListenerFactory = new PathIndexListenerFactory();
  }

  @Override
  public JsonPathIndexBuilder createBuilder(final PageTrx<Long, Record, UnorderedKeyValuePage> pageWriteTrx,
      final PathSummaryReader pathSummaryReader, final IndexDef indexDef) {
    final var indexBuilderDelegate = pathIndexBuilderFactory.create(pageWriteTrx, pathSummaryReader, indexDef);
    return new JsonPathIndexBuilder(indexBuilderDelegate);
  }

  @Override
  public JsonPathIndexListener createListener(final PageTrx<Long, Record, UnorderedKeyValuePage> pageWriteTrx,
      final PathSummaryReader pathSummaryReader, final IndexDef indexDef) {
    final var indexListenerDelegate = pathIndexListenerFactory.create(pageWriteTrx, pathSummaryReader, indexDef);
    return new JsonPathIndexListener(indexListenerDelegate);
  }

}
