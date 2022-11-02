# cmr-exchange-common

*Cross-project functionality, utilities, and general-use components*

[![Build Status][travis-badge]][travis]
[![Security Scan][security-scan-badge]][travis]
[![Dependencies Status][deps-badge]][travis]
[![Open Pull Requests][prs-badge]][prs]

[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]

[![Clojure version][clojure-v]](project.clj)

[![][logo]][logo]


## About

This repo just contains functions and components common to most of the other
projects in cmr-exchange. The name `cmr-common` was deliberately avoided, since
that is currently used in the legacy CMR codebase which the cmr-exchange
projects support (and which which they interoperate).


## Dependencies [&#x219F;](#contents)

* Java
* `lein`


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Apache License, Version 2.0.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4
[travis]: https://travis-ci.org/cmr-exchange/cmr-exchange-common
[travis-badge]: https://travis-ci.org/cmr-exchange/cmr-exchange-common.png?branch=master
[deps-badge]: https://img.shields.io/badge/deps%20check-passing-brightgreen.svg
[tag-badge]: https://img.shields.io/github/tag/cmr-exchange/cmr-exchange-common.svg
[tag]: https://github.com/cmr-exchange/cmr-exchange-common/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.9.0-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-exchange-common
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-exchange-common.svg
[security-scan-badge]: https://img.shields.io/badge/dependency%20check%20security%20scan-passing-brightgreen.svg
[prs]: https://github.com/pulls?utf8=%E2%9C%93&q=is%3Aopen+is%3Apr+org%3Acmr-exchange+archived%3Afalse+
[prs-badge]: https://img.shields.io/badge/Open%20PRs-org-yellow.svg
