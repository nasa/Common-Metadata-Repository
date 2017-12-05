# Architecture

## Overview

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

For current CMR developers use of this development environment should be
seamless: the only requirements are that:

* Your CMR repository clone directory be named `cmr`; and,
* Your CMR repository clone directory be a sibling directory to the clone of
  the D.E.M. repository.

### Diagram

TBD

### Benefits

TBD

## REPL

TBD

## Components

TBD

## Processes

TBD

## Configuration

TBD
