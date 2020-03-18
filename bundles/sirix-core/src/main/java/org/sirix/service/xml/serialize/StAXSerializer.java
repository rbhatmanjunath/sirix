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

import org.brackit.xquery.atomic.QNm;
import org.sirix.api.Axis;
import org.sirix.api.xml.XmlNodeReadOnlyTrx;
import org.sirix.axis.DescendantAxis;
import org.sirix.axis.IncludeSelf;
import org.sirix.axis.filter.FilterAxis;
import org.sirix.axis.filter.xml.TextFilter;
import org.sirix.node.NodeKind;
import org.sirix.utils.XMLToken;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * <h1>StAXSerializer</h1>
 *
 * <p>
 * Provides a StAX implementation (event API) for retrieving a sirix database.
 * </p>
 *
 * @author Johannes Lichtenberger, University of Konstanz
 *
 */
public final class StAXSerializer implements XMLEventReader {

  /**
   * Determines if start tags have to be closed, thus if end tags have to be emitted.
   */
  private boolean hasToCloseElements;

  /** {@link XMLEvent}. */
  private XMLEvent event;

  /** {@link XMLEventFactory} to create events. */
  private final XMLEventFactory eventFactory = XMLEventFactory.newFactory();

  /** Current node key. */
  private long key;

  /** Determines if all end tags have been emitted. */
  private boolean closeElementsEmitted;

  /** Determines if nextTag() method has been called. */
  private boolean hasNextTag;

  /** {@link Axis} for iteration. */
  private final Axis axis;

  /** Stack for reading end element. */
  private final Deque<Long> stack;

  /**
   * Determines if the cursor has to move back after empty elements or go up in the tree (used in
   * getElementText().
   */
  private boolean toLastKey;

  /**
   * Last emitted key (start tags, text... except end tags; used in getElementText()).
   */
  private long lastKey;

  /** Determines if {@link XmlNodeReadOnlyTrx} should be closed afterwards. */
  private final boolean closeRtx;

  /** First call. */
  private boolean isFirst;

  /** Determines if end document event should be emitted or not. */
  private boolean hasToEmitEndDocument;

  /**
   * Determines if the serializer must emit a new element in the next call to nextEvent.
   */
  private boolean hasNext;

  /** Right sibling key of start node. */
  private final long startRightSibling;

  /** Parent key of start node. */
  private final long startParent;

  /** Determines if {@code hasNext()} has been called. */
  private boolean calledHasNext;

  /**
   * Initialize XMLStreamReader implementation with transaction. The cursor points to the node the
   * XMLStreamReader starts to read. Do not serialize the tank ids.
   *
   * @param rtx {@link XmlNodeReadOnlyTrx} which is used to iterate over and generate StAX events
   */
  public StAXSerializer(final XmlNodeReadOnlyTrx rtx) {
    this(rtx, true);
  }

  /**
   * Initialize XMLStreamReader implementation with transaction. The cursor points to the node the
   * XMLStreamReader starts to read. Do not serialize the tank ids.
   *
   * @param rtx transaction which is used to iterate over and generate StAX events
   * @param hasToCloseRtx determines if the trensaction should be closed afterwards
   */
  public StAXSerializer(final XmlNodeReadOnlyTrx rtx, final boolean hasToCloseRtx) {
    hasNextTag = false;
    axis = new DescendantAxis(checkNotNull(rtx), IncludeSelf.YES);
    closeRtx = hasToCloseRtx;
    stack = new ArrayDeque<Long>();
    isFirst = true;
    hasToEmitEndDocument = true;
    hasNext = true;
    startParent = rtx.getParentKey();
    startRightSibling = rtx.getRightSiblingKey();
  }

  /**
   * Emit end tag.
   *
   * @param rtx Sirix reading transaction {@link XmlNodeReadOnlyTrx}
   */
  private void emitEndTag(final XmlNodeReadOnlyTrx rtx) {
    final long nodeKey = rtx.getNodeKey();
    final QNm qName = rtx.getName();
    event = eventFactory.createEndElement(new QName(qName.getNamespaceURI(), qName.getLocalName(), qName.getPrefix()),
        new NamespaceIterator(rtx));
    rtx.moveTo(nodeKey);
  }

  /**
   * Emit a node.
   *
   * @param rtx Sirix reading transaction {@link XmlNodeReadOnlyTrx}
   */
  private void emitNode(final XmlNodeReadOnlyTrx rtx) {
    switch (rtx.getKind()) {
      case XML_DOCUMENT:
        event = eventFactory.createStartDocument();
        break;
      case ELEMENT:
        final long key = rtx.getNodeKey();
        final QNm qName = rtx.getName();
        event = eventFactory.createStartElement(new QName(qName.getNamespaceURI(), qName.getLocalName(), qName.getPrefix()),
            new AttributeIterator(rtx), new NamespaceIterator(rtx));
        rtx.moveTo(key);
        break;
      case TEXT:
        event = eventFactory.createCharacters(XMLToken.escapeContent(rtx.getValue()));
        break;
      case COMMENT:
        event = eventFactory.createComment(XMLToken.escapeContent(rtx.getValue()));
        break;
      case PROCESSING_INSTRUCTION:
        event = eventFactory.createProcessingInstruction(rtx.getName().getLocalName(), rtx.getValue());
        break;
      // $CASES-OMITTED$
      default:
        throw new IllegalStateException("Kind not known!");
    }
  }

  @Override
  public void close() throws XMLStreamException {
    if (closeRtx) {
      axis.asXdmNodeReadTrx().close();
    }
  }

  @Override
  public String getElementText() throws XMLStreamException {
    final XmlNodeReadOnlyTrx rtx = axis.asXdmNodeReadTrx();
    final long nodeKey = rtx.getNodeKey();

    /*
     * The cursor has to move back (once) after determining, that a closing tag would be the next event
     * (precond: closeElement and either goBack or goUp is true).
     */
    if (hasToCloseElements && toLastKey) {
      rtx.moveTo(lastKey);
    }

    if (event.getEventType() != XMLStreamConstants.START_ELEMENT) {
      rtx.moveTo(nodeKey);
      throw new XMLStreamException("getElementText() only can be called on a start element");
    }
    final var textFilterAxis = new FilterAxis<>(new DescendantAxis(rtx), new TextFilter(rtx));
    final StringBuilder strBuilder = new StringBuilder();

    while (textFilterAxis.hasNext()) {
      textFilterAxis.next();
      strBuilder.append(rtx.getValue());
    }

    rtx.moveTo(nodeKey);
    return XMLToken.escapeContent(strBuilder.toString());
  }

  @Override
  public Object getProperty(final String name) throws IllegalArgumentException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasNext() {
    boolean retVal = false;

    if (!stack.isEmpty() && (hasToCloseElements || closeElementsEmitted)) {
      /*
       * mAxis.hasNext() can't be used in this case, because it would iterate to the next node but at
       * first all end-tags have to be emitted.
       */
      retVal = true;
    } else {
      retVal = axis.hasNext();
    }

    if (!retVal && hasToEmitEndDocument) {
      hasNext = false;
      retVal = !retVal;
    }

    return retVal;
  }

  @Override
  public XMLEvent nextEvent() throws XMLStreamException {
    if (!calledHasNext) {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
    }
    try {
      if (hasNext && !hasToCloseElements && !closeElementsEmitted) {
        key = axis.next();

        if (hasNextTag) {
          if (axis.asXdmNodeReadTrx().getKind() != NodeKind.ELEMENT) {
            throw new XMLStreamException("The next tag isn't a start- or end-tag!");
          }
          hasNextTag = false;
        }
      }
      if (!hasNext && hasToEmitEndDocument) {
        hasToEmitEndDocument = false;
        event = eventFactory.createEndDocument();
      } else {
        emit(axis.asXdmNodeReadTrx());
      }
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }

    isFirst = false;
    return event;
  }

  @Override
  public XMLEvent nextTag() throws XMLStreamException {
    hasNextTag = true;
    return nextEvent();
  }

  @Override
  public XMLEvent peek() throws XMLStreamException {
    final long currNodeKey = axis.asXdmNodeReadTrx().getNodeKey();
    final XmlNodeReadOnlyTrx rtx = axis.asXdmNodeReadTrx();

    if (!hasNext && hasToEmitEndDocument) {
      event = eventFactory.createEndDocument();
    } else if (!hasNext) {
      return null;
    } else {
      if (hasToCloseElements && !closeElementsEmitted && !stack.isEmpty()) {
        rtx.moveTo(stack.peek());
        emitEndTag(rtx);
      } else {
        if (isFirst && axis.isSelfIncluded() == IncludeSelf.YES) {
          emitNode(rtx);
        } else {
          if (rtx.hasFirstChild()) {
            rtx.moveToFirstChild();
            emitNode(rtx);
          } else if (rtx.hasRightSibling()) {
            if (rtx.getRightSiblingKey() == startRightSibling) {
              event = eventFactory.createEndDocument();
            } else {
              rtx.moveToRightSibling();
              final NodeKind nodeKind = rtx.getKind();
              processNode(nodeKind);
            }
          } else if (rtx.hasParent()) {
            if (rtx.getParentKey() == startParent) {
              event = eventFactory.createEndDocument();
            } else {
              rtx.moveToParent();
              emitEndTag(rtx);
            }
          }
        }
      }
    }

    rtx.moveTo(currNodeKey);
    isFirst = false;
    return event;
  }

  /**
   * Just calls {@link #nextEvent()}.
   *
   * @return next event
   */
  @Override
  public Object next() {
    try {
      event = nextEvent();
    } catch (final XMLStreamException e) {
      throw new IllegalStateException(e);
    }

    return event;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Not supported!");
  }

  /**
   * Determines if a node or an end element has to be emitted.
   *
   * @param nodeKind the node kind
   */
  private void processNode(final NodeKind nodeKind) {
    assert nodeKind != null;
    switch (nodeKind) {
      case ELEMENT:
        emitEndTag(axis.asXdmNodeReadTrx());
        break;
      case PROCESSING_INSTRUCTION:
      case COMMENT:
      case TEXT:
        emitNode(axis.asXdmNodeReadTrx());
        break;
      // $CASES-OMITTED$
      default:
        // Do nothing.
    }
  }

  /**
   * Move to node and emit it.
   *
   * @param rtx Read Transaction.
   * @throws IOException if any I/O error occurred
   */
  private void emit(final XmlNodeReadOnlyTrx rtx) throws IOException {
    assert rtx != null;
    // Emit pending end elements.
    if (hasToCloseElements) {
      if (!stack.isEmpty() && stack.peek() != rtx.getLeftSiblingKey()) {
        rtx.moveTo(stack.pop());
        emitEndTag(rtx);
        rtx.moveTo(key);
      } else if (!stack.isEmpty()) {
        rtx.moveTo(stack.pop());
        emitEndTag(rtx);
        rtx.moveTo(key);
        closeElementsEmitted = true;
        hasToCloseElements = false;
      }
    } else {
      closeElementsEmitted = false;

      // Emit node.
      emitNode(rtx);

      lastKey = rtx.getNodeKey();

      // Push end element to stack if we are a start element.
      if (rtx.getKind() == NodeKind.ELEMENT) {
        stack.push(lastKey);
      }

      // Remember to emit all pending end elements from stack if
      // required.
      if ((!rtx.hasFirstChild() && !rtx.hasRightSibling()) || (rtx.getKind() == NodeKind.ELEMENT && !rtx.hasFirstChild())) {
        moveToNextNode();
      }
    }
  }

  /**
   * Move to next node in tree either in case of a right sibling of an empty element or if no further
   * child and no right sibling can be found, so that the next node is in the following axis.
   */
  private void moveToNextNode() {
    toLastKey = true;
    if (axis.hasNext()) {
      key = axis.next();
    }
    hasToCloseElements = true;
  }

  /**
   * Implementation of an attribute-iterator.
   */
  private static final class AttributeIterator implements Iterator<Attribute> {

    /**
     * {@link XmlNodeReadOnlyTrx} implementation.
     */
    private final XmlNodeReadOnlyTrx mRtx;

    /** Number of attribute nodes. */
    private final int mAttCount;

    /** Index of attribute node. */
    private int mIndex;

    /** Node key. */
    private final long mNodeKey;

    /** Factory to create nodes {@link XMLEventFactory}. */
    private final XMLEventFactory mFac = XMLEventFactory.newFactory();

    /**
     * Constructor.
     *
     * @param rtx reference implementing the {@link XmlNodeReadOnlyTrx} interface
     */
    public AttributeIterator(final XmlNodeReadOnlyTrx rtx) {
      mRtx = checkNotNull(rtx);
      mNodeKey = mRtx.getNodeKey();
      mIndex = 0;

      if (mRtx.getKind() == NodeKind.ELEMENT) {
        mAttCount = mRtx.getAttributeCount();
      } else {
        mAttCount = 0;
      }
    }

    @Override
    public boolean hasNext() {
      boolean retVal = false;

      if (mIndex < mAttCount) {
        retVal = true;
      }

      return retVal;
    }

    @Override
    public Attribute next() {
      mRtx.moveTo(mNodeKey);
      mRtx.moveToAttribute(mIndex++);
      assert mRtx.getKind() == NodeKind.ATTRIBUTE;
      final QNm qName = mRtx.getName();
      final String value = XMLToken.escapeAttribute(mRtx.getValue());
      mRtx.moveTo(mNodeKey);
      return mFac.createAttribute(new QName(qName.getNamespaceURI(), qName.getLocalName(), qName.getPrefix()), value);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Not supported!");
    }
  }

  /**
   * Implementation of a namespace iterator.
   */
  private static final class NamespaceIterator implements Iterator<Namespace> {

    /**
     * Sirix {@link XmlNodeReadOnlyTrx}.
     */
    private final XmlNodeReadOnlyTrx mRtx;

    /** Number of namespace nodes. */
    private final int mNamespCount;

    /** Index of namespace node. */
    private int mIndex;

    /** Node key. */
    private final long mNodeKey;

    /** Factory to create nodes {@link XMLEventFactory}. */
    private final XMLEventFactory mFac = XMLEventFactory.newInstance();

    /**
     * Constructor.
     *
     * @param rtx reference implementing the {@link XmlNodeReadOnlyTrx} interface
     */
    public NamespaceIterator(final XmlNodeReadOnlyTrx rtx) {
      mRtx = checkNotNull(rtx);
      mNodeKey = mRtx.getNodeKey();
      mIndex = 0;

      if (mRtx.getKind() == NodeKind.ELEMENT) {
        mNamespCount = mRtx.getNamespaceCount();
      } else {
        mNamespCount = 0;
      }
    }

    @Override
    public boolean hasNext() {
      boolean retVal = false;

      if (mIndex < mNamespCount) {
        retVal = true;
      }

      return retVal;
    }

    @Override
    public Namespace next() {
      mRtx.moveTo(mNodeKey);
      mRtx.moveToNamespace(mIndex++);
      assert mRtx.getKind() == NodeKind.NAMESPACE;
      final QNm qName = mRtx.getName();
      mRtx.moveTo(mNodeKey);
      return mFac.createNamespace(qName.getPrefix(), qName.getNamespaceURI());
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Not supported!");
    }
  }
}
