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

## Running Tests

There are several ways in which you can run tests with dev-system. The
top-level CMR `README.md` offers some instructions on this point, including
switching between `:in-memory` mode (the default) and `:external` (see the
section "Testing CMR" in that README for more details).

Furthermore, there is a second (and optional) test runner you can use for
running suites, test namespaces, and individual test functions. See the
docstring for `run-suites` in `dev/user.clj` for usage information.

### Testing with a Local SQS/SNS

If you would like to test messaging against a local clone of SQS/SNS, then you
can do the following:

* Be sure that Docker is installed on your system and running
* Run `lein start-sqs-sns`
* From the shell where you will start the REPL, you will need the
  `CMR_SNS_ENDPOINT` and `CMR_SQS_ENDPOINT` environment variables set;
  in most cases you will want both of these set to `http://localhost:4100`
* You will also need to set the env var
  `CMR_SQS_EXTEND_POLICY_REMAINING_EXCHANGES` to `false` and to set the
  standard AWS credential environment variables, `AWS_ACCESS_KEY_ID` and
  `AWS_SECRET_ACCESS_KEY` (it doesn't matter what the actual values are).
* For CMR to use the local SQS/SNS, it needs to have the
  `CMR_DEV_SYSTEM_QUEUE_TYPE` environment variable set to "aws".
* Start the REPL, e.g. `lein repl`.
* Reset the REPL (which reloads the code and starts up the system components):
  `(reset)`

If you want to do any debugging of the local service, you'll probably want to
install the AWS cli. On a Mac, just do `brew install awscli`. You can use this
to make sure that the local SQS/SNS has started:

```
$ aws --endpoint-url http://localhost:4100 sqs list-queues
```

## Setting up profiles.clj

As noted above, you will need to create a `profiles.clj` in the `dev-system`
directory. This will provide configuration/authentication information required
by CMR for a local, in-memory "deployment". You will need to contact a core
CMR developer for the appropriate values for each key in `profiles.clj`.

## Security of `dev-system`

`dev-system` is meant to be used for testing only. It provides a control API
that allows unrestricted access to shutdown the system, evaluate arbitrary
code, remove all data, etc.

## Update of umm-cmn-json-schema.json

We need to keep the latest version of the umm-cmn-json-schema.json in sync for all concept types. When the umm-cmn-json-schema.json is updated for one concept type, the corresponding files should be updated for all concept types.

## License

Copyright © 2014-2017 NASA
