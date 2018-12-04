# cmr-nlp

*A service for converting natural language queries into CMR search parameters*

[![Build Status][travis-badge]][travis]
[![Security Scan][security-scan-badge]][travis]
[![Dependencies Status][deps-badge]][travis]
[![Open Pull Requests][prs-badge]][prs]

[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]

[![Clojure version][clojure-v]](project.clj)

[![][logo]][logo]


#### Contents

* [About](#about-)
* [Dependencies](#dependencies-)
* [Usage](#usage-)
* [License](#license-)


## About [&#x219F;](#contents)

TBD


## Dependencies [&#x219F;](#contents)

* Java
* `lein`
* `curl` (used to download English language models)
* Various language models

WARNING: You will need to run `lein download-models` before attempting to use
this library!


## Usage [&#x219F;](#contents)

### Library

Start up a repl, do a require, and define a testing query:

```clj
(require '[cmr.nlp.core :as nlp]')
(def query "What was the average surface temperature of Lake Superior last week?")
```

Tokenize:

```clj
[cmr.nlp.repl] λ=> (def tokens (nlp/tokenize query))
[cmr.nlp.repl] λ=> tokens
["What"
 "was"
 "the"
 "average"
 "surface"
 "temperature"
 "of"
 "Lake"
 "Superior"
 "last"
 "week"
 "?"]
```

Tag the parts of speech:

```clj
[cmr.nlp.repl] λ=> (def pos (nlp/tag-pos tokens))
[cmr.nlp.repl] λ=> pos
(["What" "WP"]
 ["was" "VBD"]
 ["the" "DT"]
 ["average" "JJ"]
 ["surface" "NN"]
 ["temperature" "NN"]
 ["of" "IN"]
 ["Lake" "NNP"]
 ["Superior" "NNP"]
 ["last" "JJ"]
 ["week" "NN"]
 ["?" "."])
```

Get chunked phrases:

```clj
[cmr.nlp.repl] λ=> (nlp/chunk pos)
({:phrase ["What"] :tag "NP"}
 {:phrase ["was"] :tag "VP"}
 {:phrase ["the" "average" "surface" "temperature"] :tag "NP"}
 {:phrase ["of"] :tag "PP"}
 {:phrase ["Lake" "Superior"] :tag "NP"}
 {:phrase ["last" "week"] :tag "NP"})
```

Find locations:

```clj
[cmr.nlp.repl] λ=> (nlp/find-locations tokens)
("Lake Superior")
```

Find dates:

```clj
[cmr.nlp.repl] λ=> (nlp/find-dates tokens)
("last week")
```

Get actual dates from English sentences:

```clj
[cmr.nlp.repl] λ=> (nlp/extract-dates query)
(#inst "2018-11-27T21:40:12.946-00:00")
```

This is returned as a collection due to the fact that a query may have more
than one date (i.e., indicate a range):

```clj
[cmr.nlp.repl] λ=> (def query2 "What was the average high temp between last year and two years ago?")
[cmr.nlp.repl] λ=> (nlp/extract-dates query2)
(#inst "2017-12-04T21:42:42.874-00:00"
 #inst "2016-12-04T21:42:42.878-00:00")
```


### REST API

TBD


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Apache License, Version 2.0.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4
[travis]: https://travis-ci.org/cmr-exchange/cmr-nlp
[travis-badge]: https://travis-ci.org/cmr-exchange/cmr-nlp.png?branch=master
[deps-badge]: https://img.shields.io/badge/deps%20check-passing-brightgreen.svg
[tag-badge]: https://img.shields.io/github/tag/cmr-exchange/cmr-nlp.svg
[tag]: https://github.com/cmr-exchange/cmr-nlp/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.9.0-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-nlp
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-nlp.svg
[security-scan-badge]: https://img.shields.io/badge/nvd%2Fsecurity%20scan-passing-brightgreen.svg
[prs]: https://github.com/pulls?utf8=%E2%9C%93&q=is%3Aopen+is%3Apr+org%3Acmr-exchange+archived%3Afalse+
[prs-badge]: https://img.shields.io/badge/Open%20PRs-org-yellow.svg
