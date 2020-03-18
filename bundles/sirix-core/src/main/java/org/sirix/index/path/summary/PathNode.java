package org.sirix.index.path.summary;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.path.Path;
import org.sirix.node.NodeKind;
import org.sirix.node.delegates.NameNodeDelegate;
import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.delegates.StructNodeDelegate;
import org.sirix.node.interfaces.NameNode;
import org.sirix.node.xml.AbstractStructForwardingNode;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Path node in the {@link PathSummaryReader}.
 *
 * @author Johannes Lichtenberger
 */
public final class PathNode extends AbstractStructForwardingNode implements NameNode {

  /**
   * {@link NodeDelegate} instance.
   */
  private final NodeDelegate nodeDelegate;

  /**
   * {@link StructNodeDelegate} instance.
   */
  private final StructNodeDelegate structNodeDelegate;

  /**
   * {@link NameNodeDelegate} instance.
   */
  private final NameNodeDelegate nameNodeDelegate;

  /**
   * Kind of node to index.
   */
  private final NodeKind nodeKind;

  /**
   * The node name.
   */
  private final QNm name;

  /**
   * Number of references to this path node.
   */
  private int references;

  /**
   * Level of this path node.
   */
  private int level;

  /**
   * Constructor.
   *
   * @param name          the full qualified name
   * @param nodeDelegate       {@link NodeDelegate} instance
   * @param structNodeDelegate {@link StructNodeDelegate} instance
   * @param nameNodeDelegate   {@link NameNodeDelegate} instance
   * @param nodeKind          kind of node to index
   * @param references    number of references to this path node
   * @param level         level of this path node
   */
  public PathNode(final QNm name, final NodeDelegate nodeDelegate, @Nonnull final StructNodeDelegate structNodeDelegate,
      @Nonnull final NameNodeDelegate nameNodeDelegate, @Nonnull final NodeKind nodeKind, @Nonnegative final int references,
      @Nonnegative final int level) {
    this.name = checkNotNull(name);
    this.nodeDelegate = checkNotNull(nodeDelegate);
    this.structNodeDelegate = checkNotNull(structNodeDelegate);
    this.nameNodeDelegate = checkNotNull(nameNodeDelegate);
    this.nodeKind = checkNotNull(nodeKind);
    checkArgument(references > 0, "references must be > 0!");
    this.references = references;
    this.level = level;
  }

  /**
   * Get the path up to the root path node.
   *
   * @param reader {@link PathSummaryReader} instance
   * @return path up to the root
   */
  public Path<QNm> getPath(final PathSummaryReader reader) {
    PathNode node = this;
    final long nodeKey = reader.getNodeKey();
    reader.moveTo(node.getNodeKey());
    final PathNode[] path = new PathNode[level];
    for (int i = level - 1; i >= 0; i--) {
      path[i] = node;
      node = reader.moveToParent().trx().getPathNode();
    }

    final Path<QNm> p = new Path<>();
    for (final PathNode n : path) {
      reader.moveTo(n.getNodeKey());
      if (n.getPathKind() == NodeKind.ATTRIBUTE) {
        p.attribute(reader.getName());
      } else {
        final QNm name;
        if (reader.getPathKind() == NodeKind.OBJECT_KEY) {
          name = new QNm(null, null, reader.getName().getLocalName().replace("/", "\\/"));
        } else {
          name = reader.getName();
        }
        p.child(name);
      }
    }
    reader.moveTo(nodeKey);
    return p;
  }

  /**
   * Level of this path node.
   *
   * @return level of this path node
   */
  public int getLevel() {
    return level;
  }

  /**
   * Get the number of references to this path node.
   *
   * @return number of references
   */
  public int getReferences() {
    return references;
  }

  /**
   * Set the reference count.
   *
   * @param references number of references
   */
  public void setReferenceCount(final @Nonnegative int references) {
    checkArgument(references > 0, "pReferences must be > 0!");
    this.references = references;
  }

  /**
   * Increment the reference count.
   */
  public void incrementReferenceCount() {
    references++;
  }

  /**
   * Decrement the reference count.
   */
  public void decrementReferenceCount() {
    if (references <= 1) {
      throw new IllegalStateException();
    }
    references--;
  }

  /**
   * Get the kind of path (element, attribute or namespace).
   *
   * @return path kind
   */
  public NodeKind getPathKind() {
    return nodeKind;
  }

  @Override
  public NodeKind getKind() {
    return NodeKind.PATH;
  }

  @Override
  public int getPrefixKey() {
    return nameNodeDelegate.getPrefixKey();
  }

  @Override
  public int getLocalNameKey() {
    return nameNodeDelegate.getLocalNameKey();
  }

  @Override
  public int getURIKey() {
    return nameNodeDelegate.getURIKey();
  }

  @Override
  public void setLocalNameKey(final int nameKey) {
    nameNodeDelegate.setLocalNameKey(nameKey);
  }

  @Override
  public void setPrefixKey(final int prefixKey) {
    nameNodeDelegate.setPrefixKey(prefixKey);
  }

  @Override
  public void setURIKey(final int uriKey) {
    nameNodeDelegate.setURIKey(uriKey);
  }

  @Override
  protected StructNodeDelegate structDelegate() {
    return structNodeDelegate;
  }

  @Override
  protected NodeDelegate delegate() {
    return nodeDelegate;
  }

  /**
   * Get the name node delegate.
   *
   * @return name node delegate.
   */
  public NameNodeDelegate getNameNodeDelegate() {
    return nameNodeDelegate;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(nodeDelegate, nameNodeDelegate);
  }

  @Override
  public boolean equals(final @Nullable Object obj) {
    if (obj instanceof PathNode) {
      final PathNode other = (PathNode) obj;
      return Objects.equal(nodeDelegate, other.nodeDelegate) && Objects.equal(nameNodeDelegate, other.nameNodeDelegate);
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("node delegate", nodeDelegate)
                      .add("struct delegate", structNodeDelegate)
                      .add("name delegate", nameNodeDelegate)
                      .add("references", references)
                      .add("kind", nodeKind)
                      .add("level", level)
                      .toString();
  }

  @Override
  public void setPathNodeKey(final long pNodeKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getPathNodeKey() {
    throw new UnsupportedOperationException();
  }

  @Override
  public QNm getName() {
    return name;
  }

}
