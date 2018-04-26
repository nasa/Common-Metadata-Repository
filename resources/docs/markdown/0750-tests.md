# Tests

## Test Types



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
$ lein ltest :system
```

The default behaviour of `lein ltest` runs both unit and integration tests. To
run all tests, use `lein ltest :all`.
