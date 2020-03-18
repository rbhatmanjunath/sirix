package org.sirix.index.path;

import java.util.Collections;
import java.util.Set;

public class PCRValue {
  private final long maxPCR;

  private final Set<Long> pcrs;

  private PCRValue(final long maxPCR, final Set<Long> pcrs) {
    this.maxPCR = maxPCR;
    this.pcrs = pcrs;
  }

  public static final PCRValue getInstance(final long maxPCR, final Set<Long> pcrs) {
    return new PCRValue(maxPCR, pcrs);
  }

  public static final PCRValue getEmptyInstance() {
    return new PCRValue(0, Collections.emptySet());
  }

  public long getMaxPCR() {
    return maxPCR;
  }

  public Set<Long> getPCRs() {
    return pcrs;
  }
}
