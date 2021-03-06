package org.gbif.pipelines.core.factory;

import static java.util.Objects.requireNonNull;

import java.io.FileNotFoundException;
import java.io.InputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.gbif.pipelines.core.config.model.PipelinesConfig;
import org.gbif.pipelines.core.config.model.VocabularyConfig;
import org.gbif.pipelines.core.functions.SerializableSupplier;
import org.gbif.pipelines.core.utils.FsUtils;
import org.gbif.vocabulary.lookup.PreFilters;
import org.gbif.vocabulary.lookup.VocabularyLookup;

/**
 * Factory to create instances of {@link VocabularyLookup} from a file containing an exported
 * vocabulary.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileVocabularyFactory {

  /**
   * Creates instances of {@link VocabularyLookup} from a file containing an exported vocabulary.
   *
   * <p>The lookups for some terms are customized to apply certain filters before performing the
   * lookup:
   *
   * <ul>
   *   <li>LifeStage uses a {@link PreFilters#REMOVE_NUMERIC_PREFIX}. This filter removes all the
   *       number characters that are present at the beginning of the value.
   * </ul>
   *
   * @param config pipelines config that contains specif config for the vocabularies
   * @param hdfsSiteConfig HDFS site config file
   * @param coreSiteConfig HDFS core site config file
   * @param vocabularyBackedTerm term that we are creating the vocabulary lookup instance for
   * @return {@link SerializableSupplier} parameterized for {@link VocabularyLookup}
   */
  public static SerializableSupplier<VocabularyLookup> getInstanceSupplier(
      PipelinesConfig config,
      String hdfsSiteConfig,
      String coreSiteConfig,
      VocabularyBackedTerm vocabularyBackedTerm) {
    return () -> {
      VocabularyConfig vocabularyConfig = requireNonNull(config.getVocabularyConfig());

      if (vocabularyBackedTerm == VocabularyBackedTerm.LIFE_STAGE) {
        return VocabularyLookup.newBuilder()
            .from(
                readVocabularyFile(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    vocabularyConfig.getVocabulariesPath(),
                    vocabularyConfig.getLifeStageVocabName()))
            .withPrefilter(PreFilters.REMOVE_NUMERIC_PREFIX)
            .build();
      }

      throw new IllegalArgumentException(
          "Vocabulary-backed term not supported: " + vocabularyBackedTerm);
    };
  }

  /**
   * Reads a vocabulary file from HDFS/Local FS
   *
   * @param hdfsSiteConfig HDFS site config file
   * @param coreSiteConfig HDFS core site config file
   * @param vocabulariesDir dir where the vocabulary files are
   * @param vocabularyName name of the vocabulary. It has to be the same as the one used in the file
   *     name.
   * @return {@link InputStream}
   */
  @SneakyThrows
  private static InputStream readVocabularyFile(
      String hdfsSiteConfig, String coreSiteConfig, String vocabulariesDir, String vocabularyName) {
    FileSystem fs = FsUtils.getFileSystem(hdfsSiteConfig, coreSiteConfig, vocabulariesDir);
    Path fPath = new Path(String.join(Path.SEPARATOR, vocabulariesDir, vocabularyName + ".json"));
    if (fs.exists(fPath)) {
      log.info("Reading vocabularies path - {}", fPath);
      return fs.open(fPath);
    }

    throw new FileNotFoundException("The vocabulary file doesn't exist - " + fPath);
  }

  /** Enum with the terms that are backed by a vocabulary. */
  public enum VocabularyBackedTerm {
    LIFE_STAGE;
  }
}
