package org.gbif.pipelines.core.interpretation;

import org.gbif.pipelines.core.utils.ExtendedRecordCustomBuilder;
import org.gbif.pipelines.core.ws.BaseMockServerTest;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.Rank;
import org.gbif.pipelines.io.avro.RankedName;
import org.gbif.pipelines.io.avro.TaxonRecord;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

/** Tests the {@link TaxonomyInterpreter}. */
public class TaxonomyInterpreterTest extends BaseMockServerTest {

  private static final String TEST_RECORD_ID = "testId";

  /**
   * Call mocked:
   * https://api.gbif-uat.org/v1/species/match2?kingdom=Animalia&genus=Puma&rank=SPECIES&name=Puma%20concolor&strict=false&verbose=false
   */
  @Test
  public void assembledAuthorTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Animalia")
            .genus("Puma")
            .name("Puma concolor")
            .authorship("")
            .rank(Rank.SPECIES.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(PUMA_CONCOLOR_RESPONSE);
    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);
    Map<Rank, RankedName> ranksResponse =
        taxonRecord
            .getClassification()
            .stream()
            .collect(Collectors.toMap(RankedName::getRank, rankedName -> rankedName));

    // Should
    Assert.assertEquals(2435099, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(1, ranksResponse.get(Rank.KINGDOM).getKey().intValue());
    Assert.assertEquals("Chordata", ranksResponse.get(Rank.PHYLUM).getName());
  }

  /**
   * Call mocked:
   * http://api.gbif-uat.org/v1/species/match2?kingdom=Animalia&genus=Puma&rank=SPECIES&name=Puma%20concolor%20(Linnaeus,%201771)&strict=false&verbose=false
   */
  @Test
  public void assembledAuthorRankTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Animalia")
            .genus("Puma")
            .name("Puma concolor (Linnaeus, 1771)")
            .rank(Rank.SPECIES.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(PUMA_CONCOLOR_2_RESPONSE);
    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);
    Map<Rank, RankedName> ranksResponse =
        taxonRecord
            .getClassification()
            .stream()
            .collect(Collectors.toMap(RankedName::getRank, rankedName -> rankedName));

    // Should
    Assert.assertEquals(2435099, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(1, ranksResponse.get(Rank.KINGDOM).getKey().intValue());
    Assert.assertEquals("Chordata", ranksResponse.get(Rank.PHYLUM).getName());
  }

  /**
   * Call mocked:
   * http://api.gbif-uat.org/v1/species/match2?kingdom=Animalia&genus=Puma&rank=SPECIES&name=Puma%20concolor%20(Linnaeus,%201771)&strict=false&verbose=false
   */
  @Test
  public void assembledAuthorRankAuthorshipTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Animalia")
            .genus("Puma")
            .name("Puma concolor")
            .authorship("(Linnaeus, 1771)")
            .rank(Rank.SPECIES.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(PUMA_CONCOLOR_3_RESPONSE);
    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);
    Map<Rank, RankedName> ranksResponse =
        taxonRecord
            .getClassification()
            .stream()
            .collect(Collectors.toMap(RankedName::getRank, rankedName -> rankedName));

    Assert.assertEquals(2435099, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(1, ranksResponse.get(Rank.KINGDOM).getKey().intValue());
    Assert.assertEquals("Chordata", ranksResponse.get(Rank.PHYLUM).getName());
  }

  /**
   * Call mocked:
   * https://api.gbif-uat.org/v1/species/match2?kingdom=Plantae&rank=GENUS&name=Oenanthe&strict=false&verbose=false
   */
  @Test
  public void oenantheTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Plantae")
            .name("Oenanthe")
            .authorship("")
            .rank(Rank.GENUS.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(OENANTHE_RESPONSE);
    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);
    Map<Rank, RankedName> ranksResponse =
        taxonRecord
            .getClassification()
            .stream()
            .collect(Collectors.toMap(RankedName::getRank, rankedName -> rankedName));

    // Should
    Assert.assertEquals(3034893, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(6, ranksResponse.get(Rank.KINGDOM).getKey().intValue());
    Assert.assertEquals("Oenanthe L.", taxonRecord.getUsage().getName());
  }

  /**
   * Call mocked:
   * https://api.gbif-uat.org/v1/species/match2?kingdom=Plantae&rank=GENUS&name=Oenanthe%20L.&strict=false&verbose=false
   */
  @Test
  public void oenantheAuthorshipTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Plantae")
            .name("Oenanthe")
            .authorship("L.")
            .rank(Rank.GENUS.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(OENANTHE_2_RESPONSE);
    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);
    Map<Rank, RankedName> ranksResponse =
        taxonRecord
            .getClassification()
            .stream()
            .collect(Collectors.toMap(RankedName::getRank, rankedName -> rankedName));

    Assert.assertEquals(3034893, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(6, ranksResponse.get(Rank.KINGDOM).getKey().intValue());
    Assert.assertEquals("Oenanthe L.", taxonRecord.getUsage().getName());
  }

  /**
   * Call mocked:
   * https://api.gbif-uat.org/v1/species/match2?kingdom=Animalia&rank=GENUS&name=Oenanthe%20Vieillot,%201816&strict=false&verbose=false
   */
  @Test
  public void testOenantheRankAuthorshipTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Animalia")
            .name("Oenanthe")
            .authorship("Vieillot, 1816")
            .rank(Rank.GENUS.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(OENANTHE_3_RESPONSE);

    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);
    Map<Rank, RankedName> ranksResponse =
        taxonRecord
            .getClassification()
            .stream()
            .collect(Collectors.toMap(RankedName::getRank, rankedName -> rankedName));

    // Should
    Assert.assertEquals(2492483, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(1, ranksResponse.get(Rank.KINGDOM).getKey().intValue());
    Assert.assertEquals("Oenanthe Vieillot, 1816", taxonRecord.getUsage().getName());
  }

  /**
   * Call mocked:
   * https://api.gbif-uat.org/v1/species/match2?kingdom=Animalia&phylum=Annelida&family=Lumbricidae&rank=SPECIES&name=Bold:acv7160&strict=false&verbose=false
   */
  @Test
  public void otuTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Animalia")
            .phylum("Annelida")
            .family("Lumbricidae")
            .name("BOLD:ACV7160")
            .rank(Rank.SPECIES.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(ANNELIDA_RESPONSE);
    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);

    // Should
    Assert.assertEquals("Lumbricidae", taxonRecord.getUsage().getName());
  }

  /**
   * Call mocked:
   * https://api.gbif-uat.org/v1/species/match2?kingdom=Chromista&phylum=Dinophyta&class=Dinophyceae&order=Peridiniales&family=Ceratiaceae&genus=Ceratium&rank=SPECIES&name=Ceratium%20hirundinella&strict=false&verbose=false
   */
  @Test
  public void ceratiaceaeTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Chromista")
            .phylum("Dinophyta")
            .clazz("Dinophyceae")
            .order("Peridiniales")
            .family("Ceratiaceae")
            .genus("Ceratium")
            .name("Ceratium hirundinella")
            .rank(Rank.SPECIES.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(CERATIACEAE_RESPONSE);
    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);
    Map<Rank, RankedName> ranksResponse =
        taxonRecord
            .getClassification()
            .stream()
            .collect(Collectors.toMap(RankedName::getRank, rankedName -> rankedName));

    // Should
    Assert.assertEquals(7598904, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(7479242, ranksResponse.get(Rank.FAMILY).getKey().intValue());
    Assert.assertEquals(
        "Ceratium hirundinella (O.F.Müll.) Dujard.", taxonRecord.getUsage().getName());
  }

  /**
   * Call mocked:
   * https://api.gbif-uat.org/v1/species/match2?kingdom=Animalia&genus=Puma&rank=SPECIES&name=Puma%20concolor&strict=false&verbose=false
   */
  @Test
  public void nubLookupGoodTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .kingdom("Animalia")
            .genus("Puma")
            .name("Puma concolor")
            .rank(Rank.SPECIES.name())
            .id(TEST_RECORD_ID)
            .build();

    enqueueResponse(PUMA_CONCOLOR_RESPONSE);
    TaxonRecord taxonRecord = createTaxonRecord();

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);
    Map<Rank, RankedName> ranksResponse =
        taxonRecord
            .getClassification()
            .stream()
            .collect(Collectors.toMap(RankedName::getRank, rankedName -> rankedName));

    // Should
    Assert.assertEquals(2435099, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(1, ranksResponse.get(Rank.KINGDOM).getKey().intValue());
    Assert.assertEquals("Chordata", ranksResponse.get(Rank.PHYLUM).getName());
  }

  /**
   * Calls
   * https://api.gbif-uat.org/v1/species/match2?rank=GENUS&name=Agallisus%20lepturoides&strict=false&verbose=false
   */
  @Test
  public void acceptedUsageTest() throws IOException {

    // State
    ExtendedRecord record =
        ExtendedRecordCustomBuilder.create()
            .name("Agallisus lepturoides")
            .id(TEST_RECORD_ID)
            .build();
    TaxonRecord taxonRecord = createTaxonRecord();

    enqueueResponse(AGALLISUS_LEPTUROIDES_RESPONSE);

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, taxonRecord);

    // Should
    Assert.assertEquals(1118030, taxonRecord.getUsage().getKey().intValue());
    Assert.assertEquals(1118026, taxonRecord.getAcceptedUsage().getKey().intValue());
    Assert.assertEquals(
        "Agallisus lepturoides Hovore, Penrose & Neck, 1987", taxonRecord.getUsage().getName());
    Assert.assertEquals(
        "Agallissus lepturoides (Chevrolat, 1849)", taxonRecord.getAcceptedUsage().getName());
  }

  @Test(expected = NullPointerException.class)
  public void matchNullTaxonRecordTest() {

    // When
    TaxonomyInterpreter.taxonomyInterpreter(null).accept(null, null);
  }

  @Test(expected = NullPointerException.class)
  public void matchNullArgsTest() {

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(null, null);

    // Should
    Assert.fail("This line should not be reached ");
  }

  @Test(expected = IllegalArgumentException.class)
  public void matchEmptyArgsTest() {

    // State
    ExtendedRecord record = new ExtendedRecord();
    record.setCoreTerms(new HashMap<>());

    // When
    TaxonomyInterpreter.taxonomyInterpreter(getWsConfig()).accept(record, createTaxonRecord());

    // Should
    Assert.fail("This line should not be reached ");
  }

  private TaxonRecord createTaxonRecord() {
    return TaxonRecord.newBuilder().build();
  }
}
