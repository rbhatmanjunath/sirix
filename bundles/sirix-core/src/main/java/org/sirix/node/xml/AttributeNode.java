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

package org.sirix.node.xml;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.hash.HashCode;
import org.brackit.xquery.atomic.QNm;
import org.sirix.api.visitor.VisitResult;
import org.sirix.api.visitor.XmlNodeVisitor;
import org.sirix.node.AbstractForwardingNode;
import org.sirix.node.NodeKind;
import org.sirix.node.SirixDeweyID;
import org.sirix.node.delegates.NameNodeDelegate;
import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.delegates.StructNodeDelegate;
import org.sirix.node.delegates.ValueNodeDelegate;
import org.sirix.node.immutable.xml.ImmutableAttributeNode;
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
 * Node representing an attribute.
 */
public final class AttributeNode extends AbstractForwardingNode implements ValueNode, NameNode, ImmutableXmlNode {

  /** Delegate for name node information. */
  private final NameNodeDelegate nameDelegate;

  /** Delegate for val node information. */
  private final ValueNodeDelegate valueDelegate;

  /** Node delegate. */
  private final NodeDelegate nodeDelegate;

  /** The qualified name. */
  private final QNm name;

  private BigInteger hash;

  /**
   * Creating an attribute.
   *
   * @param nodeDelegate {@link NodeDelegate} to be set
   * @param nodeDelegate {@link StructNodeDelegate} to be set
   * @param valueNodeDelegate {@link ValueNodeDelegate} to be set
   */
  public AttributeNode(final NodeDelegate nodeDelegate, final NameNodeDelegate nameNodeDelegate, final ValueNodeDelegate valueNodeDelegate,
      final QNm name) {
    assert nodeDelegate != null : "nodeDel must not be null!";
    this.nodeDelegate = nodeDelegate;
    assert nameNodeDelegate != null : "nameDel must not be null!";
    nameDelegate = nameNodeDelegate;
    assert valueNodeDelegate != null : "valDel must not be null!";
    valueDelegate = valueNodeDelegate;
    assert name != null : "qNm must not be null!";
    this.name = name;
  }

  /**
   * Creating an attribute.
   *
   * @param nodeDelegate {@link NodeDelegate} to be set
   * @param nodeDelegate {@link StructNodeDelegate} to be set
   * @param valueNodeDelegate {@link ValueNodeDelegate} to be set
   */
  public AttributeNode(final BigInteger hashCode, final NodeDelegate nodeDelegate, final NameNodeDelegate nameNodeDelegate,
      final ValueNodeDelegate valueNodeDelegate, final QNm name) {
    hash = hashCode;
    assert nodeDelegate != null : "nodeDel must not be null!";
    this.nodeDelegate = nodeDelegate;
    assert nameNodeDelegate != null : "nameDel must not be null!";
    nameDelegate = nameNodeDelegate;
    assert valueNodeDelegate != null : "valDel must not be null!";
    valueDelegate = valueNodeDelegate;
    assert name != null : "qNm must not be null!";
    this.name = name;
  }

  @Override
  public NodeKind getKind() {
    return NodeKind.ATTRIBUTE;
  }

  @Override
  public BigInteger computeHash() {
    final HashCode valueHashCode = nodeDelegate.getHashFunction().hashBytes(getRawValue());

    final BigInteger valueBigInteger = new BigInteger(1, valueHashCode.asBytes());

    BigInteger result = BigInteger.ONE;

    result = BigInteger.valueOf(31).multiply(result).add(nodeDelegate.computeHash());
    result = BigInteger.valueOf(31).multiply(result).add(nameDelegate.computeHash());
    result = BigInteger.valueOf(31).multiply(result).add(valueBigInteger);

    return Node.to128BitsAtMaximumBigInteger(result);
  }

  @Override
  public void setHash(BigInteger hash) {
    this.hash = Node.to128BitsAtMaximumBigInteger(hash);
  }

  @Override
  public BigInteger getHash() {
    return hash;
  }

  @Override
  public VisitResult acceptVisitor(final XmlNodeVisitor visitor) {
    return visitor.visit(ImmutableAttributeNode.of(this));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("nameDel", nameDelegate).add("valDel", valueDelegate).toString();
  }

  @Override
  public int getPrefixKey() {
    return nameDelegate.getPrefixKey();
  }

  @Override
  public int getLocalNameKey() {
    return nameDelegate.getLocalNameKey();
  }

  @Override
  public int getURIKey() {
    return nameDelegate.getURIKey();
  }

  @Override
  public void setPrefixKey(final int prefixKey) {
    nameDelegate.setPrefixKey(prefixKey);
  }

  @Override
  public void setLocalNameKey(final int localNameKey) {
    nameDelegate.setLocalNameKey(localNameKey);
  }

  @Override
  public void setURIKey(final int uriKey) {
    nameDelegate.setURIKey(uriKey);
  }

  @Override
  public byte[] getRawValue() {
    return valueDelegate.getRawValue();
  }

  @Override
  public void setValue(final byte[] value) {
    valueDelegate.setValue(value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(nameDelegate, valueDelegate);
  }

  @Override
  public boolean equals(final @Nullable Object obj) {
    if (obj instanceof AttributeNode) {
      final AttributeNode other = (AttributeNode) obj;
      return Objects.equal(nameDelegate, other.nameDelegate) && Objects.equal(valueDelegate, other.valueDelegate);
    }
    return false;
  }

  @Override
  public void setPathNodeKey(final @Nonnegative long pathNodeKey) {
    nameDelegate.setPathNodeKey(pathNodeKey);
  }

  @Override
  public long getPathNodeKey() {
    return nameDelegate.getPathNodeKey();
  }

  /**
   * Getting the inlying {@link NameNodeDelegate}.
   *
   * @return the {@link NameNodeDelegate} instance
   */
  public NameNodeDelegate getNameNodeDelegate() {
    return nameDelegate;
  }

  /**
   * Getting the inlying {@link ValueNodeDelegate}.
   *
   * @return the {@link ValueNodeDelegate} instance
   */
  public ValueNodeDelegate getValNodeDelegate() {
    return valueDelegate;
  }

  @Override
  protected NodeDelegate delegate() {
    return nodeDelegate;
  }

  @Override
  public QNm getName() {
    return name;
  }

  @Override
  public String getValue() {
    return new String(valueDelegate.getRawValue(), Constants.DEFAULT_ENCODING);
  }

  @Override
  public Optional<SirixDeweyID> getDeweyID() {
    return nodeDelegate.getDeweyID();
  }

  @Override
  public int getTypeKey() {
    return nodeDelegate.getTypeKey();
  }
}
