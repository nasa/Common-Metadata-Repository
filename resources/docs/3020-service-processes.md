#  • Inspecting Process-based Services

Once the system is up and running, a developer may wish to examine the managed
processes. This can be done with various utility functions provided in the
default `repl` namespace.

In particular, The D.E.M. REPL provides a means of examining the processes
associated with a given CMR service. Note, though, that these functions
are only useful in a running system (since it's only a running system that
has started services in it).

Get the object for a CMR service's managed process:

```clj
[cmr.dev.env.manager.repl] λ=> (get-process :mock-echo)
```
```clj
{:out #object[java.lang.UNIXProcess$ProcessPipeInputStream 0x285add58
       "java.lang.UNIXProcess$ProcessPipeInputStream@285add58"],
 :in #object[java.lang.UNIXProcess$ProcessPipeOutputStream 0x12fd7d5d
      "java.lang.UNIXProcess$ProcessPipeOutputStream@12fd7d5d"],
 :err #object[java.lang.UNIXProcess$ProcessPipeInputStream 0x1977abda
       "java.lang.UNIXProcess$ProcessPipeInputStream@1977abda"],
 :process #object[java.lang.UNIXProcess 0x44d8167a "java.lang.UNIXProcess@44d8167a"],
 :out-channel #object[clojure.core.async.impl.channels.ManyToManyChannel 0x3a96b3a4
               "clojure.core.async.impl.channels.ManyToManyChannel@3a96b3a4"],
 :err-channel #object[clojure.core.async.impl.channels.ManyToManyChannel 0x43f8263e
               "clojure.core.async.impl.channels.ManyToManyChannel@43f8263e"]}
```

Get a service's PID:

```clj
[cmr.dev.env.manager.repl] λ=> (get-process-id :mock-echo)
```

```clj
28046
```

Get a managed service's descendant processes:

```clj
[cmr.dev.env.manager.repl] λ=> (get-process-descendants :mock-echo)
```

```clj
(28061 28107)
```

Note that when a service is stopped, this is what is used to identify all the
related service processes that need to be terminated in addition to the main,
parent process for the service.
