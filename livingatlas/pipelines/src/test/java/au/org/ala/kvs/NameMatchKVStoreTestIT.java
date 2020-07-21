package au.org.ala.kvs;

import au.org.ala.kvs.cache.ALANameMatchKVStoreFactory;
import au.org.ala.names.ws.api.NameSearch;
import au.org.ala.names.ws.api.NameUsageMatch;
import au.org.ala.util.TestUtils;
import org.gbif.kvs.KeyValueStore;
import org.gbif.kvs.cache.KeyValueCache;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class NameMatchKVStoreTestIT {

  /**
   * Tests the Get operation on {@link KeyValueCache} that wraps a simple KV store backed by a
   * HashMap.
   */
  @Test
  public void getCacheTest() throws Exception {

    KeyValueStore<NameSearch, NameUsageMatch> kvs =
        ALANameMatchKVStoreFactory.create(TestUtils.getConfig());
    NameSearch req =
        NameSearch.builder().scientificName("Macropus rufus").build();
    NameUsageMatch match = kvs.get(req);
    assertNotNull(match.getTaxonConceptID());

    NameSearch req2 =
        NameSearch.builder().scientificName("Osphranter rufus").build();
    NameUsageMatch match2 = kvs.get(req2);
    assertNotNull(match2.getTaxonConceptID());

    kvs.close();
  }

  //    /**
  //     * Tests the Get operation on {@link KeyValueCache} that wraps a simple KV store backed by a
  // HashMap.
  //     */
  //    @Test
  //    public void getCacheFailTest() throws Exception {
  //
  //        ClientConfiguration cc = ClientConfiguration.builder()
  //                .withBaseApiUrl("http://localhostXXXXXX:9179") //GBIF base API url
  //                .withTimeOut(10000l) //Geocode service connection time-out
  //                .build();
  //        KeyValueStore<NameSearch, NameUsageMatch> kvs =
  // ALANameMatchKVStoreFactory.create(TestUtils.getConfig());
  //
  //        try {
  //            NameSearch req =
  // NameSearch.builder().scientificName("Macropus rufus").build();
  //            NameUsageMatch match = kvs.get(req);
  //            fail("Exception should be thrown");
  //        } catch (RuntimeException e){
  //            //expected
  //        }
  //    }
}
