package org.sirix.node.xml;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.hash.HashCode;
import org.sirix.api.visitor.VisitResult;
import org.sirix.api.visitor.XmlNodeVisitor;
import org.sirix.node.NodeKind;
import org.sirix.node.SirixDeweyID;
import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.delegates.StructNodeDelegate;
import org.sirix.node.delegates.ValueNodeDelegate;
import org.sirix.node.immutable.xml.ImmutableComment;
import org.sirix.node.interfaces.Node;
import org.sirix.node.interfaces.StructNode;
import org.sirix.node.interfaces.ValueNode;
import org.sirix.node.interfaces.immutable.ImmutableXmlNode;
import org.sirix.settings.Constants;
import org.sirix.settings.Fixed;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Comment node implementation.
 *
 * @author Johannes Lichtenberger
 */
public final class CommentNode extends AbstractStructForwardingNode implements ValueNode, ImmutableXmlNode {

  /**
   * {@link StructNodeDelegate} reference.
   */
  private final StructNodeDelegate structNodeDelegate;

  /**
   * {@link ValueNodeDelegate} reference.
   */
  private final ValueNodeDelegate valueNodeDelegate;

  /**
   * Value of the node.
   */
  private byte[] value;

  private BigInteger hash;

  /**
   * Constructor for TextNode.
   *
   * @param valueNodeDelegate  delegate for {@link ValueNode} implementation
   * @param structNodeDelegate delegate for {@link StructNode} implementation
   */
  public CommentNode(final BigInteger hashCode, final ValueNodeDelegate valueNodeDelegate,
      final StructNodeDelegate structNodeDelegate) {
    hash = hashCode;
    assert valueNodeDelegate != null;
    this.valueNodeDelegate = valueNodeDelegate;
    assert structNodeDelegate != null;
    this.structNodeDelegate = structNodeDelegate;
  }

  /**
   * Constructor for TextNode.
   *
   * @param valDel    delegate for {@link ValueNode} implementation
   * @param structDel delegate for {@link StructNode} implementation
   */
  public CommentNode(final ValueNodeDelegate valDel, final StructNodeDelegate structDel) {
    assert valDel != null;
    valueNodeDelegate = valDel;
    assert structDel != null;
    structNodeDelegate = structDel;
  }

  @Override
  public NodeKind getKind() {
    return NodeKind.COMMENT;
  }

  @Override
  public BigInteger computeHash() {
    final HashCode valueHashCode = structNodeDelegate.getNodeDelegate().getHashFunction().hashBytes(getRawValue());

    final BigInteger valueBigInteger = new BigInteger(1, valueHashCode.asBytes());

    BigInteger result = BigInteger.ONE;

    result = BigInteger.valueOf(31).multiply(result).add(structNodeDelegate.getNodeDelegate().computeHash());
    result = BigInteger.valueOf(31).multiply(result).add(structNodeDelegate.computeHash());
    result = BigInteger.valueOf(31).multiply(result).add(valueBigInteger);

    return Node.to128BitsAtMaximumBigInteger(result);
  }

  @Override
  public void setHash(final BigInteger hash) {
    this.hash = Node.to128BitsAtMaximumBigInteger(hash);
  }

  @Override
  public BigInteger getHash() {
    return hash;
  }

  @Override
  public byte[] getRawValue() {
    if (value == null) {
      value = valueNodeDelegate.getRawValue();
    }
    return value;
  }

  @Override
  public void setValue(final byte[] value) {
    this.value = null;
    valueNodeDelegate.setValue(value);
  }

  @Override
  public long getFirstChildKey() {
    return Fixed.NULL_NODE_KEY.getStandardProperty();
  }

  @Override
  public VisitResult acceptVisitor(final XmlNodeVisitor visitor) {
    return visitor.visit(ImmutableComment.of(this));
  }

  @Override
  public void decrementChildCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void incrementChildCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getDescendantCount() {
    return 0;
  }

  @Override
  public void decrementDescendantCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void incrementDescendantCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDescendantCount(final long descendantCount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(structNodeDelegate.getNodeDelegate(), valueNodeDelegate);
  }

  @Override
  public boolean equals(final @Nullable Object obj) {
    if (obj instanceof CommentNode) {
      final CommentNode other = (CommentNode) obj;
      return Objects.equal(structNodeDelegate.getNodeDelegate(), other.getNodeDelegate()) && valueNodeDelegate.equals(
          other.valueNodeDelegate);
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("node delegate", structNodeDelegate.getNodeDelegate())
                      .add("value delegate", valueNodeDelegate)
                      .toString();
  }

  public ValueNodeDelegate getValNodeDelegate() {
    return valueNodeDelegate;
  }

  @Override
  protected NodeDelegate delegate() {
    return structNodeDelegate.getNodeDelegate();
  }

  @Override
  protected StructNodeDelegate structDelegate() {
    return structNodeDelegate;
  }

  @Override
  public String getValue() {
    return new String(valueNodeDelegate.getRawValue(), Constants.DEFAULT_ENCODING);
  }

  @Override
  public Optional<SirixDeweyID> getDeweyID() {
    return structNodeDelegate.getNodeDelegate().getDeweyID();
  }

  @Override
  public int getTypeKey() {
    return structNodeDelegate.getNodeDelegate().getTypeKey();
  }

}
