package org.gbif.xml.occurrence.parser.parsing.validators;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests the {@link UniquenessValidator}.
 */
public class UniquenessValidatorTest {

  private static final Logger LOG = LoggerFactory.getLogger(UniquenessValidatorTest.class);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void givenUniqueIdsWhenMappedThenNoDuplicates() {
    // increase it to test with a higher volume of data
    int n = 1000;

    try (UniquenessValidator validator = UniquenessValidator.getNewInstance()) {
      for (int i = 0; i < n; i++) {
        Assert.assertTrue(validator.isUnique(UUID.randomUUID().toString()));
      }
    }
  }

  @Test
  public void givenDuplicatedIdsWhenMappedThenDuplicateFound() {
    // increase it to test with a higher volume of data
    int n = 10;

    try (UniquenessValidator validator = UniquenessValidator.getNewInstance()) {
      // add duplicates
      List<String> duplicates = Arrays.asList("12.1", "1234.1", "111.1", "12.12");

      long duplicatesFound = duplicates.stream().filter(id -> !validator.isUnique(id)).count();

      Assert.assertEquals(0, duplicatesFound);

      // add more values to test with high volume of data
      Stopwatch watch = Stopwatch.createStarted();
      for (int i = 0; i < n; i++) {
        if (!validator.isUnique(UUID.randomUUID().toString())) {
          duplicatesFound++;
        }
      }
      LOG.info("time: {}", watch.stop().elapsed(TimeUnit.MILLISECONDS));

      Assert.assertEquals(0, duplicatesFound);

      // add more duplicates
      duplicates = Arrays.asList("12.1", "1234.1", "11.1", "12.1", "1234.1", "11.1", "12.12", "12.1", "1234.1", "11.1");

      duplicatesFound = duplicates.stream().filter(id -> !validator.isUnique(id)).count();

      Assert.assertEquals(9, duplicatesFound);
    }
  }

  @Test
  public void givenNullIdWhenMappedThenExceptionThrown() {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("ID is required");

    try (UniquenessValidator validator = UniquenessValidator.getNewInstance()) {
      validator.isUnique(null);
    }
  }

}
