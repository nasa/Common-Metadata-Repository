# cmr-dev-system

Dev System combines together the separate microservices of the CMR into a
single application to make it simpler to develop.

## Setting up for local development.

You can setup locally for running the CMR in memory by doing the following.
You'll need a URS username that's been granted access to the repository. After
this has completed you can start a REPL in dev-system.

1. git clone ***REMOVED***
2. cd cmr
3. dev-system/support/setup_local_dev.sh

## Setting up profiles.clj

You will need to create profiles.clj in dev-system in order to set multiple
environment variable passwords.  This can be accomplished by copying the
contents of profiles.example.clj into a new file called profiles.clj.  Make
sure to edit the CHANGE_ME values.

## Security of dev system

Dev system is meant to be used for testing only. It provides a control API
that allows unrestricted access to shutdown the system, evaluate arbitrary
code, remove all data, etc.

## License

Copyright © 2014 NASA
