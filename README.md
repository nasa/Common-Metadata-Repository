# `cmr-edsc-stubs`

*Various Stubbed Data for CMR / EDSC*


## Usage

Start the REPL:

```bash
$ lein repl
```

Create provider (via local metadata-db service):

```clj
(stubs/create-ges-disc-provider)
```
```clj
{:status 201, :body nil}
```

Ingest sample collection (via local ingest service):

```clj
(stubs/ingest-ges-disc-airx3std-collection)
```
```
{:concept-id "C1200000020-GES_DISC", :revision-id 1, :warnings nil}
```

Ingest sample methane variables (via local ingest service) for above-ingested
sample collection:

```clj
(stubs/ingest-ges-disc-airx3std-variables)
```
```
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_A.json ...
...
(23 more)
```
```clj
(({:concept-id "V1200000021-GES_DISC", :revision-id 1}
  {:concept-id "V1200000022-GES_DISC", :revision-id 1}
  {:concept-id "V1200000023-GES_DISC", :revision-id 1}
  {:concept-id "V1200000024-GES_DISC", :revision-id 1}
  ...
  (20 more)))
```

Associate above-ingested sample collection and sample variables:

```clj
(stubs/associate-ch4-variables-with-ges-disc-airx3std-collection)
```
```clj
([{:variable_association
   {:concept_id "VA1200000060-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
  ...
  (23 more)
```

Insert a service, using the metadata-db connection in a running CMR
dev-system REPL:

```clj
(reset :db :external)
(require '[cmr-edsc-stubs.core :as stubs])
(stubs/ingest-ges-disc-airx3std-opendap-service system)
```
```clj
```


## License

Copyright © 2017 United States Government as represented by the Administrator of the National Aeronautics and Space Administration.
All Rights Reserved.
