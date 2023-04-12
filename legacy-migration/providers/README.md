# Legacy Service Migration for Providers

Provider Legacy Service Migration holds the migration scripts for migrating providers from CMR Legacy Service to CMR. 

The data migrated are all of the providers from legacy services.

The data calls the legacy services REST api parses those values into metadata documents inside of a subdirectory called providerMetadata. These can then be ingested into the CMR utilizing the new providers interface. This will enable us to store metadata on these providers and change this metadata by versioning a schema against it.

# Development Environment

## Prerequisites
* [Python](https://www.python.org/)

## Installation
* brew install python3

## Environment Variables
Due to needing system tokens to be able to retrieve all provider data please align your env variables in the following format where env is either uat, sit, or prod. Alternatively you may use ops instead of prod if you prefer that
* export CMR_<ENV>_TOKEN=<SYSTEM_TOKEN_FOR_CMR>

* Install the needed python packages using pip
pip install validators

## Ingesting into local CMR:
To validate the generated metadata you must have a running CMR instance. Please see https://github.com/nasa/Common-Metadata-Repository for how to setup CMR and what dependencies are needed. Alternatively you can validate the metadata a different way if you prefer. Note that you should of course reset your CMR between runs where you ingest all of the provider metadata otherwise you will receive errors from CMR that you are trying to ingest duplicate providers.

## Run Migration Without Ingesting

Copy this ./provider directory locally. Make sure that you are connected to the VPN for SIT and UAT migrations

`python3 migrate.py --env sit`

## Run Migration over a specific CMR environment

We should pass a flag to indicate which cmr env we want to pass. This will also align your system token for the given env as long as it was named in the proper way (see section on environment variables)

This example will generate the provider metadata for providers in uat
`python3 migrate.py --env uat`

Note: that if you don't specify an env such as the call below;the default is the SIT environment
`python3 migrate.py`


## Run Migration With Ingesting

We pass a flag for the migration script to try to ingest the provider metadata documents into a CMR env
Note: this is always done locally for validation purposes and so a CMR env must be running on your machine.

`python3 migrate.py --ingest`


## Run Migration With logging levels
Logging levels for user cmd line output can be specified by passing the --logging or -log flags. Please only use valid logging allowed by the python logging library:
debug, warning, info, or error. The default logging is info if one is not specified

## License

Copyright © 2023 NASA
