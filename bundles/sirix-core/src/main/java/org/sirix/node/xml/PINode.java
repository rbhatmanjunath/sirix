package org.sirix.node.xml;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.brackit.xquery.atomic.QNm;
import org.sirix.api.PageReadOnlyTrx;
import org.sirix.api.visitor.VisitResult;
import org.sirix.api.visitor.XmlNodeVisitor;
import org.sirix.node.NodeKind;
import org.sirix.node.SirixDeweyID;
import org.sirix.node.delegates.NameNodeDelegate;
import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.delegates.StructNodeDelegate;
import org.sirix.node.delegates.ValueNodeDelegate;
import org.sirix.node.immutable.xml.ImmutablePI;
import org.sirix.node.interfaces.NameNode;
import org.sirix.node.interfaces.Node;
import org.sirix.node.interfaces.ValueNode;
import org.sirix.node.interfaces.immutable.ImmutableXmlNode;
import org.sirix.settings.Constants;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Optional;

/**
 * <h1>PINode</h1>
 *
 * <p>
 * Node representing a processing instruction.
 * </p>
 */
public final class PINode extends AbstractStructForwardingNode implements ValueNode, NameNode, ImmutableXmlNode {

  /**
   * Delegate for name node information.
   */
  private final NameNodeDelegate nameNodeDelegate;

  /**
   * Delegate for val node information.
   */
  private final ValueNodeDelegate valueNodeDelegate;

  /**
   * Delegate for structural node information.
   */
  private final StructNodeDelegate structNodeDelegate;

  /**
   * {@link PageReadOnlyTrx} reference.
   */
  private final PageReadOnlyTrx pageReadOnlyTrx;

  private BigInteger hash;

  /**
   * Creating a processing instruction.
   *
   * @param structNodeDelegate {@link StructNodeDelegate} to be set
   * @param nameNodeDelegate   {@link NameNodeDelegate} to be set
   * @param valueNodeDelegate  {@link ValueNodeDelegate} to be set
   */
  public PINode(final BigInteger hashCode, final StructNodeDelegate structNodeDelegate,
      final NameNodeDelegate nameNodeDelegate, final ValueNodeDelegate valueNodeDelegate,
      final PageReadOnlyTrx pageReadOnlyTrx) {
    hash = hashCode;
    assert structNodeDelegate != null : "structDel must not be null!";
    this.structNodeDelegate = structNodeDelegate;
    assert nameNodeDelegate != null : "nameDel must not be null!";
    this.nameNodeDelegate = nameNodeDelegate;
    assert valueNodeDelegate != null : "valDel must not be null!";
    this.valueNodeDelegate = valueNodeDelegate;
    assert pageReadOnlyTrx != null : "pageReadTrx must not be null!";
    this.pageReadOnlyTrx = pageReadOnlyTrx;
  }

  /**
   * Creating a processing instruction.
   *
   * @param structNodeDelegate {@link StructNodeDelegate} to be set
   * @param nameNodeDelegate   {@link NameNodeDelegate} to be set
   * @param valueNodeDelegate  {@link ValueNodeDelegate} to be set
   */
  public PINode(final StructNodeDelegate structNodeDelegate, final NameNodeDelegate nameNodeDelegate,
      final ValueNodeDelegate valueNodeDelegate, final PageReadOnlyTrx pageReadOnlyTrx) {
    assert structNodeDelegate != null : "structDel must not be null!";
    this.structNodeDelegate = structNodeDelegate;
    assert nameNodeDelegate != null : "nameDel must not be null!";
    this.nameNodeDelegate = nameNodeDelegate;
    assert valueNodeDelegate != null : "valDel must not be null!";
    this.valueNodeDelegate = valueNodeDelegate;
    assert pageReadOnlyTrx != null : "pageReadTrx must not be null!";
    this.pageReadOnlyTrx = pageReadOnlyTrx;
  }

  @Override
  public NodeKind getKind() {
    return NodeKind.PROCESSING_INSTRUCTION;
  }

  @Override
  public BigInteger computeHash() {
    BigInteger result = BigInteger.ONE;

    result = BigInteger.valueOf(31).multiply(result).add(structNodeDelegate.getNodeDelegate().computeHash());
    result = BigInteger.valueOf(31).multiply(result).add(structNodeDelegate.computeHash());
    result = BigInteger.valueOf(31).multiply(result).add(nameNodeDelegate.computeHash());
    result = BigInteger.valueOf(31).multiply(result).add(valueNodeDelegate.computeHash());

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
  public VisitResult acceptVisitor(final XmlNodeVisitor visitor) {
    return visitor.visit(ImmutablePI.of(this));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("structDel", structNodeDelegate)
                      .add("nameDel", nameNodeDelegate)
                      .add("valDel", valueNodeDelegate)
                      .toString();
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
  public void setPrefixKey(final int prefixKey) {
    hash = null;
    nameNodeDelegate.setPrefixKey(prefixKey);
  }

  @Override
  public void setLocalNameKey(final int localNameKey) {
    hash = null;
    nameNodeDelegate.setLocalNameKey(localNameKey);
  }

  @Override
  public void setURIKey(final int uriKey) {
    hash = null;
    nameNodeDelegate.setURIKey(uriKey);
  }

  @Override
  public byte[] getRawValue() {
    return valueNodeDelegate.getRawValue();
  }

  @Override
  public void setValue(final byte[] value) {
    hash = null;
    valueNodeDelegate.setValue(value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(nameNodeDelegate, valueNodeDelegate);
  }

  @Override
  public boolean equals(final @Nullable Object obj) {
    if (obj instanceof PINode) {
      final PINode other = (PINode) obj;
      return Objects.equal(nameNodeDelegate, other.nameNodeDelegate) && Objects.equal(valueNodeDelegate,
          other.valueNodeDelegate);
    }
    return false;
  }

  @Override
  public void setPathNodeKey(final @Nonnegative long pathNodeKey) {
    hash = null;
    nameNodeDelegate.setPathNodeKey(pathNodeKey);
  }

  @Override
  public long getPathNodeKey() {
    return nameNodeDelegate.getPathNodeKey();
  }

  /**
   * Getting the inlying {@link NameNodeDelegate}.
   *
   * @return the {@link NameNodeDelegate} instance
   */
  public NameNodeDelegate getNameNodeDelegate() {
    return nameNodeDelegate;
  }

  /**
   * Getting the inlying {@link ValueNodeDelegate}.
   *
   * @return the {@link ValueNodeDelegate} instance
   */
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
  public QNm getName() {
    final String uri = pageReadOnlyTrx.getName(nameNodeDelegate.getURIKey(), NodeKind.NAMESPACE);
    final int prefixKey = nameNodeDelegate.getPrefixKey();
    final String prefix = prefixKey == -1 ? "" : pageReadOnlyTrx.getName(prefixKey, NodeKind.PROCESSING_INSTRUCTION);
    final int localNameKey = nameNodeDelegate.getLocalNameKey();
    final String localName =
        localNameKey == -1 ? "" : pageReadOnlyTrx.getName(localNameKey, NodeKind.PROCESSING_INSTRUCTION);
    return new QNm(uri, prefix, localName);
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
