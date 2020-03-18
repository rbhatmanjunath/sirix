package org.sirix.index.cas;

import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.path.Path;
import org.sirix.index.AtomicUtil;
import org.sirix.index.Filter;
import org.sirix.index.avltree.AVLNode;
import org.sirix.index.avltree.keyvalue.CASValue;
import org.sirix.index.avltree.keyvalue.NodeReferences;
import org.sirix.index.path.PCRCollector;
import org.sirix.index.path.PathFilter;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * CASFilter filter.
 *
 * @author Johannes Lichtenberger, University of Konstanz
 *
 */
public final class CASFilterRange implements Filter {

  /** The paths to filter. */
  private final Set<Path<QNm>> paths;

  /** {@link PathFilter} instance to filter specific paths. */
  private final PathFilter pathFilter;

  /** The minimum value. */
  private final Atomic minValue;

  /** The maximum value. */
  private final Atomic maxValue;

  /** {@code true} if the minimum should be included, {@code false} otherwise */
  private final boolean includeMinValue;

  /** {@code true} if the maximum should be included, {@code false} otherwise */
  private final boolean includeMaxValue;

  /**
   * Constructor. Initializes the internal state.
   *
   * @param paths paths to match
   * @param min the minimum value
   * @param max the maximum value
   * @param incMin include the minimum value
   * @param incMax include the maximum value
   * @param pcrCollector the PCR collector used
   */
  public CASFilterRange(final Set<Path<QNm>> paths, final Atomic min, final Atomic max,
      final boolean incMin, final boolean incMax, final PCRCollector pcrCollector) {
    this.paths = checkNotNull(paths);
    pathFilter = new PathFilter(this.paths, pcrCollector);
    minValue = checkNotNull(min);
    maxValue = checkNotNull(max);
    includeMinValue = incMin;
    includeMaxValue = incMax;
  }

  @Override
  public <K extends Comparable<? super K>> boolean filter(final AVLNode<K, NodeReferences> node) {
    final K key = node.getKey();
    if (key instanceof CASValue) {
      final CASValue casValue = (CASValue) key;
      final boolean filtered = pathFilter.filter(node);

      if (filtered) {
        return inRange(AtomicUtil.toType(casValue.getAtomicValue(), casValue.getType()));
      }
    }
    return false;
  }

  private <K extends Comparable<? super K>> boolean inRange(Atomic key) {
    final int minKeyCompare = (minValue != null) ? minValue.compareTo(key) : -1;
    final int maxKeyCompare = (maxValue != null) ? maxValue.compareTo(key) : 1;

    final boolean lowerBoundValid = ((minKeyCompare == 0) && (includeMinValue)) || (minKeyCompare < 0);
    final boolean upperBoundValid = ((maxKeyCompare == 0) && (includeMaxValue)) || (maxKeyCompare > 0);

    return upperBoundValid && lowerBoundValid;
  }
}
