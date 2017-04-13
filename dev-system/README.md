# cmr-dev-system

`dev-system` combines the separate microservices of the CMR into a single
application to make it simpler to develop.

## Setting up for local development.

While a full production deployment of CMR depends upon various services (e.g.,
databases, message queues, portions of AWS, etc.), for development purposes it
is possible to run CMR locally without these.

To do so, perform the following:

1. Clone the repo: `git clone git@github.com:nasa/Common-Metadata-Repository.git cmr`
2. Switch to the working dir: `cd cmr`
3. Copy `profiles.example.clj` to `profiles.clj`
4. Configure `profiles.clj` (see below)
3. Run the local setup script: `dev-system/support/setup_local_dev.sh`

## Setting up profiles.clj

As noted above, you will need to create a `profiles.clj` in the `dev-system`
directory. This will provide configuration/authentication information required
by CMR for a local, in-memory "deployment". You will need to contact a core
CMR developmer for the appropriate values for each key in `profiles.clj`.

## Security of `dev-system`

`dev-system` is meant to be used for testing only. It provides a control API
that allows unrestricted access to shutdown the system, evaluate arbitrary
code, remove all data, etc.

## License

Copyright © 2014, 2017 NASA
