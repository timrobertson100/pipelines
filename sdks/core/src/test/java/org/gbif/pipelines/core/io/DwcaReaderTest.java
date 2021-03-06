package org.gbif.pipelines.core.io;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.junit.Ignore;
import org.junit.Test;

public class DwcaReaderTest {

  @Test
  public void uncompressedReaderExtensionTest() throws IOException {
    // State
    String fileName = getClass().getResource("/dwca/plants_dwca_ext").getFile();

    // When
    try (DwcaReader dwCAReader = DwcaReader.fromLocation(fileName)) {
      dwCAReader.advance();
      ExtendedRecord current = dwCAReader.getCurrent();
      // Should
      assertNotNull(current);
      assertNotNull(current.getId());
    }
  }

  @Test
  public void uncompressedReaderTest() throws IOException {
    // State
    String fileName = getClass().getResource("/dwca/plants_dwca").getFile();

    // When
    try (DwcaReader dwCAReader = DwcaReader.fromLocation(fileName)) {
      dwCAReader.advance();
      ExtendedRecord current = dwCAReader.getCurrent();
      // Should
      assertNotNull(current);
      assertNotNull(current.getId());
    }
  }

  @Test
  @Ignore("Fails cause of resource settings in pom.xml/build")
  public void zipFileReaderTest() throws IOException {
    // State
    String fileName = getClass().getResource("/dwca/plants.zip").getFile();
    String fileOut = new File("target/tmp").getAbsolutePath();

    // When
    try (DwcaReader dwCAReader = DwcaReader.fromCompressed(fileName, fileOut)) {
      dwCAReader.advance();
      ExtendedRecord current = dwCAReader.getCurrent();
      // Should
      assertNotNull(current);
      assertNotNull(current.getId());
    }
  }
}
