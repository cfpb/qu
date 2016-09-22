# Changelog

## v1.1.13 - 2016-09-22

 - Frontend improvements to pagination and limits for HTML format.
 - Fixes SSL bug when retrieving dependencies.

## v1.1.12 - 2016-04-08

 - Lock down front-end dependencies using `npm shrinkwrap` (c962a27)

## v1.1.11 - 2016-02-08

 - Validate `$page` argument (a44a85b)
 
## v1.1.10 - 2015-12-29

 - Disables pagination links in HTML view when they should not be clickable (43516f9)
 - No longer sends statsd metrics for responses with 4XX status codes (37e6d8a)

## v1.1.7 - 2015-09-18

### Added

 - Do not escape html in dataset description text when browsing the html api
 
## v1.1.6 - 2015-07-27

### Added

 - Logging to print out raw result of aggregation
 - For data load, add assertion that .csv file exists before processing
 
## v1.1.5 - 2014-09-19

### Added

 - Logging to print out aggregation id if use query matches pre-built aggregation. 

## v1.1.4 - 2014-08-26

### Removed

 - Disabled the use of large offsets, which are extremely inefficient in mongo
   - Offset must be less than 10,000 in HTML requests (7b8d9a9)
   - Remove the "last" button in the website pagination (ae91e39)

### Fixed

 - Fix loading of derived slices, which had incorrect field titles (aedd275)
 - Drakefile for [cfpb/api](https://github.com/cfpb/api) no longer fails in Vagrant environment due to unzip/7z issues (9ecdbe6)
 - Fixed mistake in download instructions (c55236c)

## v1.1.3 - 2014-07-24

### Added

 - All incrementing metrics, such as cache hit, now end in `.count` (0e366c4)
   - This will require any graphite dashboards to be updated.

### Fixed

 - Data loader uses `allowDiskUse` for derived slices. Without this, errors ensue. (80ebb01)
 - Fix 404 error for lein in vagrant provisioning (0c22dd7)

## v1.1.2 - 2014-06-13

### Added
 - MongoDB 2.6 now required. Aggregations now use the Mongo Aggregation Framework and no longer use MapReduce (ca9940c)
    - Existing cached aggregations will need to be removed and recreated
 - Liberator tracing to dev environment (efbbf5a)
 - More integration tests (6c7b5b8)
 - Support for new style configuration file (fc06091)
 - Metrics around open/close/cancel stream channels (92d2840)


### Removed
 - Support for MongoDB 2.4. Aggregations now require MongoDB 2.6 (ca9940c)
 - JDK 6 no longer a testing target in TravisCI (94de20b)
 _ "_id" is no longer included in aggregation results (661120e)

### Fixed

 - Sorting on aggregations (1f04252)
 - StatsD port can be passed from environment variable without validation error (92d2840)
 - One-off error in HTML view for query (df51ed3)

### Security

 - DB authentication details no longer printed in logs if environment is not "DEV" (7688434)


## v0.9.2

### Major additions

* Added a query cache to cache all aggregations
* Added 2012 HMDA data
* Added multiple-column joins for concept loading

### Minor changes

* Added `ez-load-` functions to `cfpb.qu.loader` to break loading from a monolithic task to smaller ones
* Changed public domain statement from Unlicense to Creative Commons 0.
* Added route-generation through `route-one` library
