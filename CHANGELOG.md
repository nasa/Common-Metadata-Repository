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
    * Requires reindexing of collections

## 1.56

* CMR-3225 - Entry ID field indexed for collection keyword searches
  * Deployment Impacts
    * Requires reindexing of collections

## 1.58

* CMR-2493 and CMR-2674 - Update to Clojure 1.8 and use direct linking
  * Deployment Impacts
    * The Elasticsearch Spatial Plugin needs to be deployed.
* CMR-3260 - Allow dynamic update of humanizers
* CMR-2905 - The full acl data is indexed now.
  * No operator steps are required. The index will automatically be updated during database migrations

## 1.59

* CMR-3097 - Changed collection temporal indexing to use umm-spec-lib
  * Client Impacts
    * Collections without temporal info will be indexed with a default temporal start date of "1970-01-01T00:00:00". This will cause collections without temporal info being returned from a temporal search. This comes from the UMM directive that all collections should have temporal info. We will default the start date to "1970-01-01T00:00:00" if one is not provided.

## 1.60

* CMR-3350 - As an Ops user, I should have a way of determining via Splunk if an ingest request is new or updating an existing granule
  * Client Impacts
    * CMR changed http status code for ingesting a new concept from 200 to 201, updating or deleting an existing concept still returns http status code 200.
* CMR-3355 - Changed CMR_ENFORCE_GRANULE_UR_CONSTRAINT default to true
  * Client Impacts
    * CMR enabled unique granule ur validation during granule ingest so that ingest of granule with a granule ur that is used by a different non-deleted granule within the same provider will fail.
* CMR-3340 Translate UMM ISOTopicCategories to ISO 19115-2 (MENDS)
  * Updated UMM JSON schema to 1.7 which changed ISOTopicCategory type from string to ISOTopicCategoryEnum.
* CMR-2717 - Changed legacy services to read groups from CMR access control
  * Deployment Impacts
    * Needs config v341
    * Need to manually synchronize groups before deployment.
    * The CMR deployment must happen first.
* CMR-3387 - Include members in search response
  * Group search can now optionally include group members in a search response.
  * Deployment Impacts
    * Requires reindexing all groups after deployment
      * `curl -i -XPOST http://localhost:3011/access-control/reindex-groups?token=XXXX`

## 1.61

* CMR-3215 - Added granule validation to not allow a granule to change its parent collection
  * Client Impacts
    * Once a granule is created to reference a parent collection, the granule cannot be updated to reference a different collection as its parent collection.
    * Changed the error message format for status code 422 during ingest to the correct format.
* CMR-3286 - As a Client User, I can only retrieve provider level ACLs through search that I have permission to read.
  * Client Impacts
    * Provider ACLs will now only be searchable by users or user-types which are given permission to by a system ACL targeting ANY_ACL, by the provider ACL itself, or by a provider ACL targeting PROVIDER_OBJECT_ACL.
    * Read permission is special. A user with create, read, update, or delete permissions granted by an ACL will automatically be able to read that ACL.  This does not apply when the user is instead granted permission to an ACL via the governing management ACLs, where only explicit read permission applies.

## 1.64
* CMR-1354 - Implement temporal relevancy using a custom elastic groovy sort script.
  * Requires updates changes to elasticsearch.yml
  * Config sort-use-temporal-relevancy turns temporal relevancy on and off

## 1.66
* CMR-3156 - Added auto shutdown of CMR app when the app encounters oracle class error during startup.
  * Added configuration parameter CMR_SHUTDOWN_ON_ORACLE_CLASS_ERROR to control if the CMR app will be shut down if it encounters oracle class error during startup. It is default to true.
