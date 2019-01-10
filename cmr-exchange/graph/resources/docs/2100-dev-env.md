#  • Development Environment


**Contents**

* nREPL
* Proto-REPL
* System
* Code Reloading
* Convenience Functions


## nREPL

To write new code for CMR Graph, you'll need to have the infrastructure running
as described in the "Setup" docs, and then you'll want to start up a REPL. The
primary development REPL for CMR Graph is a terminal-based nREPL:

```
$ lein repl
```
```text
               ____
            ,dP9CGG88@b,
          ,IIIIYICCG888@@b,
         dIIIIIIIICGG8888@b
        dCIIIIIIICCGG8888@@b
        GCCIIIICCCGGG8888@@@
        GGCCCCCCCGGG88888@@@
        GGGGCCCGGGG88888@@@@\
        Y8GGGGGG8888888@@@@P \
         Y88888888888@@@@@P \ \
         `Y8888888@@@@@@@P   \  \
           |`@@@@@@@@@P'      \  \
           | .  """"           \   \       _....___
           ' .                   \   \  .d$#T!!!~"#*b.
           ' |                    \   d$MM!!!!~~~     "h
           " |                     \dRMMM!!!~           ^k
           = "                     $RMM!!~                .__
  ____   ____________            ________________  ______ |  |__
_/ ___\ /     \_  __ \  ______  / ___\_  __ \__  \ \____ \|  |  \
\  \___|  Y Y  \  | \/ /_____/ / /_/  >  | \// __ \|  |_> >   Y  \
 \___  >__|_|  /__|            \___  /|__|  (____  /   __/|___|  /
     \/      \/            __ /_____/            \/|__|        \/
      .X+.   .      ___----     'k~~                        :
    .Xx+-.     . '''  ____----""" 3>                        F
    XXx++-..     --'''            9>                       F
    XXxx++--..                     "i                    :"
    `XXXxx+++--'                     t.                .P
      `XXXxxx'                         #c.          .z#
         ``                               ^#*heee*#"


nREPL server started on port 53287 on host 127.0.0.1 - nrepl://127.0.0.1:53287
REPL-y 0.3.7, nREPL 0.2.12
Clojure 1.9.0
Java HotSpot(TM) 64-Bit Server VM 1.8.0_161-b12
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
 Javadoc: (javadoc java-object-or-class-here)
    Exit: Control+D or (exit) or (quit)
 Results: Stored in vars *1, *2, *3, an exception in *e

[cmr.graph.dev] λ=>
```


## Proto-REPL

For those using the Atom editor, CMR Graph also offers support for Proto-REPL.
To this end, the `user` ns has been provied at `dev-resources/src/user.clj`. It
is essentially a wrapper for `cmr.graph.dev` (and can be removed once Proto-REPL
supports configurable starting namespaces, as `lein` does).


## System

With the REPL up, you're ready to bring up the CMR Graph system components:

```clj
[cmr.graph.dev] λ=> (startup)
```

This will start the following CMR Graph components:

* Configuration
* Logging
* A Neo4j connection
* An Elasticsearch connection
* The CMR Graph HTTP server for the REST API

as the log messages show:

```
2018-03-09T17:13:42.947 [nREPL-worker-0] INFO c.g.c.config:35 - Starting config component ...
2018-03-09T17:13:42.987 [nREPL-worker-0] INFO c.g.c.logging:22 - Starting logging component ...
2018-03-09T17:13:42.988 [nREPL-worker-0] INFO c.g.c.neo4j:21 - Starting Neo4j component ...
2018-03-09T17:13:42.992 [nREPL-worker-0] INFO c.g.c.elastic:21 - Starting Elasticsearch component ...
2018-03-09T17:13:42.993 [nREPL-worker-0] INFO c.g.c.httpd:22 - Starting httpd component ...
```

A convenience function has been provided for use in the REPL which returns
the dynamic var where the system state is stored:

```clj
[cmr.graph.dev] λ=> (system)
```

When you're done, you can shutdown the system and all of its components with
this:

```clj
[cmr.graph.dev] λ=> (shutdown)
```


## Code Reloading

You can reload changed code in the REPL without leaving it.

If you don't have a running system, the quickest way to do this is with
`refresh`. However, this should not be used with a running system.

If you have starte the system, then you'll want to use `reset`. This stops a
running system, reloads the changed namespaces, and then restarts the system.


## Convenience Functions

Convenience wrappers have been provided for several CMR Graph API functions in
the REPL dev namespaces, automatically pulling the information they require
(e.g., Neo4j conection data) from the system data structure:

```clj
[cmr.graph.dev] λ=> (pprint (search-movie "Matr"))
({:movie
  {:tagline "Welcome to the Real World",
   :title "The Matrix",
   :released 1999}}
 {:movie
  {:tagline "Free your mind",
   :title "The Matrix Reloaded",
   :released 2003}}
 {:movie
  {:tagline "Everything that has a beginning has an end",
   :title "The Matrix Revolutions",
   :released 2003}})
nil
```

```clj
[cmr.graph.dev] λ=> (pprint (get-movie "The Matrix"))
{"title" "The Matrix",
 "cast"
 [{:role ["Emil"], :name "Emil Eifrem", :job "acted"}
  {:role nil, :name "Joel Silver", :job "produced"}
  {:role nil, :name "Lana Wachowski", :job "directed"}
  {:role nil, :name "Lilly Wachowski", :job "directed"}
  {:role ["Agent Smith"], :name "Hugo Weaving", :job "acted"}
  {:role ["Morpheus"], :name "Laurence Fishburne", :job "acted"}
  {:role ["Trinity"], :name "Carrie-Anne Moss", :job "acted"}
  {:role ["Neo"], :name "Keanu Reeves", :job "acted"}]}
nil
```

```clj
[cmr.graph.dev] λ=> (pprint (get-movie-graph 100))
{:nodes
 ({:title "Apollo 13", :label :movie}
  {:title "Tom Hanks", :label :actor}
  {:title "Kevin Bacon", :label :actor}
  ...)
 :links
 ({:target 0, :source 1}
  {:target 0, :source 2}
  {:target 0, :source 3})}
nil
```

Additional convenience functions provided in the `cmr.graph.dev` namespace,
for use in the REPL:

* `banner`
* `current-health`
* `refresh`
* `reset`


<!-- Named page links below: /-->

[repl]: https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/images/repl-screen.png
