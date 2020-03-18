package org.sirix.axis.filter.json;

import org.sirix.api.Filter;
import org.sirix.api.ResourceManager;
import org.sirix.api.json.JsonNodeReadOnlyTrx;
import org.sirix.api.json.JsonNodeTrx;
import org.sirix.axis.AbstractTemporalAxis;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Filter for temporal axis.
 *
 * @author Johannes Lichtenberger
 *
 */
public final class TemporalJsonNodeReadFilterAxis<F extends Filter<JsonNodeReadOnlyTrx>>
    extends AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> {

  /** Axis to test. */
  private final AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> axis;

  /** Test to apply to axis. */
  private final List<F> axisFilter;

  /**
   * Constructor initializing internal state.
   *
   * @param axis axis to iterate over
   * @param firstAxisTest test to perform for each node found with axis
   * @param axisTest tests to perform for each node found with axis
   */
  @SafeVarargs
  public TemporalJsonNodeReadFilterAxis(final AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> axis,
      final F firstAxisTest, final F... axisTest) {
    checkNotNull(firstAxisTest);
    this.axis = axis;
    axisFilter = new ArrayList<F>();
    axisFilter.add(firstAxisTest);

    if (axisTest != null) {
      for (int i = 0, length = axisTest.length; i < length; i++) {
        axisFilter.add(axisTest[i]);
      }
    }
  }

  @Override
  protected JsonNodeReadOnlyTrx computeNext() {
    while (axis.hasNext()) {
      final JsonNodeReadOnlyTrx rtx = axis.next();
      final boolean filterResult = doFilter(rtx);
      if (filterResult) {
        return rtx;
      }

      rtx.close();
    }
    return endOfData();
  }

  private boolean doFilter(final JsonNodeReadOnlyTrx rtx) {
    boolean filterResult = true;
    for (final F filter : axisFilter) {
      filter.setTrx(rtx);
      filterResult = filterResult && filter.filter();
      if (!filterResult) {
        break;
      }
    }
    return filterResult;
  }

  /**
   * Returns the inner axis.
   *
   * @return the axis
   */
  public AbstractTemporalAxis<JsonNodeReadOnlyTrx, JsonNodeTrx> getAxis() {
    return axis;
  }

  @Override
  public ResourceManager<JsonNodeReadOnlyTrx, JsonNodeTrx> getResourceManager() {
    return axis.getResourceManager();
  }
}
