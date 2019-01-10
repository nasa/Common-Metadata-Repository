# Setup


**Contents**

* Start
* Stop


### Start

To start cmr-opendap from the command line:

```
$ lein start-cmr-opendap
```

If you are interested in using the REPL during development, you can do this:

```
$ lein repl
```

And then, once the REPL is up, you can start the system:

```
[cmr.opendap.dev] λ=> (startup)
```

### Stop

If you started the system from the CLI, just use `^c` to terminate.

If you are in the REPL:

```
[cmr.opendap.dev] λ=> (shutdown)
```


### Reloading

If you have made code changes, and would like to load these and restart the system,

```
[cmr.opendap.dev] λ=> (reset)
```
