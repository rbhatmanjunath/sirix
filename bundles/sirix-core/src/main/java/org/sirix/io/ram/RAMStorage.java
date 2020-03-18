package org.sirix.io.ram;

import org.sirix.access.ResourceConfiguration;
import org.sirix.api.PageReadOnlyTrx;
import org.sirix.exception.SirixIOException;
import org.sirix.io.Reader;
import org.sirix.io.Storage;
import org.sirix.io.Writer;
import org.sirix.io.bytepipe.ByteHandlePipeline;
import org.sirix.page.PageReference;
import org.sirix.page.RevisionRootPage;
import org.sirix.page.UberPage;
import org.sirix.page.interfaces.Page;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In memory storage.
 *
 * @author Johannes Lichtenberger
 *
 */
public final class RAMStorage implements Storage {

  /** Storage, mapping a resource to the pageKey/page mapping. */
  private final ConcurrentMap<String, ConcurrentMap<Long, Page>> dataStorage;

  /** Storage, mapping a resource to the revision/revision root page mapping. */
  private final ConcurrentMap<String, ConcurrentMap<Integer, RevisionRootPage>> revisionRootsStorage;

  /** Mapping pageKey to the page. */
  private ConcurrentMap<Long, Page> resourceFileStorage;

  /** Mapping revision to the page. */
  private ConcurrentMap<Integer, RevisionRootPage> resourceRevisionRootsStorage;

  /** The uber page key. */
  private final ConcurrentMap<Integer, Long> uberPageKey;

  /** {@link ByteHandlePipeline} reference. */
  private final ByteHandlePipeline byteHandlerPipeline;

  /** {@link RAMAccess} reference. */
  private final RAMAccess access;

  /** Determines if the storage already exists or not. */
  private boolean doesExist;

  /** The unique page key. */
  private long pageKey;

  /** The resource configuration. */
  private final ResourceConfiguration resourceConfiguration;

  /**
   * Constructor
   *
   * @param resourceConfig {@link ResourceConfiguration} reference
   */
  public RAMStorage(final ResourceConfiguration resourceConfig) {
    resourceConfiguration = resourceConfig;
    dataStorage = new ConcurrentHashMap<>();
    revisionRootsStorage = new ConcurrentHashMap<>();
    byteHandlerPipeline = resourceConfig.byteHandlePipeline;
    access = new RAMAccess();
    uberPageKey = new ConcurrentHashMap<>();
    uberPageKey.put(-1, 0L);
  }

  @Override
  public Writer createWriter() {
    instantiate();

    return access;
  }

  private void instantiate() {
    final String resource = resourceConfiguration.getResource().getFileName().toString();
    doesExist = dataStorage.containsKey(resource);
    dataStorage.putIfAbsent(resource, new ConcurrentHashMap<>());
    resourceFileStorage = dataStorage.get(resource);
    revisionRootsStorage.putIfAbsent(resource, new ConcurrentHashMap<>());
    resourceRevisionRootsStorage = revisionRootsStorage.get(resource);
  }

  @Override
  public Reader createReader() {
    instantiate();

    return access;
  }

  @Override
  public void close() {}

  @Override
  public ByteHandlePipeline getByteHandler() {
    return byteHandlerPipeline;
  }

  @Override
  public boolean exists() throws SirixIOException {
    return doesExist;
  }

  /** Provides RAM access. */
  public class RAMAccess implements Writer {

    @Override
    public Writer truncate() {
      uberPageKey.clear();
      resourceFileStorage.clear();
      doesExist = false;
      return this;
    }

    @Override
    public Page read(PageReference reference, @Nullable PageReadOnlyTrx pageReadTrx) {
      return resourceFileStorage.get(reference.getKey());
    }

    @Override
    public PageReference readUberPageReference() {
      final Page page = resourceFileStorage.get(uberPageKey.get(-1));
      final PageReference uberPageReference = new PageReference();
      uberPageReference.setKey(-1);
      uberPageReference.setPage(page);
      return uberPageReference;
    }

    @Override
    public Writer write(final PageReference pageReference) throws SirixIOException {
      final Page page = pageReference.getPage();
      pageReference.setKey(pageKey);
      resourceFileStorage.put(pageKey++, page);
      doesExist = true;
      return this;
    }

    @Override
    public Writer writeUberPageReference(final PageReference pageReference) throws SirixIOException {
      final Page page = pageReference.getPage();
      pageReference.setKey(pageKey);
      resourceFileStorage.put(pageKey, page);
      uberPageKey.put(-1, pageKey++);
      doesExist = true;
      return this;
    }

    @Override
    public void close() throws SirixIOException {}

    @Override
    public Writer truncateTo(int revision) {
      PageReference uberPageReference = readUberPageReference();
      UberPage uberPage = (UberPage) uberPageReference.getPage();

      while (uberPage.getRevisionNumber() != revision) {
        resourceFileStorage.remove(uberPageReference.getKey());
        final Long previousUberPageKey = uberPage.getPreviousUberPageKey();
        uberPage = (UberPage) read(new PageReference().setKey(previousUberPageKey), null);
        uberPageReference = new PageReference();
        uberPageReference.setKey(previousUberPageKey);

        if (uberPage.getRevisionNumber() == revision) {
          resourceFileStorage.put(previousUberPageKey, uberPage);
          uberPageKey.put(-1, previousUberPageKey);
          break;
        }
      }

      return this;
    }

    @Override
    public RevisionRootPage readRevisionRootPage(int revision, PageReadOnlyTrx pageReadTrx) {
      return resourceRevisionRootsStorage.get(revision);
    }
  }
}
