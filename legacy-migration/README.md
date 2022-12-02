# Legacy Serivce Migration

Legacy Service Migration holds the migration scripts for migrating ordering related data from CMR Legacy Service to CMR. 

The data migrated includes: DataQualitySummary, OptionDefinition, ServiceOptionDefinition and the associations among these data and CMR collections. 

The data is read from Legacy Service tables in Oracle and ingested into CMR via the generic concept ingest API. DataQualitySummary is migrated into data-quality-summary concept, and both OptionDefinition and ServiceOptionDefinition are migrated into order-option concept. The associations are migrated via reading the corresponding Legacy Service associations tables in Oracle and service associations between collections and UMM-S concepts with the concept id of related order-option concept as association data.

# Development Environment

## Prerequisites
* [Python](https://www.python.org/)

## Installation and Environment Variables
* brew install python3
* pip3 install oracledb --upgrade
* export MIGRATE_USER=<oracle_db_username>
* export MIGRATE_PWD=<oracle_db_password>
* export ACCESS_TOKEN=<user_token_for_CMR>

## Run Migration
Make sure you can access Oracle database (via tunnel to localhost:1521) in the corresponding CMR envirionment that you want to perform the migration.

`python3 migrate.py`

By default, OrderOptionDefinitions and ServiceOptionDefinitions will not be ingested into CMR again if a matching order-option concept already exist in CMR. User can use the `-f` option to force migration of OrderOptionDefinitions and ServiceOptionDefinitions regardless of if they already exist in CMR or not.

## License

Copyright © 2022 NASA
