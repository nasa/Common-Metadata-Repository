# Developers Guide
The contents fo this document are meant to help a developer understand the system requirements, coding guidelines, and operation of the CMR needed to work on it as an active contributer.


**Table of content:**
 - [Technical Stack](#technical-stack)
 - [Running CMR](#running-the-cmr)


## Technical Stack
The CMR utilizes the following technologes to work:
- [Java](https://docs.oracle.com/en/java/)
- [Clojure](https://clojure.org)
- [Lieningen](https://leiningen.org)
- [Docker](https://www.docker.com)
- [Elastic](https://www.elastic.co)
- [Oracle](https://www.oracle.com/database)


## Clojure & Leiningen

### Clojure

#### What is Clojure?
Clojure is a dynamic development environment where you interact with your program while you write it, growing and adding to it while it’s running. To work with Clojure you need an editor that supports evaluation in source files and structural editing (working with nested forms in addition to character editing).

#### Why we use it
Clojure meets its goals by: embracing an industry-standard, open platform - the JVM; modernizing a venerable language - Lisp; fostering functional programming with immutable persistent data structures; and providing built-in concurrency support via software transactional memory and asynchronous agents. The result is robust, practical, and fast.

### Leiningen

#### What is Leiningen?
Leiningen is a build automation and dependency management tool for the simple configuration of software projects written in the Clojure programming language.

#### Why we use it
Leiningen is the easiest way to use Clojure. With a focus on project automation and declarative configuration, it gets out of your way and lets you focus on your code.



## Running the CMR
The CMR is a system consisting of many services. The services can run
individually or in a single process. Running in a single process makes
local development easier because it avoids having to start many different
processes. The `dev-system` project allows the CMR to run from a single REPL
or JAR file. If you are developing a client against the CMR you can build and
run the entire CMR with no external dependencies from this JAR file and use
that instance for local testing. The sections below contain instructions for
running the CMR as a single process or as many processes.

### Using the `cmr` CLI Tool

This project has its own tool that is able to do everything from initial setup to
running builds and tests on the CI/CD infrastructure. To use the tool
as we do below, be sure to run the following from the top-level CMR directory:

```sh
export PATH=$PATH:`pwd`/bin
source resources/shell/cmr-bash-autocomplete
```

(If you use a system shell not compatible with Bash, we accept Pull Requests for
new shells with auto-complete.)

To make this change permanent:

```sh
echo "export PATH=\$PATH:`pwd`/bin" >> ~/.profile
echo "source `pwd`/resources/shell/cmr-bash-autocomplete" >> ~/.profile
```

### Building and Running CMR Dev System in a REPL with CMR CLI tool

1. `cmr setup profile` and then update the new `./dev-system/profiles.clj` file.
   it will look something like this:
   ``` clojure
   {:dev-config {:env {:cmr-metadata-db-password "<YOUR PASSWORD HERE>"
                       :cmr-sys-dba-password "<YOUR PASSWORD HERE>"
                       :cmr-bootstrap-password "<YOUR PASSWORD HERE>"
                       :cmr-ingest-password "<YOUR PASSWORD HERE>"
                       :cmr-urs-password "<YOUR PASSWORD HERE>"}}}
   ```

2. `cmr setup dev`
3. `cmr start repl`
4. Once given a Clojure prompt, run `(reset)`

Note that the `reset` action may take a while, not only due to
the code reloading for a large number of namespaces, but for bootstrapping
services as well as starting up worker threads.

### Building and Running CMR Dev System from a JAR

Assuming you have already run the above steps (namely `cmr setup dev`), to
build and run the default CMR development system (`dev-system`) from a
`.jar` file:

1. `cmr build uberjars`
2. `cmr build all`
3. `cmr start uberjar dev-system` will run the dev-system as a background task


### Building and Running separate CMR Applications

The following will build every application but will put each JAR into the
appropriate `target` directory for each application. The command shown in step
3 is an example. For the proper command to start up each application, see the
`Applications` section below. Note: You only need to complete steps 1 and 2 once.

1. `cmr build uberjar APP`
2. `cmr run uberjar APP`

Where `APP` is any supported CMR app. You can double-tap the `TAB` key on
your keyboard to get the `cmr` tool to show you the list of available apps
after entering `uberjar` in each step above.

Note: building uberjars will interfere with your repl. If you want to use your repl post-build you will need to,
`rm -f ./dev-system/target/`


