package au.org.ala.pipelines.options;

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.gbif.pipelines.common.beam.options.InterpretationPipelineOptions;

public interface ImageServicePipelineOptions extends InterpretationPipelineOptions {

  @Description("Image Service sleep time between polling")
  @Default.Integer(5000)
  Integer getSleepTimeInMillis();

  void setSleepTimeInMillis(Integer sleepTime);

  @Description("Use async uploads")
  @Default.Boolean(false)
  boolean isAsyncUpload();

  void setAsyncUpload(boolean asyncUpload);

  @Description("The number of days to synchronise from")
  @Default.Long(-1)
  Long getModifiedWindowTimeInDays();

  void setModifiedWindowTimeInDays(Long modifiedWindowTimeInDays);

  @Description("Recognised image service Url paths. Used to translate URLs to imageIDs")
  @Default.String("https://images.ala.org.au")
  String getRecognisedPaths();

  void setRecognisedPaths(String recognisedPaths);
}
