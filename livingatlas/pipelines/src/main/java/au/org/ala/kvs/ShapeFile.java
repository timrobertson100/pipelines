package au.org.ala.kvs;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;

/** DTO for a shape file. This is mapped to configuration in pipelines.yaml. */
@AllArgsConstructor
@Data
public class ShapeFile implements Serializable {
  /** Path to the shape file */
  String path;
  /** The name field to use from the shape file. */
  String field;
  /** URL to source of the shapefile */
  String source;
}
