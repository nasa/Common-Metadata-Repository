# `cmr.sample-data`

[![Build Status][travis badge]][travis]
[![Dependencies Status][deps-badge]][deps]
[![Clojure Version][clojure-v]](project.clj)
[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]
[![npm Release][npm-badge]][npm]

*Sample Data for the open source NASA Common Metadata Repository (CMR)*


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
