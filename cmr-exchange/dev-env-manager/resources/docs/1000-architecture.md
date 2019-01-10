# Architecture

## Overview

### Over-arching Principles

This project has been implemented to keep APIs, services, general system-level
capabilities, and underlying functionality very cleanly separated. No mixing
of concepts. No over-loading of responsibilities.

This is accomplished in the mind through discipline, as well as socially by
feedback from other developers. This is accomplished in the code itself through
the use of simple functions and components to do their one declared job. Simply
put, if additional features are needed, separate functions and/or components
are created.


### A Peek at the Separation of Concerns

The architecture of the CMR D.E.M. reflects a separation of concerns split
along the following lines:

* **REPL** - A minimal set of namespaces is loaded into the REPL
    * The REPL environment is also where system-wide state is managed
    * Additionally, convenience functions are provided for managing elements in
      other levels of the architecture
* **Components** - Running code is managed in a "system" using "components",
  as defined by the Component library
    * Supporting D.E.M. services are defined as system components
    * CMR services are managed in a specialized system component that spawns
      the service in question in a separate OS process
* **Processes** - `lein` is used to start all CMR service processes
* **Configuration** - The D.E.M. `project.clj` file is used for configuration
  of all CMR service processes

For current CMR developers, use of this development environment should be
seamless: the only requirements are that:

* Your CMR repository clone directory be named `cmr`; and,
* Your CMR repository clone directory be a sibling directory to the clone of
  the D.E.M. repository.

For more information, see the [setup docs](2000-setup.html).


### Diagram

TBD


### Benefits

The approach used by this architecture fulfills the following development
vision:

* knowing each component provides the contributor with an understanding
  of most of the system
* knowing how each component is connected provides the remaining
  understanding
* this makes the following much, much easier:
    * developing new features
    * debugging existing ones
    * refactoring some or all of the project is actually possible (and
      should actually be easy to do)
* no hidden anything:
    * no implicit code or magic
    * no tribal knowledge/coding by convention


## REPL

TBD


## Components

TBD


## Processes

TBD


## Configuration

TBD
