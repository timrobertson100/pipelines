package au.org.ala.kvs.cache;

import au.org.ala.kvs.ALAPipelinesConfig;
import au.org.ala.kvs.client.*;
import au.org.ala.kvs.client.retrofit.ALACollectoryServiceClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.gbif.kvs.KeyValueStore;
import org.gbif.kvs.cache.KeyValueCache;
import org.gbif.kvs.hbase.Command;
import org.gbif.pipelines.core.functions.SerializableSupplier;

/** Key value store factory for Attribution */
@Slf4j
public class ALAAttributionKVStoreFactory {

  private final KeyValueStore<String, ALACollectoryMetadata> kvStore;
  private static volatile ALAAttributionKVStoreFactory instance;
  private static final Object MUTEX = new Object();

  @SneakyThrows
  private ALAAttributionKVStoreFactory(ALAPipelinesConfig config) {
    this.kvStore = create(config);
  }

  public static KeyValueStore<String, ALACollectoryMetadata> getInstance(
      ALAPipelinesConfig config) {
    if (instance == null) {
      synchronized (MUTEX) {
        if (instance == null) {
          instance = new ALAAttributionKVStoreFactory(config);
        }
      }
    }
    return instance.kvStore;
  }

  /** Retrieve KV Store for Collectory Metadata. */
  public static KeyValueStore<String, ALACollectoryMetadata> create(ALAPipelinesConfig config) {
    ALACollectoryServiceClient wsClient = new ALACollectoryServiceClient(config.getCollectory());
    Command closeHandler =
        () -> {
          try {
            wsClient.close();
          } catch (Exception e) {
            log.error("Unable to close", e);
          }
        };

    return cache2kBackedKVStore(wsClient, closeHandler, config);
  }

  /** Builds a KV Store backed by the rest client. */
  private static KeyValueStore<String, ALACollectoryMetadata> cache2kBackedKVStore(
      ALACollectoryService service, Command closeHandler, ALAPipelinesConfig config) {

    KeyValueStore<String, ALACollectoryMetadata> kvs =
        new KeyValueStore<String, ALACollectoryMetadata>() {
          @Override
          public ALACollectoryMetadata get(String key) {
            try {
              return service.lookupDataResource(key);
            } catch (org.gbif.rest.client.retrofit.RestClientException ex) {
              throw logAndThrow(ex, "Unable to connect to rest service");
            } catch (retrofit2.HttpException ex2) {
              throw logAndThrow(ex2, "Unable to connect to http service");
            } catch (Exception ex) {
              throw logAndThrow(
                  ex,
                  "Error contacting the collectory service to retrieve data resource metadata. Has resource been removed ? "
                      + key);
            }
          }

          @Override
          public void close() {
            closeHandler.execute();
          }
        };
    return KeyValueCache.cache(
        kvs, config.getCollectory().getCacheSizeMb(), String.class, ALACollectoryMetadata.class);
  }

  public static SerializableSupplier<KeyValueStore<String, ALACollectoryMetadata>>
      getInstanceSupplier(ALAPipelinesConfig config) {
    return () -> ALAAttributionKVStoreFactory.getInstance(config);
  }

  /**
   * Wraps an exception into a {@link RuntimeException}.
   *
   * @param throwable to propagate
   * @param message to log and use for the exception wrapper
   * @return a new {@link RuntimeException}
   */
  private static RuntimeException logAndThrow(Throwable throwable, String message) {
    log.error(message, throwable);
    return new RuntimeException(throwable);
  }
}
