# CMR Change Log

First Created as part of 1.49

Items to include in each issue

* Configuration Patch Dependency
  * Are there any configuration changes required?
* Deployment Impacts
  * Does the feature require any special work as part of deployment in the new environment?
  * Was there a spatial library change requiring a spatial plugin deployment.
* API Impacts
  * Are there any changes that could impact clients?


## 1.XX

* CMR-1234 - Added made up example entry into changelog
  * Configuration Patch Dependency:
    * Requires cubby connection config information for the Ingest application
  * Deployment Impacts
    * Update Indexes and Reindex all Collection
      * Will need to reindex all collections to start using new feature. If collections have not been reindexed everything will continue to work fine.
    * Deploy Spatial Plugin
      * We made performance improvements to polygon intersections

## 1.50

* CMR-2748 - Allow searching for collections by the granules' temporal range
  * Configuration Patch Dependency: None
  * Deployment Impacts:
    * Requires update of indexes and reindexing of collections.
    * The collection index was changed to add two new fields for granule start and end date.

## 1.51

* CMR-2745 As a client user, I want my facet search results to be sorted by relevance
  * Collection searches with any of the following parameters will now be scored: platform, instrument, sensor, two_d_coordinate_system_name, science_keywords, project, processing_level_id, data_center, archive_center