package org.gbif.pipelines.ingest.java.pipelines;

import static org.gbif.pipelines.common.PipelinesVariables.Metrics.AVRO_TO_HDFS_COUNT;
import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.AVRO_EXTENSION;
import static org.gbif.pipelines.core.utils.FsUtils.createParentDirectories;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.gbif.api.model.pipelines.StepType;
import org.gbif.pipelines.common.beam.metrics.IngestMetrics;
import org.gbif.pipelines.common.beam.metrics.MetricsHandler;
import org.gbif.pipelines.common.beam.options.InterpretationPipelineOptions;
import org.gbif.pipelines.common.beam.options.PipelinesOptionsFactory;
import org.gbif.pipelines.common.beam.utils.PathBuilder;
import org.gbif.pipelines.core.converters.MultimediaConverter;
import org.gbif.pipelines.core.converters.OccurrenceHdfsRecordConverter;
import org.gbif.pipelines.core.io.AvroReader;
import org.gbif.pipelines.core.io.SyncDataFileWriter;
import org.gbif.pipelines.core.io.SyncDataFileWriterBuilder;
import org.gbif.pipelines.ingest.java.metrics.IngestMetricsBuilder;
import org.gbif.pipelines.ingest.utils.HdfsViewAvroUtils;
import org.gbif.pipelines.ingest.utils.SharedLockUtils;
import org.gbif.pipelines.io.avro.AudubonRecord;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.ImageRecord;
import org.gbif.pipelines.io.avro.LocationRecord;
import org.gbif.pipelines.io.avro.MeasurementOrFactRecord;
import org.gbif.pipelines.io.avro.MetadataRecord;
import org.gbif.pipelines.io.avro.MultimediaRecord;
import org.gbif.pipelines.io.avro.OccurrenceHdfsRecord;
import org.gbif.pipelines.io.avro.TaxonRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.io.avro.grscicoll.GrscicollRecord;
import org.gbif.pipelines.transforms.core.BasicTransform;
import org.gbif.pipelines.transforms.core.GrscicollTransform;
import org.gbif.pipelines.transforms.core.LocationTransform;
import org.gbif.pipelines.transforms.core.TaxonomyTransform;
import org.gbif.pipelines.transforms.core.TemporalTransform;
import org.gbif.pipelines.transforms.core.VerbatimTransform;
import org.gbif.pipelines.transforms.extension.AudubonTransform;
import org.gbif.pipelines.transforms.extension.ImageTransform;
import org.gbif.pipelines.transforms.extension.MeasurementOrFactTransform;
import org.gbif.pipelines.transforms.extension.MultimediaTransform;
import org.gbif.pipelines.transforms.metadata.MetadataTransform;
import org.slf4j.MDC;

/**
 * Pipeline sequence:
 *
 * <pre>
 *    1) Reads avro files:
 *      {@link MetadataRecord},
 *      {@link BasicRecord},
 *      {@link TemporalRecord},
 *      {@link MultimediaRecord},
 *      {@link ImageRecord},
 *      {@link AudubonRecord},
 *      {@link MeasurementOrFactRecord},
 *      {@link TaxonRecord},
 *      {@link GrscicollRecord},
 *      {@link LocationRecord}
 *    2) Joins avro files
 *    3) Converts to a {@link OccurrenceHdfsRecord} based on the input files
 *    4) Moves the produced files to a directory where the latest version of HDFS records are kept
 * </pre>
 *
 * <p>How to run:
 *
 * <pre>{@code
 * java -jar target/ingest-gbif-java-BUILD_VERSION-shaded.jar org.gbif.pipelines.ingest.java.pipelines.InterpretedToHdfsViewPipeline some.properties
 *
 * or pass all parameters:
 *
 * java -jar target/ingest-gbif-java-BUILD_VERSION-shaded.jar org.gbif.pipelines.ingest.java.pipelines.InterpretedToHdfsViewPipeline \
 * --datasetId=4725681f-06af-4b1e-8fff-e31e266e0a8f \
 * --attempt=1 \
 * --inputPath=/path \
 * --targetPath=/path \
 * --properties=/path/pipelines.properties
 *
 * }</pre>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InterpretedToHdfsViewPipeline {

  public static void main(String[] args) {
    run(args);
  }

  public static void run(String[] args) {
    InterpretationPipelineOptions options = PipelinesOptionsFactory.createInterpretation(args);
    run(options);
  }

  public static void run(InterpretationPipelineOptions options) {
    ExecutorService executor = Executors.newWorkStealingPool();
    try {
      run(options, executor);
    } finally {
      executor.shutdown();
    }
  }

  public static void run(String[] args, ExecutorService executor) {
    InterpretationPipelineOptions options = PipelinesOptionsFactory.createInterpretation(args);
    run(options, executor);
  }

  @SneakyThrows
  public static void run(InterpretationPipelineOptions options, ExecutorService executor) {

    MDC.put("datasetKey", options.getDatasetId());
    MDC.put("attempt", options.getAttempt().toString());
    MDC.put("step", StepType.INTERPRETED_TO_INDEX.name());

    log.info("Options");
    UnaryOperator<String> pathFn =
        t -> PathBuilder.buildPathInterpretUsingInputPath(options, t, "*" + AVRO_EXTENSION);
    String hdfsSiteConfig = options.getHdfsSiteConfig();
    String coreSiteConfig = options.getCoreSiteConfig();

    log.info("Creating transformations");
    // Core
    BasicTransform basicTransform = BasicTransform.builder().create();
    MetadataTransform metadataTransform = MetadataTransform.builder().create();
    VerbatimTransform verbatimTransform = VerbatimTransform.create();
    TemporalTransform temporalTransform = TemporalTransform.builder().create();
    TaxonomyTransform taxonomyTransform = TaxonomyTransform.builder().create();
    GrscicollTransform grscicollTransform = GrscicollTransform.builder().create();
    LocationTransform locationTransform = LocationTransform.builder().create();

    // Extension
    MeasurementOrFactTransform measurementTransform = MeasurementOrFactTransform.builder().create();
    MultimediaTransform multimediaTransform = MultimediaTransform.builder().create();
    AudubonTransform audubonTransform = AudubonTransform.builder().create();
    ImageTransform imageTransform = ImageTransform.builder().create();

    log.info("Init metrics");
    IngestMetrics metrics = IngestMetricsBuilder.createInterpretedToHdfsViewMetrics();

    log.info("Creating pipeline");

    // Reading all avro files in parallel
    CompletableFuture<Map<String, MetadataRecord>> metadataMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    MetadataRecord.class,
                    pathFn.apply(metadataTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, ExtendedRecord>> verbatimMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    ExtendedRecord.class,
                    pathFn.apply(verbatimTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, BasicRecord>> basicMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    BasicRecord.class,
                    pathFn.apply(basicTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, TemporalRecord>> temporalMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    TemporalRecord.class,
                    pathFn.apply(temporalTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, LocationRecord>> locationMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    LocationRecord.class,
                    pathFn.apply(locationTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, TaxonRecord>> taxonMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    TaxonRecord.class,
                    pathFn.apply(taxonomyTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, GrscicollRecord>> grscicollMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    GrscicollRecord.class,
                    pathFn.apply(grscicollTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, MultimediaRecord>> multimediaMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    MultimediaRecord.class,
                    pathFn.apply(multimediaTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, ImageRecord>> imageMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    ImageRecord.class,
                    pathFn.apply(imageTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, AudubonRecord>> audubonMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    AudubonRecord.class,
                    pathFn.apply(audubonTransform.getBaseName())),
            executor);

    CompletableFuture<Map<String, MeasurementOrFactRecord>> measurementMapFeature =
        CompletableFuture.supplyAsync(
            () ->
                AvroReader.readRecords(
                    hdfsSiteConfig,
                    coreSiteConfig,
                    MeasurementOrFactRecord.class,
                    pathFn.apply(measurementTransform.getBaseName())),
            executor);

    CompletableFuture.allOf(
            metadataMapFeature,
            verbatimMapFeature,
            basicMapFeature,
            temporalMapFeature,
            locationMapFeature,
            taxonMapFeature,
            grscicollMapFeature,
            multimediaMapFeature,
            imageMapFeature,
            audubonMapFeature,
            measurementMapFeature)
        .get();

    MetadataRecord metadata = metadataMapFeature.get().values().iterator().next();
    Map<String, BasicRecord> basicMap = basicMapFeature.get();
    Map<String, ExtendedRecord> verbatimMap = verbatimMapFeature.get();
    Map<String, TemporalRecord> temporalMap = temporalMapFeature.get();
    Map<String, LocationRecord> locationMap = locationMapFeature.get();
    Map<String, TaxonRecord> taxonMap = taxonMapFeature.get();
    Map<String, GrscicollRecord> grscicollMap = grscicollMapFeature.get();
    Map<String, MultimediaRecord> multimediaMap = multimediaMapFeature.get();
    Map<String, ImageRecord> imageMap = imageMapFeature.get();
    Map<String, AudubonRecord> audubonMap = audubonMapFeature.get();
    Map<String, MeasurementOrFactRecord> measurementMap = measurementMapFeature.get();

    // Join all records, convert into OccurrenceHdfsRecord and save as an avro file
    Function<BasicRecord, OccurrenceHdfsRecord> occurrenceHdfsRecordFn =
        br -> {
          String k = br.getId();
          // Core
          ExtendedRecord er =
              verbatimMap.getOrDefault(k, ExtendedRecord.newBuilder().setId(k).build());
          TemporalRecord tr =
              temporalMap.getOrDefault(k, TemporalRecord.newBuilder().setId(k).build());
          LocationRecord lr =
              locationMap.getOrDefault(k, LocationRecord.newBuilder().setId(k).build());
          TaxonRecord txr = taxonMap.getOrDefault(k, TaxonRecord.newBuilder().setId(k).build());
          GrscicollRecord gr =
              grscicollMap.getOrDefault(k, GrscicollRecord.newBuilder().setId(k).build());
          // Extension
          MultimediaRecord mr =
              multimediaMap.getOrDefault(k, MultimediaRecord.newBuilder().setId(k).build());
          ImageRecord ir = imageMap.getOrDefault(k, ImageRecord.newBuilder().setId(k).build());
          AudubonRecord ar =
              audubonMap.getOrDefault(k, AudubonRecord.newBuilder().setId(k).build());
          MeasurementOrFactRecord mfr =
              measurementMap.getOrDefault(k, MeasurementOrFactRecord.newBuilder().setId(k).build());

          metrics.incMetric(AVRO_TO_HDFS_COUNT);

          MultimediaRecord mmr = MultimediaConverter.merge(mr, ir, ar);
          return OccurrenceHdfsRecordConverter.toOccurrenceHdfsRecord(
              br, metadata, tr, lr, txr, gr, mmr, mfr, er);
        };

    boolean useSyncMode = options.getSyncThreshold() > basicMap.size();

    try (SyncDataFileWriter<OccurrenceHdfsRecord> writer = createWriter(options)) {
      if (useSyncMode) {
        basicMap.values().stream().map(occurrenceHdfsRecordFn).forEach(writer::append);
      } else {
        CompletableFuture<?>[] futures =
            basicMap.values().stream()
                .map(
                    br ->
                        CompletableFuture.runAsync(
                            () -> writer.append(occurrenceHdfsRecordFn.apply(br)), executor))
                .toArray(CompletableFuture[]::new);
        // Wait for all futures
        CompletableFuture.allOf(futures).get();
      }
    }

    SharedLockUtils.doHdfsPrefixLock(options, () -> HdfsViewAvroUtils.move(options));

    MetricsHandler.saveCountersToInputPathFile(options, metrics.getMetricsResult());
    log.info("Pipeline has been finished - {}", LocalDateTime.now());
  }

  /** Create an AVRO file writer */
  @SneakyThrows
  @SuppressWarnings("all")
  private static SyncDataFileWriter<OccurrenceHdfsRecord> createWriter(
      InterpretationPipelineOptions options) {
    String id = options.getDatasetId() + '_' + options.getAttempt();
    String targetTempPath =
        PathBuilder.buildFilePathHdfsViewUsingInputPath(options, id + AVRO_EXTENSION);
    Path path = new Path(targetTempPath);
    FileSystem verbatimFs =
        createParentDirectories(options.getHdfsSiteConfig(), options.getCoreSiteConfig(), path);
    return SyncDataFileWriterBuilder.builder()
        .schema(OccurrenceHdfsRecord.getClassSchema())
        .codec(options.getAvroCompressionType())
        .outputStream(verbatimFs.create(path))
        .syncInterval(options.getAvroSyncInterval())
        .build()
        .createSyncDataFileWriter();
  }
}
