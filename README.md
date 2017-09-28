# `cmr-edsc-stubs`

*Various Stubbed Data for CMR / EDSC*


## Usage

Start the REPL:

```bash
$ lein repl
```

Create provider (via local metadata-db service):

```clj
(data/create-ges-disc-provider)
```

Ingest sample collection (via local ingest service):

```clj
(data/ingest-ges-disc-airx3std-collection)
```

Ingest sample methan variables (via local ingest service) for above-ingested
sample collection:

```clj
(data/ingest-ges-disc-airx3std-variables)
```


## License

Copyright © 2017 United States Government as represented by the Administrator of the National Aeronautics and Space Administration.
All Rights Reserved.
