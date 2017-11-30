# cmr.dev.env.manager

[![Build Status][travis-badge]][travis]
[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]
[![Clojure version][clojure-v]](project.clj)

[![][logo]][logo-large]

*An Alternate Development Environment Manager for the CMR*


## Setup

1. Ensure that you have the Common Metadata Repository code base cloned to the
   same directory that the development environment manager is cloned to:
    ```
    $ git clone git@github.com:nasa/Common-Metadata-Repository cmr
    $ git clone git@github.com:cmr-exchange/dev-env-manager cmr-dev-env-manager
    ```
1. Go into the cloned `cmr` directory, and set up Oracle libs (see the `README`
   in `cmr/oracle-lib`).
1. From the `cmr` directory, run `lein install-with-content!`.
1, Change to the `cmr-dev-env-manager` directory.


## Usage

Run `lein repl`, at which point you will see output similar to the following:

```
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

```
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

To bring up a dev system, complete with all running services:

```clj
[cmr.dev.env.manager.repl] λ=> (run)
```

Note that each service is started in its own OS process. For more information
on this, see the architecture section below.


## Architecture Overview

TBD


## Background

For information on what problem this project attempts to define and how it was
originally planned, see the [wiki pages](../wiki).


## License

Apache License, Version 2.0.


<!-- Named page links below: /-->

[travis]: https://travis-ci.org/cmr-exchange/dev-env-manager
[travis-badge]: https://travis-ci.org/cmr-exchange/dev-env-manager.png?branch=master
[logo]: resources/images/cmr-dev-env-mgr.png
[logo-large]: resources/images/cmr-dev-env-mgr-large.png
[tag-badge]: https://img.shields.io/github/tag/cmr-exchange/dev-env-manager.svg
[tag]: https://github.com/cmr-exchange/dev-env-manager/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.8.0-blue.svg
[jdk-v]: https://img.shields.io/badge/jdk-1.7+-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-dev-env-manager
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-dev-env-manager.svg
