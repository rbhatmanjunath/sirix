package org.sirix.access.trx.node.xml;

import com.google.common.collect.HashBiMap;
import com.google.common.hash.HashFunction;
import org.brackit.xquery.atomic.QNm;
import org.sirix.api.PageTrx;
import org.sirix.index.path.summary.PathNode;
import org.sirix.node.NodeKind;
import org.sirix.node.SirixDeweyID;
import org.sirix.node.delegates.NameNodeDelegate;
import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.delegates.StructNodeDelegate;
import org.sirix.node.delegates.ValueNodeDelegate;
import org.sirix.node.interfaces.DataRecord;
import org.sirix.node.xml.*;
import org.sirix.page.PageKind;
import org.sirix.page.PathSummaryPage;
import org.sirix.page.UnorderedKeyValuePage;
import org.sirix.settings.Fixed;
import org.sirix.utils.Compression;
import org.sirix.utils.NamePageHash;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.zip.Deflater;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Node factory to create nodes.
 *
 * @author Johannes Lichtenberger
 *
 */
final class XmlNodeFactoryImpl implements XmlNodeFactory {

  /** {@link PageTrx} implementation. */
  private final PageTrx<Long, DataRecord, UnorderedKeyValuePage> mPageWriteTrx;

  /** The hash function used for hashing nodes. */
  private final HashFunction mHashFunction;

  /**
   * Constructor.
   *
   * @param hashFunction the hash function used for hashing nodes
   * @param pageWriteTrx {@link PageTrx} implementation
   */
  XmlNodeFactoryImpl(final HashFunction hashFunction, final PageTrx<Long, DataRecord, UnorderedKeyValuePage> pageWriteTrx) {
    mPageWriteTrx = checkNotNull(pageWriteTrx);
    mPageWriteTrx.createNameKey("xs:untyped", NodeKind.ATTRIBUTE);
    mPageWriteTrx.createNameKey("xs:untyped", NodeKind.NAMESPACE);
    mPageWriteTrx.createNameKey("xs:untyped", NodeKind.ELEMENT);
    mPageWriteTrx.createNameKey("xs:untyped", NodeKind.PROCESSING_INSTRUCTION);
    mHashFunction = checkNotNull(hashFunction);
  }

  @Override
  public PathNode createPathNode(final @Nonnegative long parentKey, final @Nonnegative long leftSibKey,
      final long rightSibKey, @Nonnull final QNm name, @Nonnull final NodeKind kind, final @Nonnegative int level) {
    final int uriKey = NamePageHash.generateHashForString(name.getNamespaceURI());
    final int prefixKey = name.getPrefix() != null && !name.getPrefix().isEmpty()
        ? NamePageHash.generateHashForString(name.getPrefix())
        : -1;
    return getPathNode(parentKey, leftSibKey, rightSibKey, name, kind, level, uriKey, prefixKey, mPageWriteTrx,
        mHashFunction);
  }

  public static PathNode getPathNode(@Nonnegative long parentKey, @Nonnegative long leftSibKey, long rightSibKey,
      @Nonnull QNm name, @Nonnull NodeKind kind, @Nonnegative int level, int uriKey, int prefixKey,
      PageTrx<Long, DataRecord, UnorderedKeyValuePage> mPageWriteTrx, HashFunction mHashFunction) {
    final int localName = name.getLocalName() != null && !name.getLocalName().isEmpty()
        ? NamePageHash.generateHashForString(name.getLocalName())
        : -1;

    final long revision = mPageWriteTrx.getRevisionNumber();
    final NodeDelegate nodeDel = new NodeDelegate(
        ((PathSummaryPage) mPageWriteTrx.getActualRevisionRootPage()
                                        .getPathSummaryPageReference()
                                        .getPage()).getMaxNodeKey(0)
            + 1,
        parentKey, mHashFunction, null, revision, null);
    final StructNodeDelegate structDel =
        new StructNodeDelegate(nodeDel, Fixed.NULL_NODE_KEY.getStandardProperty(), rightSibKey, leftSibKey, 0, 0);
    final NameNodeDelegate nameDel = createNameNodeDelegate(0, prefixKey, localName, uriKey, nodeDel);

    return (PathNode) mPageWriteTrx.createEntry(nodeDel.getNodeKey(),
        new PathNode(name, nodeDel, structDel, nameDel, kind, 1, level), PageKind.PATHSUMMARYPAGE, 0);
  }

  @Override
  public ElementNode createElementNode(final @Nonnegative long parentKey, final @Nonnegative long leftSibKey,
      final @Nonnegative long rightSibKey, @Nonnull final QNm name, final @Nonnegative long pathNodeKey,
      final SirixDeweyID id) {
    final int uriKey = name.getNamespaceURI() != null && !name.getNamespaceURI().isEmpty()
        ? mPageWriteTrx.createNameKey(name.getNamespaceURI(), NodeKind.NAMESPACE)
        : -1;
    final int prefixKey = name.getPrefix() != null && !name.getPrefix().isEmpty()
        ? mPageWriteTrx.createNameKey(name.getPrefix(), NodeKind.ELEMENT)
        : -1;
    final int localNameKey = mPageWriteTrx.createNameKey(name.getLocalName(), NodeKind.ELEMENT);

    final long revision = mPageWriteTrx.getRevisionNumber();
    final NodeDelegate nodeDel = new NodeDelegate(mPageWriteTrx.getActualRevisionRootPage().getMaxNodeKey() + 1,
        parentKey, mHashFunction, null, revision, id);
    final StructNodeDelegate structDel =
        new StructNodeDelegate(nodeDel, Fixed.NULL_NODE_KEY.getStandardProperty(), rightSibKey, leftSibKey, 0, 0);
    final NameNodeDelegate nameDel = createNameNodeDelegate(pathNodeKey, prefixKey, localNameKey, uriKey, nodeDel);

    return (ElementNode) mPageWriteTrx.createEntry(nodeDel.getNodeKey(),
        new ElementNode(structDel, nameDel, new ArrayList<>(), HashBiMap.create(), new ArrayList<>(), name),
        PageKind.RECORDPAGE, -1);
  }

  @Override
  public TextNode createTextNode(final @Nonnegative long parentKey, final @Nonnegative long leftSibKey,
      final @Nonnegative long rightSibKey, @Nonnull final byte[] value, final boolean isCompressed,
      final SirixDeweyID id) {
    final long revision = mPageWriteTrx.getRevisionNumber();
    final NodeDelegate nodeDel = new NodeDelegate(mPageWriteTrx.getActualRevisionRootPage().getMaxNodeKey() + 1,
        parentKey, mHashFunction, null, revision, id);
    final boolean compression = isCompressed && value.length > 10;
    final byte[] compressedValue = compression
        ? Compression.compress(value, Deflater.HUFFMAN_ONLY)
        : value;
    final ValueNodeDelegate valDel = new ValueNodeDelegate(nodeDel, compressedValue, compression);
    final StructNodeDelegate structDel =
        new StructNodeDelegate(nodeDel, Fixed.NULL_NODE_KEY.getStandardProperty(), rightSibKey, leftSibKey, 0, 0);
    return (TextNode) mPageWriteTrx.createEntry(nodeDel.getNodeKey(), new TextNode(valDel, structDel),
        PageKind.RECORDPAGE, -1);
  }

  @Override
  public AttributeNode createAttributeNode(final @Nonnegative long parentKey, @Nonnull final QNm name,
      @Nonnull final byte[] value, final @Nonnegative long pathNodeKey, final SirixDeweyID id) {
    final long revision = mPageWriteTrx.getRevisionNumber();
    final int uriKey = mPageWriteTrx.createNameKey(name.getNamespaceURI(), NodeKind.NAMESPACE);
    final int prefixKey = name.getPrefix() != null && !name.getPrefix().isEmpty()
        ? mPageWriteTrx.createNameKey(name.getPrefix(), NodeKind.ATTRIBUTE)
        : -1;
    final int localNameKey = mPageWriteTrx.createNameKey(name.getLocalName(), NodeKind.ATTRIBUTE);

    final NodeDelegate nodeDel = new NodeDelegate(mPageWriteTrx.getActualRevisionRootPage().getMaxNodeKey() + 1,
        parentKey, mHashFunction, null, revision, id);
    final NameNodeDelegate nameDel = createNameNodeDelegate(pathNodeKey, prefixKey, localNameKey, uriKey, nodeDel);
    final ValueNodeDelegate valDel = new ValueNodeDelegate(nodeDel, value, false);

    return (AttributeNode) mPageWriteTrx.createEntry(nodeDel.getNodeKey(),
        new AttributeNode(nodeDel, nameDel, valDel, name), PageKind.RECORDPAGE, -1);
  }

  @Override
  public NamespaceNode createNamespaceNode(final @Nonnegative long parentKey, final QNm name,
      final @Nonnegative long pathNodeKey, final SirixDeweyID id) {
    final long revision = mPageWriteTrx.getRevisionNumber();
    final NodeDelegate nodeDel = new NodeDelegate(mPageWriteTrx.getActualRevisionRootPage().getMaxNodeKey() + 1,
        parentKey, mHashFunction, null, revision, id);

    final int uriKey = mPageWriteTrx.createNameKey(name.getNamespaceURI(), NodeKind.NAMESPACE);
    final int prefixKey = name.getPrefix() != null && !name.getPrefix().isEmpty()
        ? mPageWriteTrx.createNameKey(name.getPrefix(), NodeKind.NAMESPACE)
        : -1;

    final NameNodeDelegate nameDel = createNameNodeDelegate(pathNodeKey, prefixKey, -1, uriKey, nodeDel);

    return (NamespaceNode) mPageWriteTrx.createEntry(nodeDel.getNodeKey(), new NamespaceNode(nodeDel, nameDel, name),
        PageKind.RECORDPAGE, -1);
  }

  @Override
  public PINode createPINode(final @Nonnegative long parentKey, final @Nonnegative long leftSibKey,
      final @Nonnegative long rightSibKey, final QNm target, final byte[] content, final boolean isCompressed,
      final @Nonnegative long pathNodeKey, final SirixDeweyID id) {
    final long revision = mPageWriteTrx.getRevisionNumber();

    final int prefixKey = target.getPrefix() != null && !target.getPrefix().isEmpty()
        ? mPageWriteTrx.createNameKey(target.getPrefix(), NodeKind.PROCESSING_INSTRUCTION)
        : -1;
    final int localNameKey = mPageWriteTrx.createNameKey(target.getLocalName(), NodeKind.PROCESSING_INSTRUCTION);
    final int uriKey = mPageWriteTrx.createNameKey(target.getNamespaceURI(), NodeKind.NAMESPACE);
    final NodeDelegate nodeDel = new NodeDelegate(mPageWriteTrx.getActualRevisionRootPage().getMaxNodeKey() + 1,
        parentKey, mHashFunction, null, revision, id);
    final StructNodeDelegate structDel =
        new StructNodeDelegate(nodeDel, Fixed.NULL_NODE_KEY.getStandardProperty(), rightSibKey, leftSibKey, 0, 0);
    final NameNodeDelegate nameDel = createNameNodeDelegate(pathNodeKey, prefixKey, localNameKey, uriKey, nodeDel);
    final ValueNodeDelegate valDel = new ValueNodeDelegate(nodeDel, content, false);

    return (PINode) mPageWriteTrx.createEntry(nodeDel.getNodeKey(),
        new PINode(structDel, nameDel, valDel, mPageWriteTrx), PageKind.RECORDPAGE, -1);
  }

  public static NameNodeDelegate createNameNodeDelegate(@Nonnegative long pathNodeKey, int prefixKey, int localNameKey,
      int uriKey, NodeDelegate nodeDel) {
    return new NameNodeDelegate(nodeDel, uriKey, prefixKey, localNameKey, pathNodeKey);
  }

  @Override
  public CommentNode createCommentNode(final @Nonnegative long parentKey, final @Nonnegative long leftSibKey,
      final @Nonnegative long rightSibKey, final byte[] value, final boolean isCompressed, final SirixDeweyID id) {
    final long revision = mPageWriteTrx.getRevisionNumber();
    final NodeDelegate nodeDel = new NodeDelegate(mPageWriteTrx.getActualRevisionRootPage().getMaxNodeKey() + 1,
        parentKey, mHashFunction, null, revision, id);
    final boolean compression = isCompressed && value.length > 10;
    final byte[] compressedValue = compression
        ? Compression.compress(value, Deflater.HUFFMAN_ONLY)
        : value;
    final ValueNodeDelegate valDel = new ValueNodeDelegate(nodeDel, compressedValue, compression);
    final StructNodeDelegate structDel =
        new StructNodeDelegate(nodeDel, Fixed.NULL_NODE_KEY.getStandardProperty(), rightSibKey, leftSibKey, 0, 0);
    return (CommentNode) mPageWriteTrx.createEntry(nodeDel.getNodeKey(), new CommentNode(valDel, structDel),
        PageKind.RECORDPAGE, -1);
  }
}
