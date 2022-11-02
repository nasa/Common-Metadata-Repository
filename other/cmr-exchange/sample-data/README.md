# `cmr.sample-data`

[![Build Status][travis badge]][travis]
[![Dependencies Status][deps-badge]][deps]
[![Clojure Version][clojure-v]](project.clj)
[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]


*Sample Data for the open source NASA Common Metadata Repository (CMR)*

[![][logo]][logo-large]


## About the CMR

The [Common Metadata Repository][cmr-project] (CMR) is a high-performance,
high-quality, continuously evolving metadata system that catalogs all data and
service metadata records for the [EOSDIS][eosdis] system and will be the
authoritative management system for all EOSDIS metadata. These metadata records
are registered, modified, discovered, and accessed through programmatic
interfaces leveraging standard protocols and APIs.


## Usage

Start the REPL:

```bash
$ lein repl
```

Sample data files have two general result types: they may either be returned
as a file object or as the data stored in the file:

```clj
[cmr.sample-data.dev] λ=> (data/get-ges-disc-provider :obj)
#object[java.net.URL 0x6717aefa
  "file:/alt/home/oubiwann/lab/NASA/CMR/cmr-sample-data/resources/data/providers/GES_DISC.json"]
[cmr.sample-data.dev] λ=> (data/get-ges-disc-provider :data)
"{\n  \"provider-id\": \"GES_DISC\",\n  \"short-name\": \"GES_DISC\",\n  \"cmr-only\": true,\n
\"small\": true\n}\n"
```

Additionally, if the data stored in the file is JSON, you may have it parsed
as JSON and converted Clojure's native data format, EDN:

```clj
[cmr.sample-data.dev] λ=> (pprint (data/get-ges-disc-provider [:json :edn]))
{:provider-id "GES_DISC",
 :short-name "GES_DISC",
 :cmr-only true,
 :small true}
nil
```

This particular sample data returns a JSON string (i.e., the `:data` option) by
default, so you may call it with no options if that's what you need:

```clj
[cmr.sample-data.dev] λ=> (data/get-ges-disc-provider)
"{\n  \"provider-id\": \"GES_DISC\",\n  \"short-name\": \"GES_DISC\",\n  \"cmr-only\": true,\n
\"small\": true\n}\n"
```

The default data format returned is determined by how the CMR typically uses
the data in question (i.e., what format it expects the data to be in).

For a full list of functions availble for accessing the sample data, refer to
the [cmr.sample-data.core](src/cmr/sample_data/core.clj) source file
(aliases to `data` in the `cmr.sample-data.dev` REPL namespace).


## License

Copyright © 2017 United States Government as represented by the Administrator of the National Aeronautics and Space Administration.
All Rights Reserved.


<!-- Named page links below: /-->

[logo]: resources/images/logo-250px.png
[logo-large]: resources/images/logo-2400px.png
[travis]: https://travis-ci.org/cmr-exchange/sample-data
[travis badge]: https://img.shields.io/travis/cmr-exchange/sample-data.svg
[deps]: http://jarkeeper.com/cmr-exchange/sample-data
[deps-badge]: http://jarkeeper.com/cmr-exchange/sample-data/status.svg
[tag-badge]: https://img.shields.io/github/tag/cmr-exchange/sample-data.svg
[tag]: https://github.com/cmr-exchange/sample-data/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.8.0-blue.svg
[jdk-v]: https://img.shields.io/badge/jdk-1.7+-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-sample-data
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-sample-data.svg

[cmr-project]: https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository
[eosdis]: https://earthdata.nasa.gov/about
[cmr-github]: https://github.com/nasa/Common-Metadata-Repository
[clojure]: https://clojure.org/
