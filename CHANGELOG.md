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

* CMR-1860 As a client user, I want to be able to retrieve UMM JSON with paleo temporal coverage
  * Updated UMM JSON schema to 1.3 which changed singular PaleoTemporalCoverage to plural PaleoTemporalCoverages

## 1.52

* CMR-1876 - Indexer needs to index full hierarchy for locations
  * Configuration Patch Dependency: None
  * Deployment Impacts:
    * Requires update of indexes and reindexing of collections.
* CMR-2904 - Added ACL Searching
  * Added ACL index and indexing of ACLs. The ACL index will automatically be created as part of migrations during a deployment.
* CMR-3008 - Create collection index with new shard configuration and index to multiple indexes
  * Configuration Patch Dependency: Yes
    * Configuration patch must be installed first to add the elastic-collection-v2-index-num-shards parameter.
  * Deployment Impacts:
    * Requires update of indexes and reindexing of collections. Note that collection indexing will be broken until update-indexes has been called.
    * Create a new alias for the collection index used by search.
      * See ***REMOVED*** for instructions.
* CMR-2912 - Whitespace trimming of Entry title
  * Whitespace is trimmed from entry titles during indexing.
  * Deployment Impacts:
    * Requires reindexing of collections

## 1.53

* CMR-2186, CMR-2187, CMR-2188, CMR-2189
  * Added Version 2 facet response. No impacts to configuration or deployments.
* CMR-3009
  * Added alias for searching the latest collections index.
  * Automated creation of the index.
  * The alias was manually added as part of the deployment of sprint 52.

## 1.54

* CMR-3162 - Removed collection-setting-v1 index from index set.
  * Deployment Impacts
    * Requires update of indexes (no need to reindex collections).
* CMR-3006, CMR-3128, CMR-3129, CMR-3130, CMR-3131, CMR-3132 - Humanized fields
  * Deployment Impacts
    * Requires update of indexes
    * Requires reindexing of collections

## 1.55

* CMR-3003 - Sorting humanized facets by priority
  * Deployment Impacts
    * Requires update of indexes
