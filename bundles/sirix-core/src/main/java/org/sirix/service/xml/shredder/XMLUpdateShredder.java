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

package org.sirix.service.xml.shredder;

import org.brackit.xquery.atomic.QNm;
import org.sirix.access.DatabaseConfiguration;
import org.sirix.access.Databases;
import org.sirix.access.ResourceConfiguration;
import org.sirix.api.xml.XmlNodeTrx;
import org.sirix.api.xml.XmlResourceManager;
import org.sirix.exception.SirixException;
import org.sirix.exception.SirixIOException;
import org.sirix.exception.SirixUsageException;
import org.sirix.node.NodeKind;
import org.sirix.service.ShredderCommit;
import org.sirix.settings.Fixed;
import org.sirix.utils.LogWrapper;
import org.sirix.utils.TypedValue;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;

/**
 * <h1>XMLUpdateShredder</h1>
 *
 * <p>
 * Shredder, which updates a sirix revision to the next revision, as thus it just inserts or deletes
 * nodes, which have been changed. Renames are treated as insert new node, delete old node.
 * </p>
 *
 * @author Johannes Lichtenberger, University of Konstanz
 *
 */
@Deprecated
public final class XMLUpdateShredder implements Callable<Long> {

  /** {@link LogWrapper} reference. */
  private static final LogWrapper LOGWRAPPER = new LogWrapper(LoggerFactory.getLogger(XMLUpdateShredder.class));

  /** File to parse. */
  protected transient File file;

  /** Events to parse. */
  protected transient Queue<XMLEvent> events;

  /** Node key. */
  private transient long nodeKey;

  /** Determines if a node is found in the sirix storage or not. */
  private transient boolean found;

  /** Determines if an end tag has been read while inserting nodes. */
  private transient boolean insertedEndTag;

  /** Determines if transaction has been moved to right sibling. */
  private transient boolean movedToRightSibling;

  /** Determines if node has been removed at the end of a subtree. */
  private transient boolean removedNode;

  /**
   * Determines if it's a right sibling from the currently parsed node, where the parsed node and the
   * node in the sirix storage match.
   */
  private transient boolean isRightSibling;

  /** Last node key. */
  private transient long lastNodeKey;

  /**
   * The key of the node, when the nodes are equal if at all (used to check right siblings and
   * therefore if nodes have been deleted).
   */
  private transient long keyMatches;

  /** Maximum node key in revision. */
  private transient long maxNodeKey;

  /** Determines if changes should be commited. */
  private transient ShredderCommit commitAfterwards;

  /** Level where the parser is in the file to shredder. */
  private transient int levelInToShredder;

  /** {@link QName} of root node from which to shredder the subtree. */
  private transient QName rootElement;

  // /** Determines if it's the last node or not. */
  // private transient boolean mIsLastNode;

  /** Determines where an insert in the tree occurs. */
  private enum InternalInsert {
    /** Insert right after a start tag is parsed. */
    ATTOP,

    /** Insert at the start or at the middle of a subtree. */
    ATMIDDLEBOTTOM,

    /** Inserts have been made right before. */
    INTERMEDIATE,

    /** No insert occured. */
    NOINSERT
  }

  /** Determines where a delete in the tree occurs. */
  private transient InternalInsert internalInsert;

  /** Determines where a delete in the tree occurs. */
  private enum EDelete {
    /** Delete at the start or at the middle of a subtree. */
    ATSTARTMIDDLE,

    /** Delete right before an end tag is parsed. */
    ATBOTTOM,

    /** No delete occured. */
    NODELETE
  }

  /** Determines where a delete in the tree occured. */
  private transient EDelete delete;

  /** Determines how to add a new node. */
  private enum EAdd {
    /** Add as first child. */
    ASFIRSTCHILD,

    /** Add as right sibling. */
    ASRIGHTSIBLING
  }

  /** Determines if a node has been inserted into sirix. */
  private transient boolean inserted;

  /**
   * Determines if it's an empty element before an insert at the top of a subtree.
   */
  private transient boolean emptyElement;

  private final XmlNodeTrx wtx;

  private final XMLEventReader reader;

  private InsertPosition insertPosition;

  /**
   * Normal constructor to invoke a shredding process on a existing {@link XmlNodeTrx}.
   *
   * @param wtx {@link XmlNodeTrx} where the new XML Fragment should be placed
   * @param xmlEventReader {@link XMLEventReader} (StAX parser) of the XML Fragment
   * @param addAsFirstChild if the insert is occuring on a node in an existing tree.
   *        <code>false</code> is not possible when wtx is on root node
   * @param data the data the update shredder operates on. Either a {@link List} of
   *        {@link XMLEvent}s or a {@link File}
   * @param commitAfterwards determines if changes should be commited
   * @throws SirixUsageException if insertasfirstChild && updateOnly is both true OR if wtx is not
   *         pointing to doc-root and updateOnly= true
   * @throws SirixIOException if sirix cannot access node keys
   *
   */
  @SuppressWarnings("unchecked")
  public XMLUpdateShredder(final XmlNodeTrx wtx, final XMLEventReader xmlEventReader,
      final InsertPosition addAsFirstChild, final Object data, final ShredderCommit commitAfterwards)
      throws SirixIOException {
    insertPosition = addAsFirstChild;
    this.wtx = wtx;
    reader = xmlEventReader;
    if (data == null || commitAfterwards == null) {
      throw new IllegalArgumentException("None of the constructor parameters may be null!");
    }
    maxNodeKey = this.wtx.getMaxNodeKey();
    this.commitAfterwards = commitAfterwards;

    if (data instanceof File) {
      file = (File) data;
    } else if (data instanceof List<?>) {
      events = (ArrayDeque<XMLEvent>) data;
    }
  }

  /**
   * Invoking the shredder.
   *
   * @throws SirixException if sirix encounters something went wrong
   * @return revision of last revision (before commit)
   */
  @Override
  public Long call() throws SirixException {
    final long revision = wtx.getRevisionNumber();
    updateOnly();
    if (commitAfterwards == ShredderCommit.COMMIT) {
      wtx.commit();
    }
    return revision;
  }

  /**
   * Update a shreddered file.
   *
   * @throws SirixException if sirix encounters something went wrong
   */
  private void updateOnly() throws SirixException {
    try {
      // Initialize variables.
      levelInToShredder = 0;
      // mIsLastNode = false;
      movedToRightSibling = false;
      boolean firstEvent = true;

      // If structure already exists, make a sync against the current structure.
      if (maxNodeKey == 0) {
        // If no content is in the XML, a normal insertNewContent is executed.
        new XmlShredder.Builder(wtx, reader, insertPosition).build().call();
      } else {
        if (wtx.getKind() == NodeKind.XML_DOCUMENT) {
          // Find the start key for the update operation.
          long startkey = Fixed.DOCUMENT_NODE_KEY.getStandardProperty() + 1;
          while (!wtx.moveTo(startkey).hasMoved()) {
            startkey++;
          }
        }

        XMLEvent event = null;
        StringBuilder sBuilder = new StringBuilder();
        final XMLEventFactory fac = XMLEventFactory.newInstance();

        // Iterate over all nodes.
        while (reader.hasNext()) {
          // Parsing the next event.
          if (delete == EDelete.ATSTARTMIDDLE) {
            /*
             * Do not move StAX parser forward if nodes have been deleted at the start or in the middle of a
             * subtree.
             */
            delete = EDelete.NODELETE;
          } else {
            // After an insert or after nodes were equal.
            event = reader.nextEvent();
            if (event.isCharacters() && event.asCharacters().isWhiteSpace()) {
              continue;
            }
            assert event != null;
            if (firstEvent) {
              // Setup start element from StAX parser.
              firstEvent = false;

              if (event.getEventType() == XMLStreamConstants.START_DOCUMENT) {
                while (reader.hasNext() && event.getEventType() != XMLStreamConstants.START_ELEMENT) {
                  event = reader.nextEvent();
                }
                assert event.getEventType() == XMLStreamConstants.START_ELEMENT;
              }
              if (event.getEventType() != XMLStreamConstants.START_ELEMENT) {
                throw new IllegalStateException("StAX parser has to be on START_DOCUMENT or START_ELEMENT event!");
              }

              // Get root element of subtree or whole XML document to shredder.
              rootElement = event.asStartElement().getName();
            } else if (event != null && event.isEndElement() && rootElement.equals(event.asEndElement().getName())
                && levelInToShredder == 1) {
              // End with shredding if end_elem equals root-elem.
              break;
            }
          }

          assert event != null;

          switch (event.getEventType()) {
            case XMLStreamConstants.START_ELEMENT:
              processStartTag(event.asStartElement());
              break;
            case XMLStreamConstants.CHARACTERS:
              sBuilder.append(event.asCharacters().getData());
              while (reader.peek().getEventType() == XMLStreamConstants.CHARACTERS) {
                sBuilder.append(reader.nextEvent().asCharacters().getData());
              }
              final Characters text = fac.createCharacters(sBuilder.toString().trim());
              processCharacters(text);
              sBuilder = new StringBuilder();
              break;
            case XMLStreamConstants.END_ELEMENT:
              processEndTag();
              break;
            default:
              // Other nodes which are currently not supported by sirix.
          }
        }

        // if (!mIsLastNode) {
        // if (mInserted) {
        // // Remove next node after node, which was inserted, because it must
        // have been deleted.
        // if (mWtx.moveToRightSibling()) {
        // mWtx.remove();
        // }
        // } else {
        // // Remove current node (cursor has been moved to the next node
        // already).
        // mWtx.remove();
        // }
        //
        // // Also remove any siblings.
        // boolean hasRightSibling = false;
        // while (mWtx.getStructuralNode().hasRightSibling()) {
        // hasRightSibling = true;
        // mWtx.remove();
        // }
        // if (hasRightSibling) {
        // mWtx.remove();
        // }
        // }

        reader.close();
      }
      // TODO: use Java7 multi-catch feature.
    } catch (final XMLStreamException e) {
      throw new SirixIOException(e);
    } catch (final IOException e) {
      throw new SirixIOException(e);
    }

  }

  /**
   * Process start tag.
   *
   * @param paramElem {@link StartElement} currently parsed.
   * @throws XMLStreamException In case of any StAX parsing error.
   * @throws IOException In case of any I/O error.
   * @throws SirixException In case of any sirix error.
   */
  private void processStartTag(final StartElement paramElem) throws IOException, XMLStreamException, SirixException {
    assert paramElem != null;

    // Initialize variables.
    initializeVars();

    // Main algorithm to determine if same, insert or a delete has to be made.
    algorithm(paramElem);

    if (found && isRightSibling) {
      delete = EDelete.ATSTARTMIDDLE;
      deleteNode();
    } else if (!found) {
      // Increment levels.
      levelInToShredder++;
      insertElementNode(paramElem);
    } else if (found) {
      // Increment levels.
      levelInToShredder++;
      sameElementNode();
    }
  }

  /**
   * Process characters.
   *
   * @param paramText {@link Characters} currently parsed.
   * @throws XMLStreamException In case of any StAX parsing error.
   * @throws IOException In case of any I/O error.
   * @throws SirixException In case of any sirix error.
   */
  private void processCharacters(final Characters paramText) throws IOException, XMLStreamException, SirixException {
    assert paramText != null;
    // Initialize variables.
    initializeVars();
    final String text = paramText.getData().toString();
    if (!text.isEmpty()) {
      // Main algorithm to determine if same, insert or a delete has to be made.
      algorithm(paramText);

      if (found && isRightSibling) {
        /*
         * Cannot happen because if text node after end tag get's deleted it's done already while parsing
         * the end tag. If text node should be deleted at the top of a subtree (right after a start tag has
         * been parsed) it's done in processStartTag(StartElement).
         */
        // mDelete = EDelete.ATSTARTMIDDLE;
        // deleteNode();
        throw new AssertionError("");
      } else if (!found) {
        insertTextNode(paramText);
      } else if (found) {
        sameTextNode();
      }
    }
  }

  /**
   * Process end tag.
   *
   * @throws XMLStreamException In case of any parsing error.
   * @throws SirixException In case anything went wrong while moving/deleting nodes in sirix.
   */
  private void processEndTag() throws XMLStreamException, SirixException {
    levelInToShredder--;

    if (inserted) {
      insertedEndTag = true;
    }

    if (removedNode) {
      removedNode = false;
    } else {
      // Move cursor to parent.
      if (wtx.getNodeKey() == lastNodeKey) {
        /*
         * An end tag must have been parsed immediately before and it must have been an empty element at the
         * end of a subtree, thus move this time to parent node.
         */
        assert wtx.hasParent() && wtx.getKind() == NodeKind.ELEMENT;
        wtx.moveToParent();
      } else {
        if (wtx.getKind() == NodeKind.ELEMENT) {
          if (wtx.hasFirstChild() && wtx.hasParent()) {
            // It's not an empty element, thus move to parent.
            wtx.moveToParent();
          }
          // } else {
          // checkIfLastNode(true);
          // }
        } else if (wtx.hasParent()) {
          if (wtx.hasRightSibling()) {
            wtx.moveToRightSibling();
            /*
             * Means next event is an end tag in StAX reader, but something different where the sirix
             * transaction points to, which also means it has to be deleted.
             */
            keyMatches = -1;
            delete = EDelete.ATBOTTOM;
            deleteNode();
          }
          wtx.moveToParent();
        }

      }

      lastNodeKey = wtx.getNodeKey();

      // Move cursor to right sibling if it has one.
      if (wtx.hasRightSibling()) {
        wtx.moveToRightSibling();
        movedToRightSibling = true;

        skipWhitespaces(reader);
        if (reader.peek().getEventType() == XMLStreamConstants.END_ELEMENT) {
          /*
           * Means next event is an end tag in StAX reader, but something different where the sirix
           * transaction points to, which also means it has to be deleted.
           */
          keyMatches = -1;
          delete = EDelete.ATBOTTOM;
          deleteNode();
        }
      } else {
        movedToRightSibling = false;
      }
    }
  }

  /**
   * Main algorithm to determine if nodes are equal, have to be inserted, or have to be removed.
   *
   * @param paramEvent The currently parsed StAX event.
   * @throws IOException In case the open operation fails (delegated from checkDescendants(...)).
   * @throws XMLStreamException In case any StAX parser problem occurs.
   */
  private void algorithm(final XMLEvent paramEvent) throws IOException, XMLStreamException {
    assert paramEvent != null;
    do {
      /*
       * Check if a node in the shreddered file on the same level equals the current element node.
       */
      if (paramEvent.isStartElement()) {
        found = checkElement(paramEvent.asStartElement());
      } else if (paramEvent.isCharacters()) {
        found = checkText(paramEvent.asCharacters());
      }
      if (wtx.getNodeKey() != nodeKey) {
        isRightSibling = true;
      }

      keyMatches = wtx.getNodeKey();
      //
      // if (mFound && mIsRightSibling) {
      // /*
      // * Root element of next subtree in shreddered file matches
      // * so check all descendants. If they match the node must be
      // * inserted.
      // */
      // switch (paramEvent.getEventType()) {
      // case XMLStreamConstants.START_ELEMENT:
      // mMoved = EMoved.FIRSTNODE;
      // //mFound = checkDescendants(paramEvent.asStartElement());
      // mFound = checkDescendants(paramEvent.asStartElement());
      // break;
      // case XMLStreamConstants.CHARACTERS:
      // mFound = checkText(paramEvent.asCharacters());
      // break;
      // default:
      // // throw new AssertionError("Node type not known or not implemented!");
      // }
      // mWtx.moveTo(mKeyMatches);
      // }
    } while (!found && wtx.moveToRightSibling().hasMoved());
    wtx.moveTo(nodeKey);
  }

  /**
   * Check if text event and text in sirix storage match.
   *
   * @param paramEvent {@link XMLEvent}.
   * @return true if they match, otherwise false.
   */
  private boolean checkText(final Characters paramEvent) {
    assert paramEvent != null;
    final String text = paramEvent.getData().trim();
    return wtx.getKind() == NodeKind.TEXT && wtx.getValue().equals(text);
  }

  /**
   * In case they are the same nodes move cursor to next node and update stack.
   *
   * @throws SirixIOException In case of any sirix error.
   * @throws XMLStreamException In case of any StAX parsing error.
   */
  private void sameTextNode() throws SirixIOException, XMLStreamException {
    // Update variables.
    internalInsert = InternalInsert.NOINSERT;
    delete = EDelete.NODELETE;
    inserted = false;
    insertedEndTag = false;
    removedNode = false;

    // Check if last node reached.
    // checkIfLastNode(false);

    // Skip whitespace events.
    skipWhitespaces(reader);

    // Move to right sibling if next node isn't an end tag.
    if (reader.peek().getEventType() != XMLStreamConstants.END_ELEMENT) {
      // // Check if next node matches or not.
      // boolean found = false;
      // if (mReader.peek().getEventType() == XMLStreamConstants.START_ELEMENT)
      // {
      // found = checkElement(mReader.peek().asStartElement());
      // } else if (mReader.peek().getEventType() ==
      // XMLStreamConstants.CHARACTERS) {
      // found = checkText(mReader.peek().asCharacters());
      // }
      //
      // // If next node doesn't match/isn't the same move on.
      // if (!found) {
      if (wtx.moveToRightSibling().hasMoved()) {
        movedToRightSibling = true;
      } else {
        movedToRightSibling = false;
      }
      // }
    }

    internalInsert = InternalInsert.ATMIDDLEBOTTOM;
  }

  // /**
  // * Check if it's the last node in the shreddered file and modify flag
  // mIsLastNode
  // * if it is the last node.
  // *
  // * @param paramDeleted
  // * Determines if method is invoked inside deleteNode()
  // */
  // private void checkIfLastNode(final boolean paramDeleted) {
  // // Last node or not?
  // int level = mLevelInShreddered;
  //
  // if (paramDeleted && level == 1 && mWtx.getItem().getKind() ==
  // ENodes.ELEMENT_KIND
  // && mWtx.getQNameOfCurrentNode().equals(mRootElem) && level == 1) {
  // mIsLastNode = true;
  // }
  //
  // if (!mIsLastNode) {
  // if (paramDeleted && level == 1) {
  // level++;
  // }
  // if (level > 0) {
  // final long nodeKey = mWtx.getItem().getKey();
  // while (!mWtx.getStructuralNode().hasRightSibling() && level != 0) {
  // mWtx.moveToParent();
  // level--;
  // if (mWtx.getItem().getKind() == ENodes.ELEMENT_KIND
  // && mWtx.getQNameOfCurrentNode().equals(mRootElem) && level == 1) {
  // mIsLastNode = true;
  // break;
  // }
  // }
  // mWtx.moveTo(nodeKey);
  // }
  // }
  // }

  /**
   * Nodes match, thus update stack and move cursor to first child if it is not a leaf node.
   *
   * @throws XMLStreamException In case of any StAX parsing error.
   * @throws SirixException In case anything went wrong while moving the sirix transaction.
   */
  private void sameElementNode() throws XMLStreamException, SirixException {
    // Update variables.
    internalInsert = InternalInsert.NOINSERT;
    delete = EDelete.NODELETE;
    inserted = false;
    insertedEndTag = false;
    removedNode = false;

    // Check if last node reached.
    // checkIfLastNode(false);

    // Skip whitespace events.
    skipWhitespaces(reader);

    // Move transaction.
    if (wtx.hasFirstChild()) {
      /*
       * If next event needs to be inserted, it has to be inserted at the top of the subtree, as first
       * child.
       */
      internalInsert = InternalInsert.ATTOP;
      wtx.moveToFirstChild();

      if (reader.peek().getEventType() == XMLStreamConstants.END_ELEMENT) {
        /*
         * Next event is an end tag, so the current child element, where the transaction currently is
         * located needs to be removed.
         */
        keyMatches = -1;
        delete = EDelete.ATBOTTOM;
        deleteNode();
      }
      // } else if (mReader.peek().getEventType() ==
      // XMLStreamConstants.END_ELEMENT
      // &&
      // !mReader.peek().asEndElement().getName().equals(mWtx.getQNameOfCurrentNode()))
      // {
      // /*
      // * Node must be removed when next end tag doesn't match the current name
      // and it has no children.
      // */
      // mKeyMatches = -1;
      // mDelete = EDelete.ATBOTTOM;
      // deleteNode();
    } else if (reader.peek().getEventType() != XMLStreamConstants.END_ELEMENT) {
      /*
       * sirix transaction can't find a child node, but StAX parser finds one, so it must be inserted as a
       * first child of the current node.
       */
      internalInsert = InternalInsert.ATTOP;
      emptyElement = true;
    } else {
      internalInsert = InternalInsert.ATMIDDLEBOTTOM;
    }
  }

  /**
   * Skip any whitespace event.
   *
   * @param paramReader the StAX {@link XMLEventReader} to use
   * @throws XMLStreamException if any parsing error occurs while moving the StAX parser
   */
  private void skipWhitespaces(final XMLEventReader paramReader) throws XMLStreamException {
    while (paramReader.peek().getEventType() == XMLStreamConstants.CHARACTERS
        && paramReader.peek().asCharacters().isWhiteSpace()) {
      paramReader.nextEvent();
    }
  }

  /**
   * Insert an element node.
   *
   * @param paramElement {@link StartElement}, which is going to be inserted.
   * @throws SirixException In case any exception occurs while moving the cursor or deleting nodes in
   *         sirix.
   * @throws XMLStreamException In case of any StAX parsing error.
   */
  private void insertElementNode(final StartElement paramElement) throws SirixException, XMLStreamException {
    assert paramElement != null;
    /*
     * Add node if it's either not found among right siblings (and the cursor on the shreddered file is
     * on a right sibling) or if it's not found in the structure and it is a new last right sibling.
     */
    delete = EDelete.NODELETE;
    removedNode = false;

    switch (internalInsert) {
      case ATTOP:
        // We are at the top of a subtree, no end tag has been parsed before.
        if (!emptyElement) {
          // Has to be inserted on the parent node.
          wtx.moveToParent();
        }

        // Insert element as first child.
        addNewElement(EAdd.ASFIRSTCHILD, paramElement);
        internalInsert = InternalInsert.INTERMEDIATE;
        break;
      case INTERMEDIATE:
        // Inserts have been made before.
        EAdd insertNode = EAdd.ASFIRSTCHILD;

        if (insertedEndTag) {
          /*
           * An end tag has been read while inserting, thus insert node as right sibling of parent node.
           */
          insertedEndTag = false;
          insertNode = EAdd.ASRIGHTSIBLING;
        }

        // Possibly move one sibling back if transaction already moved to next
        // node.
        if (movedToRightSibling) {
          wtx.moveToLeftSibling();
        }

        // Make sure if transaction is on a text node the node is inserted as a
        // right sibling.
        if (wtx.getKind() == NodeKind.TEXT) {
          insertNode = EAdd.ASRIGHTSIBLING;
        }

        addNewElement(insertNode, paramElement);
        break;
      case ATMIDDLEBOTTOM:
        // Insert occurs at the middle or end of a subtree.

        // Move one sibling back.
        if (movedToRightSibling) {
          movedToRightSibling = false;
          wtx.moveToLeftSibling();
        }

        // Insert element as right sibling.
        addNewElement(EAdd.ASRIGHTSIBLING, paramElement);
        internalInsert = InternalInsert.INTERMEDIATE;
        break;
      default:
        throw new AssertionError("Enum value not known!");
    }

    inserted = true;
  }

  /**
   * Insert a text node.
   *
   * @param paramText {@link Characters}, which is going to be inserted.
   * @throws SirixException In case any exception occurs while moving the cursor or deleting nodes in
   *         sirix.
   * @throws XMLStreamException In case of any StAX parsing error.
   */
  private void insertTextNode(final Characters paramText) throws SirixException, XMLStreamException {
    assert paramText != null;
    /*
     * Add node if it's either not found among right siblings (and the cursor on the shreddered file is
     * on a right sibling) or if it's not found in the structure and it is a new last right sibling.
     */
    delete = EDelete.NODELETE;
    removedNode = false;

    switch (internalInsert) {
      case ATTOP:
        // Insert occurs at the top of a subtree (no end tag has been parsed
        // immediately before).

        // Move to parent.
        wtx.moveToParent();

        // Insert as first child.
        addNewText(EAdd.ASFIRSTCHILD, paramText);

        // Move to next node if no end tag follows (thus cursor isn't moved to
        // parent in processEndTag()).
        if (reader.peek().getEventType() != XMLStreamConstants.END_ELEMENT) {
          if (wtx.moveToRightSibling().hasMoved()) {
            movedToRightSibling = true;
          } else {
            movedToRightSibling = false;
          }
        } else if (wtx.hasRightSibling()) {
          movedToRightSibling = false;
          inserted = true;
          keyMatches = -1;
          delete = EDelete.ATBOTTOM;
          deleteNode();
        }
        internalInsert = InternalInsert.INTERMEDIATE;
        break;
      case INTERMEDIATE:
        // Inserts have been made before.

        EAdd addNode = EAdd.ASFIRSTCHILD;

        if (insertedEndTag) {
          /*
           * An end tag has been read while inserting, so move back to left sibling if there is one and insert
           * as right sibling.
           */
          if (movedToRightSibling) {
            wtx.moveToLeftSibling();
          }
          addNode = EAdd.ASRIGHTSIBLING;
          insertedEndTag = false;
        }

        // Insert element as right sibling.
        addNewText(addNode, paramText);

        // Move to next node if no end tag follows (thus cursor isn't moved to
        // parent in processEndTag()).
        if (reader.peek().getEventType() != XMLStreamConstants.END_ELEMENT) {
          if (wtx.moveToRightSibling().hasMoved()) {
            movedToRightSibling = true;
          } else {
            movedToRightSibling = false;
          }
        }
        break;
      case ATMIDDLEBOTTOM:
        // Insert occurs in the middle or end of a subtree.

        // Move one sibling back.
        if (movedToRightSibling) {
          wtx.moveToLeftSibling();
        }

        // Insert element as right sibling.
        addNewText(EAdd.ASRIGHTSIBLING, paramText);

        // Move to next node.
        wtx.moveToRightSibling();

        internalInsert = InternalInsert.INTERMEDIATE;
        break;
      default:
        throw new AssertionError("Enum value not known!");
    }

    inserted = true;
  }

  /**
   * Delete node.
   *
   * @throws SirixException In case any exception occurs while moving the cursor or deleting nodes in
   *         sirix.
   */
  private void deleteNode() throws SirixException {
    /*
     * If found in one of the rightsiblings in the current shreddered structure remove all nodes until
     * the transaction points to the found node (keyMatches).
     */
    if (inserted && !movedToRightSibling) {
      inserted = false;
      if (wtx.hasRightSibling()) {
        // Cursor is on the inserted node, so move to right sibling.
        wtx.moveToRightSibling();
      }
    }

    // // Check if transaction is on the last node in the shreddered file.
    // checkIfLastNode(true);

    // Determines if transaction has moved to the parent node after a delete
    // operation.
    boolean movedToParent = false;

    // Determines if ldeleteNodeast node in a subtree is going to be deleted.
    boolean isLast = false;

    do {
      if (wtx.getNodeKey() != keyMatches) {
        if (!wtx.hasRightSibling() && !wtx.hasLeftSibling()) {
          if (delete == EDelete.ATSTARTMIDDLE) {
          }
          /*
           * Node has no right and no left sibling, so the transaction moves to the parent after the delete.
           */
          movedToParent = true;
        } else if (!wtx.hasRightSibling()) {
          // Last node has been reached, which means that the transaction moves
          // to the left sibling.
          isLast = true;
        }

        wtx.remove();
      }
    } while (wtx.getNodeKey() != keyMatches && !movedToParent && !isLast);

    if (movedToParent) {
      if (delete == EDelete.ATBOTTOM) {
        /*
         * Deleted right before an end tag has been parsed, thus don't move transaction to next node in
         * processEndTag().
         */
        removedNode = true;
      }
      /*
       * sirix transaction has been moved to parent, because all child nodes have been deleted, thus to
       * right sibling.
       */
      wtx.moveToRightSibling();
    } else {
      if (wtx.hasFirstChild()) {
        if (delete == EDelete.ATBOTTOM && isLast) {
          /*
           * Deleted right before an end tag has been parsed, thus don't move transaction to next node in
           * processEndTag().
           */
          removedNode = true;
        }

        if (isLast) {
          // If last node of a subtree has been removed, move to parent and
          // right sibling.
          wtx.moveToParent();
          wtx.moveToRightSibling();

          // If the delete occurs right before an end tag the level hasn't been
          // incremented.
          if (delete == EDelete.ATSTARTMIDDLE) {
          }
        }
      }
    }

    // Check if transaction is on the last node in the shreddered file.
    // checkIfLastNode(true);
    internalInsert = InternalInsert.NOINSERT;
  }

  /**
   * Initialize variables needed for the main algorithm.
   */
  private void initializeVars() {
    nodeKey = wtx.getNodeKey();
    found = false;
    isRightSibling = false;
    keyMatches = -1;
  }

  /**
   * Add a new text node.
   *
   * @param paramAdd determines how to add the node
   * @param paramTextEvent the current {@link Character} event from the StAX parser.
   * @throws SirixException if adding text node fails
   */
  private void addNewText(final EAdd paramAdd, final Characters paramTextEvent) throws SirixException {
    assert paramTextEvent != null;
    final String text = paramTextEvent.getData().trim();
    final ByteBuffer textByteBuffer = ByteBuffer.wrap(TypedValue.getBytes(text));
    if (textByteBuffer.array().length > 0) {
      if (paramAdd == EAdd.ASFIRSTCHILD) {
        wtx.insertTextAsFirstChild(new String(textByteBuffer.array()));
      } else {
        wtx.insertTextAsRightSibling(new String(textByteBuffer.array()));
      }
    }
  }

  /**
   * Add a new element node.
   *
   * @param paramAdd determines wether node is added as first child or right sibling
   * @param paramStartElement the current {@link StartElement}
   * @throws SirixException if inserting node fails
   */
  private void addNewElement(final EAdd paramAdd, final StartElement paramStartElement) throws SirixException {
    assert paramStartElement != null;
    final QName name = paramStartElement.getName();
    final QNm qName = new QNm(name.getNamespaceURI(), name.getPrefix(), name.getLocalPart());
    long key;

    if (insertPosition == InsertPosition.AS_RIGHT_SIBLING) {
      key = wtx.insertElementAsRightSibling(qName).getNodeKey();
    } else {
      if (paramAdd == EAdd.ASFIRSTCHILD) {
        key = wtx.insertElementAsFirstChild(qName).getNodeKey();
      } else {
        key = wtx.insertElementAsRightSibling(qName).getNodeKey();
      }
    }

    // Parse namespaces.
    for (final Iterator<?> it = paramStartElement.getNamespaces(); it.hasNext();) {
      final Namespace namespace = (Namespace) it.next();
      wtx.insertNamespace(new QNm(namespace.getNamespaceURI(), namespace.getPrefix(), ""));
      wtx.moveTo(key);
    }

    // Parse attributes.
    for (final Iterator<?> it = paramStartElement.getAttributes(); it.hasNext();) {
      final Attribute attribute = (Attribute) it.next();
      final QName attName = attribute.getName();
      wtx.insertAttribute(new QNm(attName.getNamespaceURI(), attName.getPrefix(), attName.getLocalPart()),
          attribute.getValue());
      wtx.moveTo(key);
    }
  }

  /**
   * Check if current element matches the element in the shreddered file.
   *
   * @param mEvent StartElement event, from the XML file to shredder.
   * @return true if they are equal, false otherwise.
   */
  private boolean checkElement(final StartElement mEvent) {
    assert mEvent != null;
    boolean retVal = false;

    // Matching element names?
    final QName name = mEvent.getName();
    final QNm currName = wtx.getName();
    if (wtx.getKind() == NodeKind.ELEMENT && currName.getNamespaceURI().equals(name.getNamespaceURI())
        && currName.getLocalName().equals(name.getLocalPart())) {
      // Check if atts and namespaces are the same.
      final long nodeKey = wtx.getNodeKey();

      // Check attributes.
      boolean foundAtts = false;
      boolean hasAtts = false;
      for (final Iterator<?> it = mEvent.getAttributes(); it.hasNext();) {
        hasAtts = true;
        final Attribute attribute = (Attribute) it.next();
        for (int i = 0, attCount = wtx.getAttributeCount(); i < attCount; i++) {
          wtx.moveToAttribute(i);
          final QName attName = attribute.getName();
          final QNm currAttName = wtx.getName();
          if (attName.getNamespaceURI().equals(currAttName.getNamespaceURI())
              && attName.getLocalPart().equals(currAttName.getLocalName())
              && attribute.getValue().equals(wtx.getValue())) {
            foundAtts = true;
            wtx.moveTo(nodeKey);
            break;
          }
          wtx.moveTo(nodeKey);
        }

        if (!foundAtts) {
          break;
        }
      }
      if (!hasAtts && wtx.getAttributeCount() == 0) {
        foundAtts = true;
      }

      // Check namespaces.
      boolean foundNamesps = false;
      boolean hasNamesps = false;
      for (final Iterator<?> namespIt = mEvent.getNamespaces(); namespIt.hasNext();) {
        hasNamesps = true;
        final Namespace namespace = (Namespace) namespIt.next();
        for (int i = 0, namespCount = wtx.getNamespaceCount(); i < namespCount; i++) {
          wtx.moveToNamespace(i);
          if (namespace.getNamespaceURI().equals(wtx.nameForKey(wtx.getURIKey()))) {
            final String prefix = namespace.getPrefix();
            if (prefix.isEmpty()) {
              foundNamesps = true;
              wtx.moveTo(nodeKey);
              break;
            } else if (prefix.equals(wtx.nameForKey(wtx.getPrefixKey()))) {
              foundNamesps = true;
              wtx.moveTo(nodeKey);
              break;
            }
          }
          wtx.moveTo(nodeKey);
        }

        if (!foundNamesps) {
          break;
        }
      }
      if (!hasNamesps && wtx.getNamespaceCount() == 0) {
        foundNamesps = true;
      }

      // Check if atts and namespaces are the same.
      if (foundAtts && foundNamesps) {
        retVal = true;
      } else {
        retVal = false;
      }
    }

    return retVal;
  }

  /**
   * Main method.
   *
   * @param args input and output files
   */
  public static void main(final String[] args) {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: XMLShredder input.xml output.tnk");
    }

    LOGWRAPPER.info("Shredding '" + args[0] + "' to '" + args[1] + "' ... ");
    final long time = System.currentTimeMillis();
    final Path target = Paths.get(args[1]);

    final DatabaseConfiguration config = new DatabaseConfiguration(target);
    Databases.createXmlDatabase(config);
    final var db = Databases.openXmlDatabase(target);
    db.createResource(new ResourceConfiguration.Builder("shredded").build());
    try (final XmlResourceManager resMgr = db.openResourceManager("shredded");
        final XmlNodeTrx wtx = resMgr.beginNodeTrx();
        final FileInputStream fis = new FileInputStream(Paths.get(args[0]).toFile())) {
      final XMLEventReader reader = XmlShredder.createFileReader(fis);
      final XMLUpdateShredder shredder =
          new XMLUpdateShredder(wtx, reader, InsertPosition.AS_FIRST_CHILD, new File(args[0]), ShredderCommit.COMMIT);
      shredder.call();
    } catch (final SirixException | IOException e) {
      LOGWRAPPER.error(e.getMessage(), e);
    }

    LOGWRAPPER.info(" done [" + (System.currentTimeMillis() - time) + "ms].");
  }
}
