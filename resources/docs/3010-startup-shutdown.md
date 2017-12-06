#  • Startup & Shutdown


## Firing Up the REPL

With the setup done, you are ready to run `lein repl`. Having done that, you
will see output similar to the following:

```code
    Development Environment Manager
    .....................................................................................
    ::'######::'##::::'##:'########::::::::'##:::::::::::::::::::::::::::::::::::::::::::
    :'##... ##: ###::'###: ##.... ##::::::'##:::dMMMMb::::::::dMMMMMP::::::dMMMMMMMMb::::
    : ##:::..:: ####'####: ##:::: ##:::::'##:::dMP.VMP:::::::dMP....::::::dMP"dMP"dMP::::
    : ##::::::: ## ### ##: ########:::::'##:::dMP:dMP.::::::dMMMP::::::::dMP.dMP.dMP.::::
    : ##::::::: ##. #: ##: ##.. ##:::::'##:::dMP:aMP.:amr::dMP..:::amr::dMP:dMP:dMP.:amr:
    : ##::: ##: ##:.:: ##: ##::. ##:::'##:::dMMMMP.::dMP::dMMMMMP:dMP::dMP:dMP:dMP.:dMP::
    :. ######:: ##:::: ##: ##:::. ##:'##::::......:::..:::......::..:::..::..::..:::..:::
    ::......:::..:::::..::..:::::..::..::::::::::::::::::::::::::::::::::::::::::::::::::

    for NASA's Earthdata Common Metadata Repository

    Loading ...
```

After a few seconds, the REPL will be loaded and ready to use:

```clj
nREPL server started on port 54636 on host 127.0.0.1 - nrepl://127.0.0.1:54636
REPL-y 0.3.7, nREPL 0.2.12
Clojure 1.8.0
Java HotSpot(TM) 64-Bit Server VM 1.8.0_60-b27
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
 Javadoc: (javadoc java-object-or-class-here)
    Exit: Control+D or (exit) or (quit)
 Results: Stored in vars *1, *2, *3, an exception in *e

[cmr.dev.env.manager.repl] λ=>
```


## Startup

To bring up a dev system, complete with all enabled running services:

```clj
[cmr.dev.env.manager.repl] λ=> (startup)
```

Note that each service is started in its own OS process. For more information
on this, see the architecture section below.


## Shutdown

Conversely, to stop all components -- including CMR services -- and return the
system to an initial, `nil` state:

```clj
[cmr.dev.env.manager.repl] λ=> (shutdown)
```
