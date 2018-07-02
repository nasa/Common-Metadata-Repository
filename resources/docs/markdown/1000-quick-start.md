# Quick Start

With dependencies installed and repo cloned, switch to the project directory
and start the REPL:

```
$ lein repl
```

Then bring up the system:

```clj
[cmr.opendap.dev] λ=> (startup)
```
```
2018-04-07T15:26:54.830 [nREPL-worker-0] INFO cmr.opendap.components.config:62 - Starting config component ...
2018-04-07T15:26:54.837 [nREPL-worker-0] INFO cmr.opendap.components.logging:22 - Starting logging component ...
2018-04-07T15:26:54.845 [nREPL-worker-0] INFO cmr.opendap.components.caching:56 - Starting caching component ...
2018-04-07T15:26:54.855 [nREPL-worker-0] INFO cmr.opendap.components.httpd:23 - Starting httpd component ...
```

Hack away to your heart's content (or use `curl` to hit the REST API at
http://localhost:3013; see `cmr.opendap.rest.route` for the available resources).

When done:

```clj
[cmr.opendap.dev] λ=> (shutdown)
```
