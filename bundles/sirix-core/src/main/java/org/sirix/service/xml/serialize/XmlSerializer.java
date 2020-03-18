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

import org.brackit.xquery.util.serialize.Serializer;
import org.sirix.access.DatabaseConfiguration;
import org.sirix.access.Databases;
import org.sirix.access.ResourceConfiguration;
import org.sirix.api.ResourceManager;
import org.sirix.api.xml.XmlNodeReadOnlyTrx;
import org.sirix.api.xml.XmlNodeTrx;
import org.sirix.api.xml.XmlResourceManager;
import org.sirix.node.NodeKind;
import org.sirix.settings.CharsForSerializing;
import org.sirix.settings.Constants;
import org.sirix.utils.LogWrapper;
import org.sirix.utils.SirixFiles;
import org.sirix.utils.XMLToken;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sirix.service.xml.serialize.XmlSerializerProperties.*;

/**
 * Most efficient way to serialize a subtree into an OutputStream. The encoding always is UTF-8.
 * Note that the OutputStream internally is wrapped by a BufferedOutputStream. There is no need to
 * buffer it again outside of this class.
 */
public final class XmlSerializer extends org.sirix.service.AbstractSerializer<XmlNodeReadOnlyTrx, XmlNodeTrx> {

  /** {@link LogWrapper} reference. */
  private static final LogWrapper LOGWRAPPER = new LogWrapper(LoggerFactory.getLogger(XmlSerializer.class));

  /** Offset that must be added to digit to make it ASCII. */
  private static final int ASCII_OFFSET = 48;

  /** Precalculated powers of each available long digit. */
  private static final long[] LONG_POWERS = {1L, 10L, 100L, 1000L, 10000L, 100000L, 1000000L, 10000000L, 100000000L,
      1000000000L, 10000000000L, 100000000000L, 1000000000000L, 10000000000000L, 100000000000000L, 1000000000000000L,
      10000000000000000L, 100000000000000000L, 1000000000000000000L};

  /** OutputStream to write to. */
  private final OutputStream out;

  /** Indent output. */
  private final boolean doIndent;

  /** Serialize XML declaration. */
  private final boolean doSerializeXMLDeclaration;

  /** Serialize rest header and closer and rest:id. */
  private final boolean doSerializeRest;

  /** Serialize a rest-sequence element for the start-document. */
  private final boolean doSerializeRestSequence;

  /** Serialize id. */
  private final boolean doSerializeId;

  /** Number of spaces to indent. */
  private final int indentSpaces;

  /** Determines if serializing with initial indentation. */
  private final boolean withInitialIndent;

  private final boolean emitXQueryResultSequence;

  private final boolean doSerializeTimestamp;

  private final boolean doEmitMetaData;

  /**
   * Initialize XMLStreamReader implementation with transaction. The cursor points to the node the
   * XMLStreamReader starts to read.
   *
   * @param resourceMgr resource manager to read the resource
   * @param nodeKey start node key
   * @param builder builder of XML Serializer
   * @param revision revision to serialize
   * @param revsions further revisions to serialize
   */
  private XmlSerializer(final XmlResourceManager resourceMgr, final @Nonnegative long nodeKey,
      final XmlSerializerBuilder builder, final boolean initialIndent, final @Nonnegative int revision,
      final int... revsions) {
    super(resourceMgr, builder.mMaxLevel == -1
        ? null
        : new XmlMaxLevelVisitor(builder.mMaxLevel), nodeKey, revision, revsions);
    out = new BufferedOutputStream(builder.mStream, 4096);
    doIndent = builder.mIndent;
    doSerializeXMLDeclaration = builder.mDeclaration;
    doSerializeRest = builder.mREST;
    doSerializeRestSequence = builder.mRESTSequence;
    doSerializeId = builder.mID;
    indentSpaces = builder.mIndentSpaces;
    withInitialIndent = builder.mInitialIndent;
    emitXQueryResultSequence = builder.mEmitXQueryResultSequence;
    doSerializeTimestamp = builder.mSerializeTimestamp;
    doEmitMetaData = builder.mMetaData;
  }

  /**
   * Emit node (start element or characters).
   *
   * @param rtx Sirix {@link XmlNodeReadOnlyTrx}
   */
  @Override
  protected void emitNode(final XmlNodeReadOnlyTrx rtx) {
    try {
      switch (rtx.getKind()) {
        case XML_DOCUMENT:
          break;
        case ELEMENT:
          // Emit start element.
          indent();
          out.write(CharsForSerializing.OPEN.getBytes());
          writeQName(rtx);
          final long key = rtx.getNodeKey();
          // Emit namespace declarations.
          for (int index = 0, nspCount = rtx.getNamespaceCount(); index < nspCount; index++) {
            rtx.moveToNamespace(index);
            if (rtx.getPrefixKey() == -1) {
              out.write(CharsForSerializing.XMLNS.getBytes());
              write(rtx.nameForKey(rtx.getURIKey()));
              out.write(CharsForSerializing.QUOTE.getBytes());
            } else {
              out.write(CharsForSerializing.XMLNS_COLON.getBytes());
              write(rtx.nameForKey(rtx.getPrefixKey()));
              out.write(CharsForSerializing.EQUAL_QUOTE.getBytes());
              write(rtx.nameForKey(rtx.getURIKey()));
              out.write(CharsForSerializing.QUOTE.getBytes());
            }
            rtx.moveTo(key);
          }
          // Emit attributes.
          // Add virtual rest:id attribute.
          if (doSerializeId || doEmitMetaData) {
            if (doSerializeRest) {
              out.write(CharsForSerializing.REST_PREFIX.getBytes());
            } else if (revisions.length > 1 || (revisions.length == 1 && revisions[0] == -1)) {
              out.write(CharsForSerializing.SID_PREFIX.getBytes());
            } else {
              out.write(CharsForSerializing.SPACE.getBytes());
            }
            out.write(CharsForSerializing.ID.getBytes());
            out.write(CharsForSerializing.EQUAL_QUOTE.getBytes());
            write(rtx.getNodeKey());
            out.write(CharsForSerializing.QUOTE.getBytes());
          }
          if (doEmitMetaData) {
            if (doSerializeRest) {
              out.write(CharsForSerializing.REST_PREFIX.getBytes());
            } else if (revisions.length > 1 || (revisions.length == 1 && revisions[0] == -1)) {
              out.write(CharsForSerializing.SID_PREFIX.getBytes());
            } else {
              out.write(CharsForSerializing.SPACE.getBytes());
            }
            out.write(CharsForSerializing.ID.getBytes());
            out.write(CharsForSerializing.EQUAL_QUOTE.getBytes());
            write(rtx.getNodeKey());
            out.write(CharsForSerializing.QUOTE.getBytes());
          }

          // Iterate over all persistent attributes.
          for (int index = 0, attCount = rtx.getAttributeCount(); index < attCount; index++) {
            rtx.moveToAttribute(index);
            out.write(CharsForSerializing.SPACE.getBytes());
            writeQName(rtx);
            out.write(CharsForSerializing.EQUAL_QUOTE.getBytes());
            out.write(XMLToken.escapeAttribute(rtx.getValue()).getBytes(Constants.DEFAULT_ENCODING));
            out.write(CharsForSerializing.QUOTE.getBytes());
            rtx.moveTo(key);
          }
          if (rtx.hasFirstChild() && (visitor == null || currentLevel() + 1 < maxLevel())) {
            out.write(CharsForSerializing.CLOSE.getBytes());
          } else {
            out.write(CharsForSerializing.SLASH_CLOSE.getBytes());
          }
          if (doIndent && !(rtx.getFirstChildKind() == NodeKind.TEXT && rtx.getChildCount() == 1)) {
            out.write(CharsForSerializing.NEWLINE.getBytes());
          }
          break;
        case COMMENT:
          indent();
          out.write(CharsForSerializing.OPENCOMMENT.getBytes());
          out.write(XMLToken.escapeContent(rtx.getValue()).getBytes(Constants.DEFAULT_ENCODING));
          if (doIndent) {
            out.write(CharsForSerializing.NEWLINE.getBytes());
          }
          out.write(CharsForSerializing.CLOSECOMMENT.getBytes());
          break;
        case TEXT:
          if (rtx.hasRightSibling() || rtx.hasLeftSibling())
            indent();
          out.write(XMLToken.escapeContent(rtx.getValue()).getBytes(Constants.DEFAULT_ENCODING));
          if (doIndent && (rtx.hasRightSibling() || rtx.hasLeftSibling())) {
            out.write(CharsForSerializing.NEWLINE.getBytes());
          }
          break;
        case PROCESSING_INSTRUCTION:
          indent();
          out.write(CharsForSerializing.OPENPI.getBytes());
          writeQName(rtx);
          out.write(CharsForSerializing.SPACE.getBytes());
          out.write(XMLToken.escapeContent(rtx.getValue()).getBytes(Constants.DEFAULT_ENCODING));
          if (doIndent) {
            out.write(CharsForSerializing.NEWLINE.getBytes());
          }
          out.write(CharsForSerializing.CLOSEPI.getBytes());
          break;
        // $CASES-OMITTED$
        default:
          throw new IllegalStateException("Node kind not known!");
      }
    } catch (final IOException e) {
      LOGWRAPPER.error(e.getMessage(), e);
    }
  }

  /**
   * Emit end element.
   *
   * @param rtx Sirix {@link XmlNodeReadOnlyTrx}
   */
  @Override
  protected void emitEndNode(final XmlNodeReadOnlyTrx rtx) {
    try {
      if (doIndent && !(rtx.getFirstChildKind() == NodeKind.TEXT && rtx.getChildCount() == 1))
        indent();
      out.write(CharsForSerializing.OPEN_SLASH.getBytes());
      writeQName(rtx);
      out.write(CharsForSerializing.CLOSE.getBytes());
      if (doIndent) {
        out.write(CharsForSerializing.NEWLINE.getBytes());
      }
    } catch (final IOException e) {
      LOGWRAPPER.error(e.getMessage(), e);
    }
  }

  // Write a QName.
  private void writeQName(final XmlNodeReadOnlyTrx rtx) throws IOException {
    if (rtx.getPrefixKey() != -1) {
      out.write(rtx.rawNameForKey(rtx.getPrefixKey()));
      out.write(CharsForSerializing.COLON.getBytes());
    }
    out.write(rtx.rawNameForKey(rtx.getLocalNameKey()));
  }

  @Override
  protected void emitStartDocument() {
    try {
      if (doSerializeXMLDeclaration) {
        write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        if (doIndent) {
          out.write(CharsForSerializing.NEWLINE.getBytes());
        }
      }

      final int length = (revisions.length == 1 && revisions[0] < 0)
          ? resMgr.getMostRecentRevisionNumber()
          : revisions.length;

      if (doSerializeRestSequence || length > 1) {
        if (doSerializeRestSequence) {
          write("<rest:sequence xmlns:rest=\"https://sirix.io/rest\">");
        } else {
          write("<sdb:sirix xmlns:sdb=\"https://sirix.io/rest\">");
        }

        if (doIndent) {
          out.write(CharsForSerializing.NEWLINE.getBytes());
          stack.push(Constants.NULL_ID_LONG);
        }
      }
    } catch (final IOException e) {
      LOGWRAPPER.error(e.getMessage(), e);
    }
  }

  @Override
  protected void emitEndDocument() {
    try {
      final int length = (revisions.length == 1 && revisions[0] < 0)
          ? resMgr.getMostRecentRevisionNumber()
          : revisions.length;

      if (doSerializeRestSequence || length > 1) {
        if (doIndent) {
          stack.pop();
        }
        indent();

        if (doSerializeRestSequence) {
          write("</rest:sequence>");
        } else {
          write("</sdb:sirix>");
        }
      }

      out.flush();
    } catch (final IOException e) {
      LOGWRAPPER.error(e.getMessage(), e);
    }
  }

  @Override
  protected void emitRevisionStartNode(final @Nonnull XmlNodeReadOnlyTrx rtx) {
    try {
      final int length = (revisions.length == 1 && revisions[0] < 0)
          ? resMgr.getMostRecentRevisionNumber()
          : revisions.length;

      if (doSerializeRest || length > 1) {
        indent();
        if (doSerializeRest) {
          write("<rest:item");
        } else {
          write("<sdb:sirix-item");
        }

        if (length > 1 || emitXQueryResultSequence) {
          if (doSerializeRest) {
            write(" rest:revision=\"");
          } else {
            write(" sdb:revision=\"");
          }
          write(Integer.toString(rtx.getRevisionNumber()));
          write("\"");

          if (doSerializeTimestamp) {
            if (doSerializeRest) {
              write(" rest:revisionTimestamp=\"");
            } else {
              write(" sdb:revisionTimestamp=\"");
            }

            write(DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(rtx.getRevisionTimestamp()));
            write("\"");
          }

          write(">");
        } else if (doSerializeRest) {
          write(">");
        }

        if (rtx.hasFirstChild())
          stack.push(Constants.NULL_ID_LONG);

        if (doIndent) {
          out.write(CharsForSerializing.NEWLINE.getBytes());
        }
      }
    } catch (final IOException e) {
      LOGWRAPPER.error(e.getMessage(), e);
    }
  }

  @Override
  protected void emitRevisionEndNode(final @Nonnull XmlNodeReadOnlyTrx rtx) {
    try {
      final int length = (revisions.length == 1 && revisions[0] < 0)
          ? resMgr.getMostRecentRevisionNumber()
          : revisions.length;

      if (doSerializeRest || length > 1) {
        if (rtx.moveToDocumentRoot().trx().hasFirstChild())
          stack.pop();
        indent();
        if (doSerializeRest) {
          write("</rest:item>");
        } else {
          write("</sdb:sirix-item>");
        }
      }

      if (doIndent) {
        out.write(CharsForSerializing.NEWLINE.getBytes());
      }
    } catch (final IOException e) {
      LOGWRAPPER.error(e.getMessage(), e);
    }
  }

  @Override
  protected void setTrxForVisitor(XmlNodeReadOnlyTrx rtx) {
    castVisitor().setTrx(rtx);
  }

  private long maxLevel() {
    return castVisitor().getMaxLevel();
  }

  private XmlMaxLevelVisitor castVisitor() {
    return (XmlMaxLevelVisitor) visitor;
  }

  private long currentLevel() {
    return castVisitor().getCurrentLevel();
  }

  @Override
  protected boolean isSubtreeGoingToBeVisited(final XmlNodeReadOnlyTrx rtx) {
    return visitor == null || currentLevel() + 1 < maxLevel();
  }

  @Override
  protected boolean isSubtreeGoingToBePruned(final XmlNodeReadOnlyTrx rtx) {
    if (visitor == null) {
      return false;
    } else {
      return currentLevel() + 1 >= maxLevel();
    }
  }

  /**
   * Indentation of output.
   *
   * @throws IOException if can't indent output
   */
  private void indent() throws IOException {
    if (doIndent) {
      final int indentSpaces = withInitialIndent
          ? (stack.size() + 1) * this.indentSpaces
          : stack.size() * this.indentSpaces;
      for (int i = 0; i < indentSpaces; i++) {
        out.write(" ".getBytes(Constants.DEFAULT_ENCODING));
      }
    }
  }

  /**
   * Write characters of string.
   *
   * @param value value to write
   * @throws IOException if can't write to string
   * @throws UnsupportedEncodingException if unsupport encoding
   */
  protected void write(final String value) throws UnsupportedEncodingException, IOException {
    out.write(value.getBytes(Constants.DEFAULT_ENCODING));
  }

  /**
   * Write non-negative non-zero long as UTF-8 bytes.
   *
   * @param value value to write
   * @throws IOException if can't write to string
   */
  private void write(final long value) throws IOException {
    final int length = (int) Math.log10(value);
    int digit;
    long remainder = value;
    for (int i = length; i >= 0; i--) {
      digit = (byte) (remainder / LONG_POWERS[i]);
      out.write((byte) (digit + ASCII_OFFSET));
      remainder -= digit * LONG_POWERS[i];
    }
  }

  /**
   * Main method.
   *
   * @param args args[0] specifies the input-TT file/folder; args[1] specifies the output XML file.
   * @throws Exception any exception
   */
  public static void main(final String... args) throws Exception {
    if (args.length < 2 || args.length > 3) {
      throw new IllegalArgumentException("Usage: XMLSerializer input-TT output.xml");
    }

    LOGWRAPPER.info("Serializing '" + args[0] + "' to '" + args[1] + "' ... ");
    final long time = System.nanoTime();
    final Path target = Paths.get(args[1]);
    SirixFiles.recursiveRemove(target);
    Files.createDirectories(target.getParent());
    Files.createFile(target);

    final Path databaseFile = Paths.get(args[0]);
    final DatabaseConfiguration config = new DatabaseConfiguration(databaseFile);
    Databases.createXmlDatabase(config);
    try (final var db = Databases.openXmlDatabase(databaseFile)) {
      db.createResource(ResourceConfiguration.newBuilder("shredded").build());

      try (final XmlResourceManager resMgr = db.openResourceManager("shredded");
          final FileOutputStream outputStream = new FileOutputStream(target.toFile())) {
        final XmlSerializer serializer = XmlSerializer.newBuilder(resMgr, outputStream).emitXMLDeclaration().build();
        serializer.call();
      }
    }

    LOGWRAPPER.info(" done [" + (System.nanoTime() - time) / 1_000_000 + "ms].");
  }

  /**
   * Constructor, setting the necessary stuff.
   *
   * @param resMgr Sirix {@link ResourceManager}
   * @param stream {@link OutputStream} to write to
   * @param revisions revisions to serialize
   */
  public static XmlSerializerBuilder newBuilder(final XmlResourceManager resMgr, final OutputStream stream,
      final int... revisions) {
    return new XmlSerializerBuilder(resMgr, stream, revisions);
  }

  /**
   * Constructor.
   *
   * @param resMgr Sirix {@link ResourceManager}
   * @param nodeKey root node key of subtree to shredder
   * @param stream {@link OutputStream} to write to
   * @param properties {@link XmlSerializerProperties} to use
   * @param revisions revisions to serialize
   */
  public static XmlSerializerBuilder newBuilder(final XmlResourceManager resMgr, final @Nonnegative long nodeKey,
      final OutputStream stream, final XmlSerializerProperties properties, final int... revisions) {
    return new XmlSerializerBuilder(resMgr, nodeKey, stream, properties, revisions);
  }

  /**
   * XMLSerializerBuilder to setup the XMLSerializer.
   */
  public static final class XmlSerializerBuilder {
    public boolean mRESTSequence;

    /**
     * Intermediate boolean for indendation, not necessary.
     */
    private boolean mIndent;

    /**
     * Intermediate boolean for rest serialization, not necessary.
     */
    private boolean mREST;

    /**
     * Intermediate boolean for XML-Decl serialization, not necessary.
     */
    private boolean mDeclaration;

    /**
     * Intermediate boolean for ids, not necessary.
     */
    private boolean mID;

    /**
     * Intermediate number of spaces to indent, not necessary.
     */
    private int mIndentSpaces = 2;

    /** Stream to pipe to. */
    private final OutputStream mStream;

    /** Resource manager to use. */
    private final XmlResourceManager mResourceMgr;

    /** Further revisions to serialize. */
    private int[] mVersions;

    /** Revision to serialize. */
    private int mVersion;

    /** Node key of subtree to shredder. */
    private long mNodeKey;

    private boolean mInitialIndent;

    private boolean mEmitXQueryResultSequence;

    private boolean mSerializeTimestamp;

    private boolean mMetaData;

    private long mMaxLevel;

    /**
     * Constructor, setting the necessary stuff.
     *
     * @param resourceMgr Sirix {@link ResourceManager}
     * @param stream {@link OutputStream} to write to
     * @param revisions revisions to serialize
     */
    public XmlSerializerBuilder(final XmlResourceManager resourceMgr, final OutputStream stream,
        final int... revisions) {
      mMaxLevel = -1;
      mNodeKey = 0;
      mResourceMgr = checkNotNull(resourceMgr);
      mStream = checkNotNull(stream);
      if (revisions == null || revisions.length == 0) {
        mVersion = mResourceMgr.getMostRecentRevisionNumber();
      } else {
        mVersion = revisions[0];
        mVersions = new int[revisions.length - 1];
        System.arraycopy(revisions, 1, mVersions, 0, revisions.length - 1);
      }
    }

    /**
     * Constructor.
     *
     * @param resourceMgr Sirix {@link ResourceManager}
     * @param nodeKey root node key of subtree to shredder
     * @param stream {@link OutputStream} to write to
     * @param properties {@link XmlSerializerProperties} to use
     * @param revisions revisions to serialize
     */
    public XmlSerializerBuilder(final XmlResourceManager resourceMgr, final @Nonnegative long nodeKey,
        final OutputStream stream, final XmlSerializerProperties properties, final int... revisions) {
      checkArgument(nodeKey >= 0, "nodeKey must be >= 0!");
      mMaxLevel = -1;
      mResourceMgr = checkNotNull(resourceMgr);
      mNodeKey = nodeKey;
      mStream = checkNotNull(stream);
      if (revisions == null || revisions.length == 0) {
        mVersion = mResourceMgr.getMostRecentRevisionNumber();
      } else {
        mVersion = revisions[0];
        mVersions = new int[revisions.length - 1];
        System.arraycopy(revisions, 1, mVersions, 0, revisions.length - 1);
      }
      final ConcurrentMap<?, ?> map = checkNotNull(properties.getProps());
      mIndent = checkNotNull((Boolean) map.get(S_INDENT[0]));
      mREST = checkNotNull((Boolean) map.get(S_REST[0]));
      mID = checkNotNull((Boolean) map.get(S_ID[0]));
      mIndentSpaces = checkNotNull((Integer) map.get(S_INDENT_SPACES[0]));
      mDeclaration = checkNotNull((Boolean) map.get(S_XMLDECL[0]));
    }

    /**
     * Specify the start node key.
     *
     * @param nodeKey node key to start serialization from (the root of the subtree to serialize)
     * @return this XMLSerializerBuilder reference
     */
    public XmlSerializerBuilder startNodeKey(final long nodeKey) {
      mNodeKey = nodeKey;
      return this;
    }

    /**
     * Sets an initial indentation.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder withInitialIndent() {
      mInitialIndent = true;
      return this;
    }

    /**
     * Sets if the serialization is used for XQuery result sets.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder isXQueryResultSequence() {
      mEmitXQueryResultSequence = true;
      return this;
    }

    /**
     * Sets if the serialization of timestamps of the revision(s) is used or not.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder serializeTimestamp(boolean serializeTimestamp) {
      mSerializeTimestamp = serializeTimestamp;
      return this;
    }

    /**
     * Pretty prints the output.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder prettyPrint() {
      mIndent = true;
      return this;
    }

    /**
     * Emit RESTful output.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder emitRESTful() {
      mREST = true;
      return this;
    }

    /**
     * Emit a rest-sequence start-tag/end-tag in startDocument()/endDocument() method.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder emitRESTSequence() {
      mRESTSequence = true;
      return this;
    }

    /**
     * Emit an XML declaration.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder emitXMLDeclaration() {
      mDeclaration = true;
      return this;
    }

    /**
     * Emit the unique nodeKeys / IDs of element-nodes.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder emitIDs() {
      mID = true;
      return this;
    }

    /**
     * Emit metadata of element-nodes.
     *
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder emitMetaData() {
      mMetaData = true;
      return this;
    }

    /**
     * The maximum level.
     *
     * @param maxLevel the maximum level
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder maxLevel(final long maxLevel) {
      mMaxLevel = maxLevel;
      return this;
    }

    /**
     * The versions to serialize.
     *
     * @param revisions the versions to serialize
     * @return this {@link XmlSerializerBuilder} instance
     */
    public XmlSerializerBuilder revisions(final int[] revisions) {
      checkNotNull(revisions);

      mVersion = revisions[0];

      mVersions = new int[revisions.length - 1];
      System.arraycopy(revisions, 1, mVersions, 0, revisions.length - 1);

      return this;
    }

    /**
     * Building new {@link Serializer} instance.
     *
     * @return a new {@link Serializer} instance
     */
    public XmlSerializer build() {
      return new XmlSerializer(mResourceMgr, mNodeKey, this, mInitialIndent, mVersion, mVersions);
    }
  }
}
