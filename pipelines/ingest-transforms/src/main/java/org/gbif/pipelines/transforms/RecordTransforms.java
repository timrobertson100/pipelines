package org.gbif.pipelines.transforms;

import org.gbif.pipelines.core.Interpretation;
import org.gbif.pipelines.core.interpreters.BasicInterpreter;
import org.gbif.pipelines.core.interpreters.LocationInterpreter;
import org.gbif.pipelines.core.interpreters.MetadataInterpreter;
import org.gbif.pipelines.core.interpreters.MultimediaInterpreter;
import org.gbif.pipelines.core.interpreters.TaxonomyInterpreter;
import org.gbif.pipelines.core.interpreters.TemporalInterpreter;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.LocationRecord;
import org.gbif.pipelines.io.avro.MetadataRecord;
import org.gbif.pipelines.io.avro.MultimediaRecord;
import org.gbif.pipelines.io.avro.TaxonRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.parsers.ws.config.WsConfig;
import org.gbif.pipelines.parsers.ws.config.WsConfigFactory;

import java.nio.file.Paths;
import java.util.Optional;

import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.DoFn;

/**
 * Contains ParDo functions for Beam, each method returns GBIF transformation (basic, temporal,
 * multimedia, location, metadata, taxonomy). Transformation uses {@link
 * org.gbif.pipelines.core.interpreters} to interpret and convert source data to target data
 *
 * <p>You can apply this functions to your Beam pipeline:
 *
 * <pre>{@code
 * PCollection<ExtendedRecord> records = ...
 * PCollection<TemporalRecord> t = records.apply(ParDo.of(new BasicFn()));
 *
 * }</pre>
 */
public class RecordTransforms {

  private RecordTransforms() {}

  /**
   * ParDo runs sequence of interpretations for {@link MultimediaRecord} using {@link
   * ExtendedRecord} as a source and {@link MultimediaInterpreter} as interpretation steps
   */
  public static class MultimediaFn extends DoFn<ExtendedRecord, MultimediaRecord> {
    private final Counter counter = Metrics.counter(RecordTransforms.class, "MultimediaRecord");

    @ProcessElement
    public void processElement(ProcessContext context) {
      Interpretation.from(context::element)
          .to(er -> MultimediaRecord.newBuilder().setId(er.getId()).build())
          .via(MultimediaInterpreter::interpretMultimedia)
          .consume(context::output);

      counter.inc();
    }
  }

  /**
   * ParDo runs sequence of interpretations for {@link TemporalRecord} using {@link ExtendedRecord}
   * as a source and {@link TemporalInterpreter} as interpretation steps
   */
  public static class TemporalFn extends DoFn<ExtendedRecord, TemporalRecord> {
    private final Counter counter = Metrics.counter(RecordTransforms.class, "TemporalRecord");

    @ProcessElement
    public void processElement(ProcessContext context) {
      Interpretation.from(context::element)
          .to(er -> TemporalRecord.newBuilder().setId(er.getId()).build())
          .via(TemporalInterpreter::interpretEventDate)
          .via(TemporalInterpreter::interpretDateIdentified)
          .via(TemporalInterpreter::interpretModifiedDate)
          .via(TemporalInterpreter::interpretDayOfYear)
          .consume(context::output);

      counter.inc();
    }
  }

  /**
   * ParDo runs sequence of interpretations for {@link BasicRecord} using {@link ExtendedRecord} as
   * a source and {@link BasicInterpreter} as interpretation steps
   */
  public static class BasicFn extends DoFn<ExtendedRecord, BasicRecord> {
    private final Counter counter = Metrics.counter(RecordTransforms.class, "BasicRecord");

    @ProcessElement
    public void processElement(ProcessContext context) {
      Interpretation.from(context::element)
          .to(er -> BasicRecord.newBuilder().setId(er.getId()).build())
          .via(BasicInterpreter::interpretBasisOfRecord)
          .via(BasicInterpreter::interpretSex)
          .via(BasicInterpreter::interpretEstablishmentMeans)
          .via(BasicInterpreter::interpretLifeStage)
          .via(BasicInterpreter::interpretTypeStatus)
          .via(BasicInterpreter::interpretIndividualCount)
          .via(BasicInterpreter::interpretReferences)
          .consume(context::output);

      counter.inc();
    }
  }

  /**
   * ParDo runs sequence of interpretations for {@link LocationRecord} using {@link ExtendedRecord}
   * as a source and {@link LocationInterpreter} as interpretation steps
   *
   * <p>wsConfig to create a WsConfig object, please use {@link WsConfigFactory}
   */
  public static class LocationFn extends DoFn<ExtendedRecord, LocationRecord> {
    private final Counter counter = Metrics.counter(RecordTransforms.class, "LocationRecord");

    private final WsConfig wsConfig;

    public LocationFn(WsConfig wsConfig) {
      this.wsConfig = wsConfig;
    }

    public LocationFn(String properties) {
      this.wsConfig = WsConfigFactory.create("geocode", Paths.get(properties));
    }

    @ProcessElement
    public void processElement(ProcessContext context) {
      Interpretation.from(context::element)
          .to(er -> LocationRecord.newBuilder().setId(er.getId()).build())
          .via(LocationInterpreter.interpretCountryAndCoordinates(wsConfig))
          .via(LocationInterpreter::interpretContinent)
          .via(LocationInterpreter::interpretWaterBody)
          .via(LocationInterpreter::interpretStateProvince)
          .via(LocationInterpreter::interpretMinimumElevationInMeters)
          .via(LocationInterpreter::interpretMaximumElevationInMeters)
          .via(LocationInterpreter::interpretMinimumDepthInMeters)
          .via(LocationInterpreter::interpretMaximumDepthInMeters)
          .via(LocationInterpreter::interpretMinimumDistanceAboveSurfaceInMeters)
          .via(LocationInterpreter::interpretMaximumDistanceAboveSurfaceInMeters)
          .via(LocationInterpreter::interpretCoordinatePrecision)
          .via(LocationInterpreter::interpretCoordinateUncertaintyInMeters)
          .consume(context::output);

      counter.inc();
    }
  }

  /**
   * ParDo runs sequence of interpretations for {@link MetadataRecord} using {@link ExtendedRecord}
   * as a source and {@link MetadataInterpreter} as interpretation steps
   *
   * <p>wsConfig to create a WsConfig object, please use {@link WsConfigFactory}
   */
  public static class MetadataFn extends DoFn<String, MetadataRecord> {
    private final Counter counter = Metrics.counter(RecordTransforms.class, "MetadataRecord");

    private final WsConfig wsConfig;

    public MetadataFn(WsConfig wsConfig) {
      this.wsConfig = wsConfig;
    }

    public MetadataFn(String properties) {
      this.wsConfig = WsConfigFactory.create("metadata", Paths.get(properties));
    }

    @ProcessElement
    public void processElement(ProcessContext context) {
      Interpretation.from(context::element)
          .to(id -> MetadataRecord.newBuilder().setId(id).build())
          .via(MetadataInterpreter.interpret(wsConfig))
          .consume(context::output);

      counter.inc();
    }
  }

  /**
   * ParDo runs sequence of interpretations for {@link TaxonRecord} using {@link ExtendedRecord} as
   * a source and {@link TaxonomyInterpreter} as interpretation steps
   *
   * <p>wsConfig to create a WsConfig object, please use {@link WsConfigFactory}
   */
  public static class TaxonomyFn extends DoFn<ExtendedRecord, TaxonRecord> {
    private final Counter counter = Metrics.counter(RecordTransforms.class, "TaxonRecord");

    private final WsConfig wsConfig;

    public TaxonomyFn(WsConfig wsConfig) {
      this.wsConfig = wsConfig;
    }

    public TaxonomyFn(String properties) {
      this.wsConfig = WsConfigFactory.create("match", Paths.get(properties));
    }

    @ProcessElement
    public void processElement(ProcessContext context) {
      Interpretation.from(context::element)
          .to(TaxonRecord.newBuilder()::build)
          .via(TaxonomyInterpreter.taxonomyInterpreter(wsConfig))
          // the id is null when there is an error in the interpretation. In these
          // cases we do not write the taxonRecord because it is totally empty.
          .consume(v -> Optional.ofNullable(v.getId()).ifPresent(id -> context.output(v)));

      counter.inc();
    }
  }
}
