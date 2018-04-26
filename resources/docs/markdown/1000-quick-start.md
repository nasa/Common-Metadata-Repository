# Quick Start

With dependencies installed and repo cloned, switch to the project directory
and start the REPL:

```
$ lein repl
```
```text

 __________ ___   ___ _________
/   /_____/|   \ /   |    _o___)                       OPeNDAP support in
\___\%%%%%'|____|____|___|\____\                           NASA Earthdata
 `BBBBBBBB' `BBBBBBB' `BB' `BBB'
 _________ _________  __________ ___    __ _________  _______  _________
/    O    \    _o___)/   /_____/|   \  |  |     O   \/   O   \|    _o___)
\_________/___|%%%%%'\___\%%%%%'|____\_|__|_________/___/%\___\___|%%%%%'
 `BBBBBBB' `B'        `BBBBBBBB' `BBBBBBB' `BBBBBBB'`BB'   `BB'`B'



nREPL server started on port 52191 on host 127.0.0.1 - nrepl://127.0.0.1:52191
REPL-y 0.3.7, nREPL 0.2.12
Clojure 1.8.0
Java HotSpot(TM) 64-Bit Server VM 1.8.0_91-b14
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
 Javadoc: (javadoc java-object-or-class-here)
    Exit: Control+D or (exit) or (quit)
 Results: Stored in vars *1, *2, *3, an exception in *e

[cmr.opendap.dev] λ=>
```

Then bring up the system:

```clj
(startup)
```
```
2018-04-07T15:26:54.830 [nREPL-worker-0] INFO cmr.opendap.components.config:62 - Starting config component ...
2018-04-07T15:26:54.837 [nREPL-worker-0] INFO cmr.opendap.components.logging:22 - Starting logging component ...
2018-04-07T15:26:54.845 [nREPL-worker-0] INFO cmr.opendap.components.caching:56 - Starting caching component ...
2018-04-07T15:26:54.855 [nREPL-worker-0] INFO cmr.opendap.components.httpd:23 - Starting httpd component ...
```

Hack away to your heart's content (or use `curl` to hit the REST API at
http://localhost:3012; see `cmr.opendap.rest.route` for the available resources).

When done:

```clj
(shutdown)
```
