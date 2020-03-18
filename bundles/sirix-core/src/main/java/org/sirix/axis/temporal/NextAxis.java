package org.sirix.axis.temporal;

import org.sirix.api.NodeCursor;
import org.sirix.api.NodeReadOnlyTrx;
import org.sirix.api.NodeTrx;
import org.sirix.api.ResourceManager;
import org.sirix.axis.AbstractTemporalAxis;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Open the next revision and try to move to the node with the given node key.
 *
 * @author Johannes Lichtenberger
 *
 */
public final class NextAxis<R extends NodeReadOnlyTrx & NodeCursor, W extends NodeTrx & NodeCursor>
    extends AbstractTemporalAxis<R, W> {

  /** Sirix {@link ResourceManager}. */
  private final ResourceManager<R, W> resourceManager;

  /** Determines if it's the first call. */
  private boolean isFirst;

  /** The revision number. */
  private int revision;

  /** Node key to lookup and retrieve. */
  private long nodeKey;

  /**
   * Constructor.
   *
   * @param rtx Sirix {@link NodeReadOnlyTrx}
   */
  public NextAxis(final ResourceManager<R, W> resourceManager, final R rtx) {
    this.resourceManager = checkNotNull(resourceManager);
    this.revision = 0;
    this.nodeKey = rtx.getNodeKey();
    this.revision = rtx.getRevisionNumber() + 1;
    this.isFirst = true;
  }

  @Override
  protected R computeNext() {
    if (revision <= resourceManager.getMostRecentRevisionNumber() && isFirst) {
      isFirst = false;

      final Optional<R> optionalRtx = resourceManager.getNodeReadTrxByRevisionNumber(revision);

      final R rtx;
      if (optionalRtx.isPresent()) {
        rtx = optionalRtx.get();
      } else {
        rtx = resourceManager.beginNodeReadOnlyTrx(revision);
      }

      revision++;

      if (rtx.moveTo(nodeKey).hasMoved()) {
        return rtx;
      } else {
        rtx.close();
        return endOfData();
      }
    } else {
      return endOfData();
    }
  }

  @Override
  public ResourceManager<R, W> getResourceManager() {
    return resourceManager;
  }
}
