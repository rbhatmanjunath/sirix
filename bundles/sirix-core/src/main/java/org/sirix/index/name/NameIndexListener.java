package org.sirix.index.name;

import org.brackit.xquery.atomic.QNm;
import org.sirix.access.trx.node.xml.XmlIndexController.ChangeType;
import org.sirix.index.SearchMode;
import org.sirix.index.avltree.AVLTreeReader.MoveCursor;
import org.sirix.index.avltree.AVLTreeWriter;
import org.sirix.index.avltree.keyvalue.NodeReferences;
import org.sirix.node.interfaces.immutable.ImmutableNode;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.Set;

public final class NameIndexListener {

  private final Set<QNm> includes;
  private final Set<QNm> excludes;
  private final AVLTreeWriter<QNm, NodeReferences> avlTreeWriter;

  public NameIndexListener(final Set<QNm> includes, final Set<QNm> excludes,
      final AVLTreeWriter<QNm, NodeReferences> avlTreeWriter) {
    this.includes = includes;
    this.excludes = excludes;
    this.avlTreeWriter = avlTreeWriter;
  }

  public void listen(ChangeType type, @Nonnull ImmutableNode node, QNm name) {
    final boolean included = (includes.isEmpty() || includes.contains(name));
    final boolean excluded = (!excludes.isEmpty() && excludes.contains(name));

    if (!included || excluded) {
      return;
    }

    switch (type) {
      case INSERT:
        final Optional<NodeReferences> textReferences = avlTreeWriter.get(name, SearchMode.EQUAL);
        if (textReferences.isPresent()) {
          setNodeReferences(node, textReferences.get(), name);
        } else {
          setNodeReferences(node, new NodeReferences(), name);
        }
        break;
      case DELETE:
        avlTreeWriter.remove(name, node.getNodeKey());
        break;
      default:
    }
  }

  private void setNodeReferences(final ImmutableNode node, final NodeReferences references, final QNm name) {
    avlTreeWriter.index(name, references.addNodeKey(node.getNodeKey()), MoveCursor.NO_MOVE);
  }

}
