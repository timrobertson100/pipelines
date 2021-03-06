package au.org.ala.utils;

import static au.org.ala.pipelines.beam.ALAUUIDMintingPipeline.UNIQUE_COMPOSITE_KEY_JOIN_CHAR;

import au.org.ala.pipelines.options.ALASolrPipelineOptions;
import au.org.ala.pipelines.options.UUIDPipelineOptions;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.gbif.dwc.terms.Term;
import org.gbif.pipelines.common.beam.options.InterpretationPipelineOptions;
import org.gbif.pipelines.common.beam.utils.PathBuilder;
import org.gbif.pipelines.core.factory.FileSystemFactory;
import org.gbif.pipelines.core.utils.ModelUtils;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ValidationUtils {

  public static final String METADATA_NOT_AVAILABLE = "NOT_AVAILABLE";
  public static final String UNIQUE_TERMS_NOT_SPECIFIED = "TERMS_NOT_SET";
  public static final String NO_VERBATIM = "NO_VERBATIM";
  public static final String NOT_INTERPRET = "NOT_INTERPRET";
  public static final String NOT_VALIDATED = "NOT_VALIDATED";
  public static final String UUID_REQUIRED = "UUID_REQUIRED";
  public static final String NOT_INDEXED = "NOT_INDEXED";
  public static final String HAS_EMPTY_KEYS = "HAS_EMPTY_KEYS";
  public static final String HAS_DUPLICATES = "HAS_DUPLICATES";

  public static final String IMAGE_SERVICE_METRICS = "image-service-metrics.yml";
  public static final String UUID_METRICS = "uuid-metrics.yml";
  public static final String INTERPRETATION_METRICS = "interpretation-metrics.yml";
  public static final String VERBATIM_METRICS = "dwca-metrics.yml";
  public static final String INDEXING_METRICS = "indexing-metrics.yml";
  public static final String SENSITIVE_METRICS = "sensitive-metrics.yml";
  public static final String JACKKNIFE_METRICS = "jackknife-metrics.yml";

  public static final String DUPLICATE_KEY_COUNT = "duplicateKeyCount";
  public static final String EMPTY_KEY_RECORDS = "emptyKeyRecords";
  public static final String DUPLICATE_RECORD_KEY_COUNT = "duplicateRecordKeyCount";
  public static final String VALIDATION_REPORT_FILE = "validation-report.yaml";
  public static final String VALIDATION_OUTPUT_DIR = "validation";
  public static final String DUPLICATE_KEYS_OUTPUT = "duplicateKeys.csv";
  public static final String UNIQUE_TERMS_SPECIFIED = "uniqueTermsSpecified";
  public static final String METADATA_AVAILABLE = "metadataAvailable";

  /** Checks a dataset can be indexed. */
  public static ValidationResult checkReadyForIndexing(ALASolrPipelineOptions options) {

    ValidationResult isValid = checkValidationFile(options);
    if (!isValid.getValid()) {
      return isValid;
    }

    FileSystem fs =
        FileSystemFactory.getInstance(options.getHdfsSiteConfig(), options.getCoreSiteConfig())
            .getFs(options.getInputPath());

    return checkReadyForIndexing(
        fs, options.getInputPath(), options.getDatasetId(), options.getAttempt());
  }

  /** Checks a dataset can be indexed. */
  public static ValidationResult checkReadyForIndexing(
      FileSystem fs, String filePath, String datasetId, Integer attempt) {

    ValidationResult isValid = checkValidationFile(fs, filePath, datasetId, attempt);

    if (!isValid.getValid()) {
      return isValid;
    }

    // check date on DwCA?
    long verbatimTime = metricsModificationTime(fs, filePath, datasetId, attempt, VERBATIM_METRICS);
    // check date on Interpretation?
    long interpretationTime =
        metricsModificationTime(fs, filePath, datasetId, attempt, INTERPRETATION_METRICS);
    // check UUID date
    long uuidTime = metricsModificationTime(fs, filePath, datasetId, attempt, UUID_METRICS);

    if (interpretationTime < verbatimTime) {
      log.warn(
          "The imported verbatim is newer than the interpretation. Interpretation should be re-ran.");
    }
    if (interpretationTime > uuidTime) {
      log.warn(
          "The imported interpretation is newer than the uuid. Unable to index until UUID minting re-ran");
      return ValidationResult.builder().valid(false).message(UUID_REQUIRED).build();
    }

    return ValidationResult.OK;
  }

  /**
   * Checks the content of the validate file, returning true if the UUID content has been checked
   * and is thought to be valid.
   */
  public static ValidationResult checkValidationFile(InterpretationPipelineOptions options) {

    FileSystem fs =
        FileSystemFactory.getInstance(options.getHdfsSiteConfig(), options.getCoreSiteConfig())
            .getFs(options.getInputPath());

    return checkValidationFile(
        fs, options.getInputPath(), options.getDatasetId(), options.getAttempt());
  }

  @SneakyThrows
  public static ValidationResult checkValidationFile(
      FileSystem fs, String inputPath, String datasetId, Integer attempt) {

    String validateFilePath = getValidationFilePath(inputPath, datasetId, attempt);
    Path metrics = new Path(validateFilePath);

    if (fs.exists(metrics)) {
      // read YAML
      Yaml yaml = new Yaml();
      // the YAML files created by metrics are UTF-16 encoded
      Map<String, Object> yamlObject =
          yaml.load(new InputStreamReader(fs.open(metrics), StandardCharsets.UTF_8));

      // check metadata available
      boolean metadataAvailable =
          Boolean.parseBoolean(yamlObject.getOrDefault(METADATA_AVAILABLE, "false").toString());
      if (!metadataAvailable) {
        return ValidationResult.builder().valid(false).message(METADATA_NOT_AVAILABLE).build();
      }

      // check unique terms
      boolean uniqueTermsSpecified =
          Boolean.parseBoolean(yamlObject.getOrDefault(UNIQUE_TERMS_SPECIFIED, "false").toString());
      if (!uniqueTermsSpecified) {
        return ValidationResult.builder().valid(false).message(UNIQUE_TERMS_NOT_SPECIFIED).build();
      }

      // check invalid record count
      long emptyKeyRecords =
          Long.parseLong(yamlObject.getOrDefault(EMPTY_KEY_RECORDS, -1L).toString());

      if (emptyKeyRecords > 0) {
        log.warn(
            "The number of records with empty values for all unique terms: " + emptyKeyRecords);
      }

      if (emptyKeyRecords != 0) {
        return ValidationResult.builder().valid(false).message(HAS_EMPTY_KEYS).build();
      }

      // check duplicate record count
      long duplicateKeyCount =
          Long.parseLong(yamlObject.getOrDefault(DUPLICATE_KEY_COUNT, -1L).toString());
      long duplicateRecordKeyCount =
          Long.parseLong(yamlObject.getOrDefault(DUPLICATE_RECORD_KEY_COUNT, -1L).toString());

      if (duplicateKeyCount > 0) {
        log.warn("The number of duplicate keys: " + duplicateKeyCount);
        log.warn("The number of records with duplicate keys: " + duplicateRecordKeyCount);
      }

      if (duplicateKeyCount != 0) {
        return ValidationResult.builder().valid(false).message(HAS_DUPLICATES).build();
      } else {
        return ValidationResult.OK;
      }

    } else {
      log.info(
          "Validation not completed for dataset {}, no validation report at inputPath {}",
          datasetId,
          validateFilePath);
      return ValidationResult.builder().valid(false).message(NOT_VALIDATED).build();
    }
  }

  /**
   * Checks the content of the validate file, returning true if the UUID content has been checked
   * and is thought to be valid.
   */
  public static Long getDuplicateKeyCount(UUIDPipelineOptions options) throws Exception {
    FileSystem fs =
        FileSystemFactory.getInstance(options.getHdfsSiteConfig(), options.getCoreSiteConfig())
            .getFs(options.getInputPath());
    String validateFilePath = getValidationFilePath(options);
    Path metrics = new Path(validateFilePath);

    if (fs.exists(metrics)) {
      // read YAML
      Yaml yaml = new Yaml();
      // the YAML files created by metrics are UTF-16 encoded
      Map<String, Object> yamlObject =
          yaml.load(new InputStreamReader(fs.open(metrics), StandardCharsets.UTF_8));

      return Long.parseLong(yamlObject.getOrDefault(DUPLICATE_KEY_COUNT, -1L).toString());
    } else {
      throw new FileNotFoundException();
    }
  }

  /**
   * Checks the content of the validate file, returning true if the UUID content has been checked
   * and is thought to be valid.
   */
  @SneakyThrows
  public static Long getInvalidRecordCount(UUIDPipelineOptions options) {

    FileSystem fs =
        FileSystemFactory.getInstance(options.getHdfsSiteConfig(), options.getCoreSiteConfig())
            .getFs(options.getInputPath());
    String validateFilePath = getValidationFilePath(options);
    Path metrics = new Path(validateFilePath);

    if (fs.exists(metrics)) {
      // read YAML
      Yaml yaml = new Yaml();
      // the YAML files created by metrics are UTF-16 encoded
      Map<String, Object> yamlObject =
          yaml.load(new InputStreamReader(fs.open(metrics), StandardCharsets.UTF_8));

      return Long.parseLong(yamlObject.getOrDefault(EMPTY_KEY_RECORDS, -1L).toString());
    } else {
      throw new FileNotFoundException(
          "Unable to retrieve count. File not found: " + validateFilePath);
    }
  }

  @NotNull
  public static String getValidationFilePath(InterpretationPipelineOptions options) {
    return String.join(
        "/", options.getInputPath(), options.getDatasetId().trim(), "1", VALIDATION_REPORT_FILE);
  }

  @NotNull
  public static String getValidationFilePath(String inputPath, String datasetId, Integer attempt) {
    return String.join("/", inputPath, datasetId, attempt.toString(), VALIDATION_REPORT_FILE);
  }

  public static Path getMetrics(
      String filePath, String datasetId, Integer attempt, String metricsFile) {
    return new Path(String.join("/", filePath, datasetId, attempt.toString(), metricsFile));
  }

  @SneakyThrows
  public static Map<String, Object> readValidation(
      FileSystem fs, String filePath, String datasetID, Integer attempt) {

    // read YAML
    Yaml yaml = new Yaml();
    Path validationMetrics = getMetrics(filePath, datasetID, attempt, VALIDATION_REPORT_FILE);

    // the YAML files created by metrics are UTF-16 encoded
    return yaml.load(new InputStreamReader(fs.open(validationMetrics), StandardCharsets.UTF_8));
  }

  @SneakyThrows
  public static Long readVerbatimCount(
      FileSystem fs, String filePath, String datasetID, Integer attempt) {

    // read YAML
    Yaml yaml = new Yaml();
    Path validationMetrics = getMetrics(filePath, datasetID, attempt, VERBATIM_METRICS);

    if (!fs.exists(validationMetrics)) {
      return -1L;
    }

    // the YAML files created by metrics are UTF-16 encoded
    Map<String, Object> metrics =
        yaml.load(new InputStreamReader(fs.open(validationMetrics), StandardCharsets.UTF_16));

    return Long.parseLong(metrics.getOrDefault("archiveToErCountAttempted", "-1").toString());
  }

  /**
   * Generate a unique key based on the darwin core fields. This works the same was unique keys
   * where generated in the biocache-store. This is repeated to maintain backwards compatibility
   * with existing data holdings.
   */
  public static String generateUniqueKey(
      String datasetID, ExtendedRecord source, List<Term> uniqueTerms) {

    List<String> uniqueValues = new ArrayList<>();
    boolean allUniqueValuesAreEmpty = true;
    for (Term term : uniqueTerms) {
      String value = ModelUtils.extractNullAwareValue(source, term);
      if (value != null && StringUtils.trimToNull(value) != null) {
        // we have a term with a value
        allUniqueValuesAreEmpty = false;
        uniqueValues.add(value.trim());
      }
    }

    if (allUniqueValuesAreEmpty) {

      String termList = uniqueTerms.stream().map(Term::simpleName).collect(Collectors.joining(","));
      String errorMessage =
          String.format(
              "Unable to load dataset %s, All supplied unique terms (%s) where empty record with ID %s",
              datasetID, termList, source.getId());

      log.warn(errorMessage);
      throw new RuntimeException(errorMessage);
    }

    // add the datasetID
    uniqueValues.add(0, datasetID);

    // create the unique key
    return String.join(UNIQUE_COMPOSITE_KEY_JOIN_CHAR, uniqueValues);
  }

  /**
   * Generate a unique key based on the darwin core fields. This works the same was unique keys
   * where generated in the biocache-store. This is repeated to maintain backwards compatibility
   * with existing data holdings.
   */
  public static String generateUniqueKeyForValidation(
      String datasetID, ExtendedRecord source, List<Term> uniqueTerms) {

    final List<String> uniqueValues = new ArrayList<>();
    boolean allUniqueValuesAreEmpty = true;
    for (Term term : uniqueTerms) {
      String value = ModelUtils.extractNullAwareValue(source, term);
      if (value != null && StringUtils.trimToNull(value) != null) {
        // we have a term with a value
        allUniqueValuesAreEmpty = false;
        uniqueValues.add(value.trim());
      }
    }

    if (allUniqueValuesAreEmpty) {
      return "";
    }

    // add the datasetID
    uniqueValues.add(0, datasetID);

    // create the unique key
    return String.join(UNIQUE_COMPOSITE_KEY_JOIN_CHAR, uniqueValues);
  }

  @SneakyThrows
  public static boolean metricsExists(
      FileSystem fs, String filePath, String datasetId, Integer attempt, String metricsFile) {
    Path metrics = new Path(String.join("/", filePath, datasetId, attempt.toString(), metricsFile));
    return fs.exists(metrics);
  }

  @SneakyThrows
  public static long metricsModificationTime(
      FileSystem fs, String filePath, String datasetId, Integer attempt, String metricsFile) {
    String path = String.join("/", filePath, datasetId, attempt.toString(), metricsFile);
    Path metrics = new Path(path);
    if (fs.exists(metrics)) {
      return fs.getFileStatus(metrics).getModificationTime();
    } else {
      throw new FileNotFoundException("Unable to read metrics file at: " + path);
    }
  }

  /**
   * Checks that verbatim avro is present using the inputPath value of options.
   *
   * @param options
   * @return true if verbatim avro is available
   */
  public static boolean isVerbatimAvroAvailable(InterpretationPipelineOptions options) {
    boolean verbatimAvroAvailable = false;
    try {
      FileSystem fs =
          FileSystemFactory.getInstance(options.getHdfsSiteConfig(), options.getCoreSiteConfig())
              .getFs(options.getInputPath());
      verbatimAvroAvailable = ALAFsUtils.exists(fs, options.getInputPath());
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return verbatimAvroAvailable;
  }

  /**
   * Checks that verbatim avro is present using the inputPath value of options.
   *
   * @param options
   * @return true if verbatim avro is available
   */
  public static boolean isInterpretedMultimediaAvroAvailable(
      InterpretationPipelineOptions options) {
    boolean multimediaAvroAvailable = false;
    try {
      FileSystem fs =
          FileSystemFactory.getInstance(options.getHdfsSiteConfig(), options.getCoreSiteConfig())
              .getFs(options.getInputPath());

      String path = PathBuilder.buildDatasetAttemptPath(options, "multimedia", true);

      multimediaAvroAvailable = ALAFsUtils.exists(fs, path);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return multimediaAvroAvailable;
  }
}
