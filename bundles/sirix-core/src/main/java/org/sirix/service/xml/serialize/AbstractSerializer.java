/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met: * Redistributions of source code must retain the
 * above copyright notice, this list of conditions and the following disclaimer. * Redistributions
 * in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sirix.service.xml.serialize;

import org.sirix.api.Axis;
import org.sirix.api.ResourceManager;
import org.sirix.api.xml.XmlNodeReadOnlyTrx;
import org.sirix.api.xml.XmlResourceManager;
import org.sirix.axis.DescendantAxis;
import org.sirix.axis.IncludeSelf;
import org.sirix.exception.SirixException;
import org.sirix.node.NodeKind;
import org.sirix.settings.Constants;

import javax.annotation.Nonnegative;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class implements main serialization algorithm. Other classes can extend it.
 *
 * @author Johannes Lichtenberger, University of Konstanz
 *
 */
public abstract class AbstractSerializer implements Callable<Void> {

  /** Sirix {@link ResourceManager}. */
  protected final XmlResourceManager resMgr;

  /** Stack for reading end element. */
  protected final Deque<Long> stack;

  /** Array with versions to print. */
  protected final int[] revisions;

  /** Root node key of subtree to shredder. */
  protected final long nodeKey;

  /**
   * Constructor.
   *
   * @param resMgr Sirix {@link ResourceManager}
   * @param revision first revision to serialize
   * @param revisions revisions to serialize
   */
  public AbstractSerializer(final XmlResourceManager resMgr, final @Nonnegative int revision, final int... revisions) {
    stack = new ArrayDeque<>();
    this.revisions = revisions == null
        ? new int[1]
        : new int[revisions.length + 1];
    initialize(revision, revisions);
    this.resMgr = checkNotNull(resMgr);
    nodeKey = 0;
  }

  /**
   * Constructor.
   *
   * @param resMgr Sirix {@link ResourceManager}
   * @param key key of root node from which to shredder the subtree
   * @param revision first revision to serialize
   * @param revisions revisions to serialize
   */
  public AbstractSerializer(final XmlResourceManager resMgr, final @Nonnegative long key,
      final @Nonnegative int revision, final int... revisions) {
    stack = new ArrayDeque<>();
    this.revisions = revisions == null
        ? new int[1]
        : new int[revisions.length + 1];
    initialize(revision, revisions);
    this.resMgr = checkNotNull(resMgr);
    nodeKey = key;
  }

  /**
   * Initialize.
   *
   * @param revision first revision to serialize
   * @param revisions revisions to serialize
   */
  private void initialize(final @Nonnegative int revision, final int... revisions) {
    this.revisions[0] = revision;
    if (revisions != null) {
      for (int i = 0; i < revisions.length; i++) {
        this.revisions[i + 1] = revisions[i];
      }
    }
  }

  /**
   * Serialize the storage.
   *
   * @return null.
   * @throws SirixException if can't call serailzer
   */
  @Override
  public Void call() throws SirixException {
    emitStartDocument();

    final int nrOfRevisions = revisions.length;
    final int length = (nrOfRevisions == 1 && revisions[0] < 0)
        ? (int) resMgr.getMostRecentRevisionNumber()
        : nrOfRevisions;

    for (int i = 1; i <= length; i++) {
      try (final XmlNodeReadOnlyTrx rtx = resMgr.beginNodeReadOnlyTrx((nrOfRevisions == 1 && revisions[0] < 0)
          ? i
          : revisions[i - 1])) {
        emitRevisionStartTag(rtx);

        rtx.moveTo(nodeKey);

        final Axis descAxis = new DescendantAxis(rtx, IncludeSelf.YES);

        // Setup primitives.
        boolean closeElements = false;
        long key = rtx.getNodeKey();

        // Iterate over all nodes of the subtree including self.
        while (descAxis.hasNext()) {
          key = descAxis.next();

          // Emit all pending end elements.
          if (closeElements) {
            while (!stack.isEmpty() && stack.peek() != rtx.getLeftSiblingKey()) {
              rtx.moveTo(stack.pop());
              emitEndTag(rtx);
              rtx.moveTo(key);
            }
            if (!stack.isEmpty()) {
              rtx.moveTo(stack.pop());
              emitEndTag(rtx);
            }
            rtx.moveTo(key);
            closeElements = false;
          }

          // Emit node.
          emitNode(rtx);

          // Push end element to stack if we are a start element with
          // children.
          if (rtx.getKind() == NodeKind.ELEMENT && rtx.hasFirstChild()) {
            stack.push(rtx.getNodeKey());
          }

          // Remember to emit all pending end elements from stack if
          // required.
          if (!rtx.hasFirstChild() && !rtx.hasRightSibling()) {
            closeElements = true;
          }
        }

        // Finally emit all pending end elements.
        while (!stack.isEmpty() && stack.peek() != Constants.NULL_ID_LONG) {
          rtx.moveTo(stack.pop());
          emitEndTag(rtx);
        }

        emitRevisionEndTag(rtx);
      }
    }

    emitEndDocument();

    return null;
  }

  /**
   * Emit start document.
   */
  protected abstract void emitStartDocument();

  /**
   * Emit start tag.
   *
   * @param rtx Sirix {@link XmlNodeReadOnlyTrx}
   */
  protected abstract void emitNode(XmlNodeReadOnlyTrx rtx);

  /**
   * Emit end tag.
   *
   * @param rtx Sirix {@link XmlNodeReadOnlyTrx}
   */
  protected abstract void emitEndTag(XmlNodeReadOnlyTrx rtx);

  /**
   * Emit a start tag, which specifies a revision.
   *
   * @param rtx Sirix {@link XmlNodeReadOnlyTrx}
   */
  protected abstract void emitRevisionStartTag(XmlNodeReadOnlyTrx rtx);

  /**
   * Emit an end tag, which specifies a revision.
   *
   * @param rtx Sirix {@link XmlNodeReadOnlyTrx}
   */
  protected abstract void emitRevisionEndTag(XmlNodeReadOnlyTrx rtx);

  /** Emit end document. */
  protected abstract void emitEndDocument();
}
