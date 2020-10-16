package au.org.ala.pipelines.interpreters;

import static org.gbif.api.vocabulary.OccurrenceIssue.COORDINATE_UNCERTAINTY_METERS_INVALID;
import static org.gbif.pipelines.core.utils.ModelUtils.addIssue;
import static org.gbif.pipelines.core.utils.ModelUtils.extractNullAwareValue;
import static org.gbif.pipelines.core.utils.ModelUtils.extractValue;
import static org.gbif.pipelines.core.utils.ModelUtils.hasValue;

import au.org.ala.pipelines.parser.CoordinatesParser;
import au.org.ala.pipelines.parser.DistanceParser;
import au.org.ala.pipelines.vocabulary.*;
import com.google.common.base.Strings;
import com.google.common.collect.Range;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.common.parsers.core.OccurrenceParseResult;
import org.gbif.common.parsers.date.TemporalAccessorUtils;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.kvs.KeyValueStore;
import org.gbif.kvs.geocode.LatLng;
import org.gbif.pipelines.core.interpreters.core.LocationInterpreter;
import org.gbif.pipelines.core.interpreters.core.TemporalInterpreter;
import org.gbif.pipelines.core.parsers.common.ParsedField;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.LocationRecord;
import org.gbif.rest.client.geocode.GeocodeResponse;
import org.gbif.rest.client.geocode.Location;

/** Extensions to GBIF's {@link LocationInterpreter} */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ALALocationInterpreter {

  /**
   * Interpret stateProvince values, performing a coordinate lookup and comparing with supplied
   * stateProvince.
   *
   * @param stateProvinceLookupService Provided by ALA country/state SHP file
   */
  public static BiConsumer<ExtendedRecord, LocationRecord> interpretStateProvince(
      KeyValueStore<LatLng, GeocodeResponse> stateProvinceLookupService) {
    return (er, lr) -> {
      ParsedField<LatLng> parsedLatLon = CoordinatesParser.parseCoords(er);
      addIssue(lr, parsedLatLon.getIssues());

      if (parsedLatLon.isSuccessful()) {

        LatLng latlng = parsedLatLon.getResult();
        lr.setDecimalLatitude(latlng.getLatitude());
        lr.setDecimalLongitude(latlng.getLongitude());
        lr.setHasCoordinate(true);

        // do the lookup by coordinates
        GeocodeResponse gr = stateProvinceLookupService.get(latlng);
        if (gr != null) {
          Collection<Location> locations = gr.getLocations();
          Optional<Location> stateProvince = locations.stream().findFirst();

          if (stateProvince.isPresent()) {
            // use the retrieve value, this takes precidence over the stateProvince DwCTerm
            // which follows the GBIF implementation of setting DwCTerm country value
            lr.setStateProvince(stateProvince.get().getName());
          } else {
            if (log.isDebugEnabled()) {
              log.debug(
                  "Current stateProvince SHP file does not contain a state at {}",
                  latlng.toString());
            }
          }
        } else {
          if (log.isDebugEnabled()) {
            log.debug(
                "No recognised stateProvince  is found at : {}",
                parsedLatLon.getResult().toString());
          }
        }
      }

      // Assign state from source if no state is fetched from coordinates
      if (Strings.isNullOrEmpty(lr.getStateProvince())) {
        LocationInterpreter.interpretStateProvince(er, lr);
      }
    };
  }

  /**
   * Verify country and state info,
   *
   * @return
   */
  public static BiConsumer<ExtendedRecord, LocationRecord> verifyLocationInfo(
      CentrePoints countryCentrePoints,
      CentrePoints stateProvinceCentrePoints,
      Vocab stateProvinceVocab) {

    return (er, lr) -> {
      if (lr.getDecimalLongitude() != null && lr.getDecimalLatitude() != null) {
        if (!Strings.isNullOrEmpty(lr.getCountryCode())) {
          if (countryCentrePoints.coordinatesMatchCentre(
              lr.getCountry(), lr.getDecimalLatitude(), lr.getDecimalLongitude())) {
            addIssue(lr, ALAOccurrenceIssue.COORDINATES_CENTRE_OF_COUNTRY.name());
          }
        }

        if (!Strings.isNullOrEmpty(lr.getStateProvince())) {
          // Formalize state name
          Optional<String> formalStateName = stateProvinceVocab.matchTerm(lr.getStateProvince());
          if (formalStateName.isPresent()) {
            lr.setStateProvince(formalStateName.get());
          }

          String suppliedStateProvince = extractNullAwareValue(er, DwcTerm.stateProvince);
          if (!Strings.isNullOrEmpty(suppliedStateProvince)) {
            // If the stateProvince that is retrieved using the coordinates differs from the
            // supplied stateProvince
            // raise an issue
            Optional<String> formalSuppliedName =
                stateProvinceVocab.matchTerm(suppliedStateProvince);
            if (formalSuppliedName.isPresent()) {
              suppliedStateProvince = formalSuppliedName.get();
            }
            if (!suppliedStateProvince.equalsIgnoreCase(lr.getStateProvince()))
              addIssue(lr, ALAOccurrenceIssue.STATE_COORDINATE_MISMATCH.name());
          }

          if (stateProvinceCentrePoints.coordinatesMatchCentre(
              lr.getStateProvince(), lr.getDecimalLatitude(), lr.getDecimalLongitude())) {
            addIssue(lr, ALAOccurrenceIssue.COORDINATES_CENTRE_OF_STATEPROVINCE.name());
          } else {
            if (log.isTraceEnabled()) {
              log.trace(
                  "{},{} is not at the centre of {}!",
                  lr.getDecimalLatitude(),
                  lr.getDecimalLongitude(),
                  lr.getStateProvince());
            }
          }
        }
      }
    };
  }

  /**
   * Parsing of georeferenceDate darwin terms.
   *
   * @param er
   * @param lr
   */
  public static void interpretGeoreferencedDate(ExtendedRecord er, LocationRecord lr) {
    if (hasValue(er, DwcTerm.georeferencedDate)) {
      LocalDate upperBound = LocalDate.now().plusDays(1);
      Range<LocalDate> validRecordedDateRange =
          Range.closed(ALATemporalInterpreter.MIN_LOCAL_DATE, upperBound);

      // GBIF TemporalInterpreter only accepts OccurrenceIssue
      // Convert GBIF IDENTIFIED_DATE_UNLIKELY to ALA GEOREFERENCED_DATE_UNLIKELY
      OccurrenceParseResult<TemporalAccessor> parsed =
          TemporalInterpreter.interpretLocalDate(
              extractValue(er, DwcTerm.georeferencedDate),
              validRecordedDateRange,
              OccurrenceIssue.IDENTIFIED_DATE_UNLIKELY);
      if (parsed.isSuccessful()) {
        Optional.ofNullable(
                TemporalAccessorUtils.toEarliestLocalDateTime(parsed.getPayload(), false))
            .map(LocalDateTime::toString)
            .ifPresent(lr::setGeoreferencedDate);
      }

      if (parsed.getIssues().contains(OccurrenceIssue.IDENTIFIED_DATE_UNLIKELY)) {
        addIssue(lr, ALAOccurrenceIssue.GEOREFERENCED_DATE_UNLIKELY.name());
      }
    } else {
      addIssue(lr, ALAOccurrenceIssue.MISSING_GEOREFERENCE_DATE.name());
    }
  }

  /**
   * Only checking if georeference fields are missing. It does not interpret or assign value to
   * LocationRecord. The follow issues are raised: MISSING_GEODETICDATUM MISSING_GEOREFERENCE_DATE
   * MISSING_GEOREFERENCEPROTOCOL MISSING_GEOREFERENCESOURCES MISSING_GEOREFERENCEVERIFICATIONSTATUS
   */
  public static void interpretGeoreferenceTerms(ExtendedRecord er, LocationRecord lr) {

    // check for missing georeferencedBy
    if (Strings.isNullOrEmpty(extractNullAwareValue(er, DwcTerm.georeferencedBy))) {
      addIssue(lr, ALAOccurrenceIssue.MISSING_GEOREFERENCEDBY.name());
    }

    // check for missing georeferencedProtocol
    if (Strings.isNullOrEmpty(extractNullAwareValue(er, DwcTerm.georeferenceProtocol))) {
      addIssue(lr, ALAOccurrenceIssue.MISSING_GEOREFERENCEPROTOCOL.name());
    }

    // check for missing georeferenceSources
    if (Strings.isNullOrEmpty(extractNullAwareValue(er, DwcTerm.georeferenceSources))) {
      addIssue(lr, ALAOccurrenceIssue.MISSING_GEOREFERENCESOURCES.name());
    }

    // check for missing georeferenceVerificationStatus
    if (Strings.isNullOrEmpty(extractNullAwareValue(er, DwcTerm.georeferenceVerificationStatus))) {
      addIssue(lr, ALAOccurrenceIssue.MISSING_GEOREFERENCEVERIFICATIONSTATUS.name());
    }
  }

  public static void interpretCoordinateUncertaintyInMeters(ExtendedRecord er, LocationRecord lr) {
    String uncertaintyValue = extractNullAwareValue(er, DwcTerm.coordinateUncertaintyInMeters);
    String precisionValue = extractNullAwareValue(er, DwcTerm.coordinatePrecision);

    double uncertaintyInMeters = -1;

    // If uncertainty NOT supplied
    if (Strings.isNullOrEmpty(uncertaintyValue)) {
      addIssue(lr, OccurrenceIssue.COORDINATE_UNCERTAINTY_METERS_INVALID.name());
      // And if precision exists and is greater than 1
      // We need to check if uncertainty is misplaced to precision
      if (!Strings.isNullOrEmpty(precisionValue)) {
        try {
          // convert possible uom to meters
          double possiblePrecision = DistanceParser.parse(precisionValue);
          if (possiblePrecision > 1) {
            uncertaintyInMeters = possiblePrecision;
            addIssue(lr, ALAOccurrenceIssue.UNCERTAINTY_IN_PRECISION.name());
          }
        } catch (Exception e) {
          // Ignore precision/uncertainty process
          if (log.isDebugEnabled()) {
            log.debug("Unable to parse coordinatePrecision value: " + precisionValue);
          }
        }
      }
    } else {
      // Uncertainty available
      try {
        uncertaintyInMeters = DistanceParser.parse(uncertaintyValue);
      } catch (Exception e) {
        if (log.isDebugEnabled()) {
          log.debug("Unable to parse coordinateUncertaintyInMeters: " + uncertaintyValue);
        }
        addIssue(lr, OccurrenceIssue.COORDINATE_UNCERTAINTY_METERS_INVALID.name());
      }
    }

    // 5000 km seems safe
    if (uncertaintyInMeters > 0d && uncertaintyInMeters < 5_000_000d) {
      lr.setCoordinateUncertaintyInMeters(uncertaintyInMeters);
    } else {
      lr.setCoordinateUncertaintyInMeters(null); // Safely remove value
      addIssue(lr, COORDINATE_UNCERTAINTY_METERS_INVALID);
    }
  }
}
