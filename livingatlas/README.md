# Living Atlas Pipelines extensions 
| | |
| ---- | ----|
| [![Build Status](https://api.travis-ci.org/gbif/pipelines.svg?branch=ala-dev)](http://travis-ci.org/gbif/pipelines) | Travis build for ala-dev branch |
| [![Build Status](https://builds.gbif.org/job/pipelines-la-dev/badge/icon?style=flat-square)](https://builds.gbif.org/job/pipelines/)| GBIF Jenkins build for ala- dev branch |
| [![Coverage](https://sonar.gbif.org/api/project_badges/measure?project=org.gbif.pipelines%3Apipelines-parent&metric=coverage)](https://sonar.gbif.org/dashboard?id=org.gbif.pipelines%3Apipelines-parent) |  Sonar  |

The aim of this module is to add functionality required by the Living Atlases to facilitate the replacement to [biocache-store](https://github.com/AtlasOfLivingAustralia/biocache-store) for data ingress. 

## Architecture

For details on the GBIF implementation, see the [pipelines github repository](https://github.com/gbif/pipelines).
This project is focussed on extensions to that architecture to support use by the Living Atlases.

![Pipelines](https://docs.google.com/drawings/d/e/2PACX-1vQhQSg5VFo2xRZfDhmvhKuNLUpyTOlW-t-m1fesJ2RElWorVPAEbnsZg_StJKh22mEcS4D28j_nPoTV/pub?w=960&h=720 "Pipelines") 

Above is a representation of the data flow from source data in Darwin core archives supplied by data providers, to the API access to these data via the biocache-service component.

Within the "Interpreted AVRO" box is a list of "transforms" each of which take the source data and produce an isolated output in a AVRO formatted file.

[GBIF's pipelines](https://github.com/gbif/pipelines) already supports a number of core transforms for handling biodiversity occurrence data. The intention is to make us of these transforms "as-is" which effectively perform the very similar functionality to what is supported by the biocache-store project (ALA's current ingress library for occurrence data). 

This list of transforms will need to be added to backfill some of the ingress requirements of the ALA. These transforms will make use of existing ALA services:

* *ALA Taxonomy transform* - will make use of the existing **[ala-name-matching](https://github.com/AtlasOfLivingAustralia/ala-name-matching) library**
* *Sensitive data* - will make use of existing services in https://lists.ala.org.au to retrieve sensitive species rules.
* *Spatial layers* - will make use of existing services in https://spatial.ala.org.au/ws/ to retrieve sampled environmental and contextual values for geospatial points
* *Species lists* - will make use of existing services in https://lists.ala.org.au to retrieve species lists.

For information on how the architecture between biocache-store and pipelines differ, [see this page](architectures.md).


In addition pipelines for following will need to be developed:

* Duplicate detection
* Environmental outlier detection
* Expert distribution outliers

## To be done:

1. Sensible use of GBIF's key/value store framework (backend storage to be identified)
2. Dealing with sensitive data
4. Integration with Lists tool
6. Handling of images with ALA's image-service as storage

## Dependent projects

The pipelines work will necessitate some minor additional API additions and change to the following components:

### biocache-service
[experimental/pipelines branch](https://github.com/AtlasOfLivingAustralia/biocache-service/tree/experimental/pipelines) 
The aim for this proof of concept is to make very minimal changes to biocache-service, maintain the existing API and have no impact on existing services and applications.

### ala-namematching-service
A simple **drop wizard wrapper around the [ala-name-matching](https://github.com/AtlasOfLivingAustralia/ala-name-matching) library** has been prototyped to support integration with pipelines.
 
## Getting started

In the absence of ansible scripts, below are some instructions for setting up a local development environment for pipelines.
These steps will load a dataset into a SOLR index.

### Software requirements:

* Java 8 - this is mandatory (see [GBIF pipelines documentation](https://github.com/gbif/pipelines#about-the-project))
* Maven needs to run with OpenSDK 1.8 
'nano ~/.mavenrc' add 'export JAVA_HOME=[JDK1.8 PATH]'
* [Docker Desktop](https://www.docker.com/products/docker-desktop)
* [lombok plugin for intelliJ](https://projectlombok.org/setup/intellij) needs to be installed for slf4 annotation  

### Setting up la-pipelines
  
1. Download shape files from [here](https://pipelines-shp.s3-ap-southeast-2.amazonaws.com/pipelines-shapefiles.zip) and expand into `/data/pipelines-shp` directory
1. Download SDS shape files from [here](https://biocache.ala.org.au/archives/layers/sds-layers.tgz) and expand into `/data/pipelines-shp` directory
1. Download a test darwin core archive (e.g. https://archives.ala.org.au/archives/gbif/dr893/dr893.zip)
1. Create the following directory `/data/pipelines-data`
1. Build with maven `mvn clean package`

### Running la-pipelines

1. Start required docker containers using
```
docker-compose -f pipelines/src/main/docker/ala-nameservice.yml up -d
docker-compose -f pipelines/src/main/docker/solr8.yml up -d
```
1. `cd scripts`
1. To convert DwCA to AVRO, run `./dwca-avro.sh dr893`
1. To interpret, run `./interpret-spark-embedded.sh dr893`
1. To mint UUIDs, run `./uuid-spark-embedded.sh dr893`
1. To sample run:
    1. `./export-latlng-embedded.sh dr893`
    1. `./sample.sh dr893`
    1. `./sample-avro-embedded.sh dr893`
1. To setup SOLR:
    1. Run `cd ../solr/scripts` and  then run ' `./update-solr-config.sh`
    1. Run `cd ../../scripts`
1. To index, run `./index-spark-embedded.sh dr893`

## Integration Tests

Tests follow the GBIF/failsafe/surefire convention. 
All integration tests have a suffix of "IT". 
All junit tests are ran with `mvn package` and integration tests are ran with `mvn verify`.

`mvn verify` will start the docker containers in the `pre-integration-test` phase, 
and shut them down in the `post-integration-test` 
phase.


To start the required containers for local development purposes, 
install [Docker Desktop](https://www.docker.com/products/docker-desktop) and run the following:

```
docker-compose -f pipelines/src/main/docker/ala-nameservice.yml up -d
docker-compose -f pipelines/src/main/docker/solr8.yml up -d
```

To shutdown, run the following:
```
docker-compose -f pipelines/src/main/docker/ala-nameservice.yml kill
docker-compose -f pipelines/src/main/docker/solr8.yml kill
```

Note: The docker containers that are ran as part of the maven build run on different 
ports to those specified in the docker compose files `pipelines/src/main/docker`. 
This was a deliberate choice allow developers to run integration tests in IDEs while developing pipelines,
and then run maven builds on the same machine without port clashes.


## Code style and tools

For code style and tool see the [recommendations](https://github.com/gbif/pipelines#codestyle-and-tools-recommendations) on the GBIF pipelines project. In particular, note the project uses Project Lombok, please install Lombok plugin for Intellij IDEA.

`avro-tools` is recommended to aid to development for quick views of AVRO outputs. 
This can be installed on Macs with [Homebrew](https://brew.sh/) like so:

```
brew install avro-tools
```
