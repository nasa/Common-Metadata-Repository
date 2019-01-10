# Tests


## Test Types

CMR OPeNDAP supports several different types of test:

* [unit](https://en.wikipedia.org/wiki/Software_testing#Unit_testing)
* [integration](https://en.wikipedia.org/wiki/Software_testing#Integration_testing)
* [system](https://en.wikipedia.org/wiki/Software_testing#System_testing)


## Example Usage

To run just the unit tests, use this command:

```
$ lein ltest :unit
```

Similarly, for just the integration tests:

```
$ CMR_SIT_TOKEN=`cat ~/.cmr/tokens/sit` lein ltest :integration
```

Just system tests:

```
$ CMR_SIT_TOKEN=`cat ~/.cmr/tokens/sit` lein ltest :system
```

The default behaviour of `lein ltest` runs both unit and integration tests:

```
$ CMR_SIT_TOKEN=`cat ~/.cmr/tokens/sit` lein ltest
```

To run all tests:

```
$ CMR_SIT_TOKEN=`cat ~/.cmr/tokens/sit` lein ltest :all
```


## Running Tests and System Together

If you've got a running REPL that you don't want to shutdown and would also
like to run the system or integration tests, you can use an environment
variable to ensure no conflicts with a port that's alredy bound:

```
HTTPD_PORT=5099 CMR_SIT_TOKEN=`cat ~/.cmr/tokens/sit` lein ltest :all
```
