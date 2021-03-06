package au.org.ala.pipelines.interpreters;

import static org.gbif.pipelines.core.utils.ModelUtils.extractOptValue;

import au.org.ala.pipelines.parser.CollectorNameParser;
import au.org.ala.pipelines.parser.LicenseParser;
import java.util.Arrays;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.gbif.api.vocabulary.License;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ALABasicInterpreter {
  public static void interpretRecordedBy(ExtendedRecord er, BasicRecord br) {
    extractOptValue(er, DwcTerm.recordedBy)
        .filter(x -> !x.isEmpty())
        .map(CollectorNameParser::parseList)
        .map(Arrays::asList)
        .ifPresent(br::setRecordedBy);
  }

  public static void interpretLicense(ExtendedRecord er, BasicRecord br) {
    LicenseParser parser = LicenseParser.getInstance();
    Optional<String> value = extractOptValue(er, DcTerm.license);
    if (value.isPresent()) {
      br.setLicense(parser.matchLicense(value.get()));
    } else {
      br.setLicense(License.UNSPECIFIED.name());
    }
  }
}
