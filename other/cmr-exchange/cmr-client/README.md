# A Clojure(Script) CMR Client

[![Build Status][travis badge]][travis]
[![Dependencies Status][deps-badge]][deps]
[![Clojure Version][clojure-v]](project.clj)
[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]
[![npm Release][npm-badge]][npm]


*A Clojure(Script)+JavaScript Client for NASA's Common Metadata Repository*

[![][logo]][logo]


## About the CMR

The [Common Metadata Repository][cmr-project] (CMR) is a high-performance,
high-quality, continuously evolving metadata system that catalogs all data and
service metadata records for the [EOSDIS][eosdis] system and will be the
authoritative management system for all EOSDIS metadata. These metadata records
are registered, modified, discovered, and accessed through programmatic
interfaces leveraging standard protocols and APIs.


## About the CMR Client

This project is an experimental implementation that allows JVM users (Java,
Scala, Clojure, Groovy, etc.), Node.js users, and JavaScript users to make 
calls to the CMR's published REST API. 

In addition, this project aims to produce a unified codebase that could be 
used in testing by the CMR team, eliminating the need for an enormouse amount 
of old, hard-to-use, hard-to-maintain testing fixtures and one-off utility 
functions that currently exists in the CMR codebase.

## Source Code

The source for the CMR client is available on [github][cmr-github]. Note that
this project has one codebase that provides clients for the following
platforms:

* [Clojure][clojure]
* [ClojureScript][clojurescript]
* [JavaScript][javascript]

For more details, see the [Guides][guides] section of the project
documentation.


## Documentation

See the [API Documentation][client-api-docs]


## Usage

See the [client guides][guides] for usage examples in Clojure,
ClojureScript, and JavaScript.


## Contributing

For information on how to contribute to this project (anything from bug reports
and feature requests to documentation updates and code), see the
[contributing docs][contributing-docs]


## License

Copyright © 2017 Duncan McGreggor

Distributed under the Apache License, Version 2.0.


<!-- Named page links below: /-->

[logo]: misc/images/ohboyohboyohboy.gif
[travis]: https://travis-ci.org/cmr-exchange/cmr-client
[travis badge]: https://img.shields.io/travis/cmr-exchange/cmr-client.svg
[deps]: http://jarkeeper.com/cmr-exchange/cmr-client
[deps-badge]: http://jarkeeper.com/cmr-exchange/cmr-client/status.svg
[tag-badge]: https://img.shields.io/github/tag/cmr-exchange/cmr-client.svg
[tag]: https://github.com/cmr-exchange/cmr-client/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.8.0-blue.svg
[jdk-v]: https://img.shields.io/badge/jdk-1.7+-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-client
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-client.svg
[npm-badge]: https://img.shields.io/npm/v/@nasa-earthdata/cmr.svg
[npm]: https://www.npmjs.com/package/@nasa-earthdata/cmr

[cmr-project]: https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository
[eosdis]: https://earthdata.nasa.gov/about
[cmr-github]: https://github.com/nasa/Common-Metadata-Repository
[clojure]: https://clojure.org/
[clojurescript]: https://clojurescript.org/
[javascript]: https://developer.mozilla.org/en-US/docs/Web/JavaScript
[client-api-docs]: https://cmr-exchange.github.io/cmr-client/current/
[guides]: https://cmr-exchange.github.io/cmr-client/current/3000-guides.html
[contributing-docs]: https://cmr-exchange.github.io/cmr-client/current/9100-contributing.html
