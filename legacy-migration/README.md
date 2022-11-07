# Legacy Serivce Migration

Legacy Service Migration holds the migration scripts for migrating ordering related data from CMR Legacy Services to CMR. 

The data migrated includes: DataQualitySummary, OptionDefinition, ServiceOptionDefinition, ServiceEntry and the associations among these data and CMR collections. 

The data is read from Legacy Service tables in Oracle and ingested into CMR via the generic concept ingest API. The associations are migrated via reading the corresponding Legacy Service associations tables in Oracle and make the same associations in CMR using the CMR generic concepts associations API.

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


## License

Copyright © 2022 NASA
