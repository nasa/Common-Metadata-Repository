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
```clj
{:status 201, :body nil}
```

Ingest sample collection (via local ingest service):

```clj
(data/ingest-ges-disc-airx3std-collection)
```
```
{:concept-id "C1200000020-GES_DISC", :revision-id 1, :warnings nil}
```

Ingest sample methane variables (via local ingest service) for above-ingested
sample collection:

```clj
(data/ingest-ges-disc-airx3std-variables)
```
```
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_A.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_A_ct.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_A_err.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_A_max.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_A_min.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_A_sdev.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_D.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_D_ct.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_D_err.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_D_max.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_D_min.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_D_sdev.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_A.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_A_ct.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_A_err.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_A_max.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_A_min.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_A_sdev.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_D.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_D_ct.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_D_err.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_D_max.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_D_min.json ...
Loading /Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_TqJ_D_sdev.json ...
```
```clj
(({:concept-id "V1200000021-GES_DISC", :revision-id 1}
  {:concept-id "V1200000022-GES_DISC", :revision-id 1}
  {:concept-id "V1200000023-GES_DISC", :revision-id 1}
  {:concept-id "V1200000024-GES_DISC", :revision-id 1}
  {:concept-id "V1200000025-GES_DISC", :revision-id 1}
  {:concept-id "V1200000026-GES_DISC", :revision-id 1}
  {:concept-id "V1200000027-GES_DISC", :revision-id 1}
  {:concept-id "V1200000028-GES_DISC", :revision-id 1}
  {:concept-id "V1200000029-GES_DISC", :revision-id 1}
  {:concept-id "V1200000030-GES_DISC", :revision-id 1}
  {:concept-id "V1200000031-GES_DISC", :revision-id 1}
  {:concept-id "V1200000032-GES_DISC", :revision-id 1}
  {:concept-id "V1200000033-GES_DISC", :revision-id 1}
  {:concept-id "V1200000034-GES_DISC", :revision-id 1}
  {:concept-id "V1200000035-GES_DISC", :revision-id 1}
  {:concept-id "V1200000036-GES_DISC", :revision-id 1}
  {:concept-id "V1200000037-GES_DISC", :revision-id 1}
  {:concept-id "V1200000038-GES_DISC", :revision-id 1}
  {:concept-id "V1200000039-GES_DISC", :revision-id 1}
  {:concept-id "V1200000040-GES_DISC", :revision-id 1}
  {:concept-id "V1200000041-GES_DISC", :revision-id 1}
  {:concept-id "V1200000042-GES_DISC", :revision-id 1}
  {:concept-id "V1200000043-GES_DISC", :revision-id 1}
  {:concept-id "V1200000044-GES_DISC", :revision-id 1}))
```

Associate above-ingested sample collection and sample variables:

```clj
(data/associate-ch4-variables-with-ges-disc-airx3std-collection)
```
```clj
([{:variable_association
   {:concept_id "VA1200000060-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000061-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000062-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000063-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000064-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000065-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000066-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000067-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000068-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000069-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000080-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000081-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000082-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000083-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000084-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000085-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000086-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000087-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000088-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000089-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000090-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000091-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000092-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}]
 [{:variable_association
   {:concept_id "VA1200000093-CMR", :revision_id 1},
   :associated_item {:concept_id "C1200000020-GES_DISC"}}])
```

## License

Copyright © 2017 United States Government as represented by the Administrator of the National Aeronautics and Space Administration.
All Rights Reserved.
