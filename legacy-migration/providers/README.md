# Legacy Service Migration for Providers

Provider Legacy Service Migration holds the migration scripts for migrating providers from CMR Legacy Service to CMR. 

The data migrated are all of the providers from legacy services.

The data calls the legacy services REST api parses those values into metadata documents inside of a subdirectory called providerMetadata. These can then be ingested into the CMR utilizing the new providers interface. This will enable us to store metadata on these providers and change this metadata by versioning a schema against it.

# Development Environment

## Prerequisites
* [Python](https://www.python.org/)

## Installation and Environment Variables
* brew install python3
* export ACCESS_TOKEN=<user_token_for_CMR>

## Run Migration

Copy this ./provider directory locally. Make sure that you are connected to the VPN for SIT and UAT migrations

`python3 migrate.py`

## License

Copyright © 2023 NASA