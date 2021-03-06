package au.org.ala.kvs.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.io.Serializable;
import lombok.Builder;
import lombok.Value;

@JsonDeserialize(builder = LatLng.LatLngBuilder.class)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatLng implements Serializable {

  Double latitude;
  Double longitude;

  @JsonPOJOBuilder(withPrefix = "")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LatLngBuilder {}

  @Override
  public String toString() {
    return latitude + "," + longitude;
  }
}
