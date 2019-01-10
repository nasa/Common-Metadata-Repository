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
2018-07-09T14:17:18.567 [nREPL-worker-12] INFO cmr.opendap.components.config:168 - Starting config component ...
2018-07-09T14:17:18.568 [nREPL-worker-12] INFO cmr.opendap.components.logging:16 - Starting logging component ...
2018-07-09T14:17:18.568 [nREPL-worker-12] INFO cmr.mission-control.components.pubsub:152 - Starting pub-sub component ...
2018-07-09T14:17:18.569 [nREPL-worker-12] INFO cmr.mission-control.components.pubsub:159 - Adding subscribers ...
2018-07-09T14:17:18.569 [nREPL-worker-12] INFO cmr.authz.components.caching:92 - Starting authz caching component ...
2018-07-09T14:17:18.571 [nREPL-worker-12] INFO cmr.opendap.components.caching:127 - Starting concept caching component ...
2018-07-09T14:17:18.571 [nREPL-worker-12] INFO cmr.opendap.components.concept:149 - Starting concept component ...
2018-07-09T14:17:18.571 [nREPL-worker-12] INFO cmr.opendap.components.auth:195 - Starting authorization component ...
2018-07-09T14:17:18.571 [nREPL-worker-12] INFO cmr.opendap.components.httpd:17 - Starting httpd component ...
```

Hack away to your heart's content (or use `curl` to hit the REST API at
http://localhost:3013; see `cmr.opendap.app.routes.rest.*` for the available resources).

When done:

```clj
[cmr.opendap.dev] λ=> (shutdown)
```
