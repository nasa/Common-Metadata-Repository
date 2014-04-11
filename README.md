# cmr-index-set-app

FIXME

## Design

An Index Set is a logical set of indexes in Elasticsearch for indexing and searching for concepts. Multiple index sets would be needed to allow for reindexing or migrations.

An Index Set consists of the following:

  * name - A descriptive human readable name.
  * id - A positive integer to uniquely identify an index set. The id is used as a prefix for the indexes in elasticsearch.
  * concept-type-index-config - A map of concept types to Index configs


## Running

To start a web server for the application, run:

    lein run

## Curls

-Create index-set using json string
 curl -i -H "Accept: application/json" -H "Content-type: application/json" -XPOST "http://localhost:3005/index-sets" -d "{\"index-set\":{\"name\":\"cmr-base-index-set\",\"create-reason\":\"include message about reasons for creating this index set\",\"granule\":{\"index-names\":[\"G2-PROV1\",\"G4-Prov3\",\"g5_prov5\"],\"mapping\":{\"granule\":{\"_all\":{\"enabled\":false},\"properties\":{\"collection-concept-id\":{\"store\":\"yes\",\"index_options\":\"docs\",\"omit_norms\":\"true\",\"type\":\"string\",\"index\":\"not_analyzed\"},\"concept-id\":{\"store\":\"yes\",\"index_options\":\"docs\",\"omit_norms\":\"true\",\"type\":\"string\",\"index\":\"not_analyzed\"}},\"dynamic\":\"strict\",\"_source\":{\"enabled\":false},\"_id\":{\"path\":\"concept-id\"}}},\"settings\":{\"index\":{\"number_of_replicas\":0,\"refresh_interval\":\"10s\",\"number_of_shards\":1}}},\"collection\":{\"index-names\":[\"C4-collections\",\"c6_Collections\"],\"mapping\":{\"collection\":{\"_all\":{\"enabled\":false},\"properties\":{\"entry-title\":{\"store\":\"yes\",\"index_options\":\"docs\",\"omit_norms\":\"true\",\"type\":\"string\",\"index\":\"not_analyzed\"},\"concept-id\":{\"store\":\"yes\",\"index_options\":\"docs\",\"omit_norms\":\"true\",\"type\":\"string\",\"index\":\"not_analyzed\"}},\"dynamic\":\"strict\",\"_source\":{\"enabled\":false},\"_id\":{\"path\":\"concept-id\"}}},\"settings\":{\"index\":{\"number_of_replicas\":0,\"refresh_interval\":\"20s\",\"number_of_shards\":1}}},\"id\":3}}"

- Get index-set by id
curl -XGET "http://localhost:3005/index-sets/3"

- Get all index-sets
curl  -XGET "http://localhost:3005/index-sets"

- Delete index-set by id
curl  -XDELETE "http://localhost:3005/index-sets/3"


- Reset for dev purposes
curl -i -H "Accept: application/json" -H "Content-type: application/json" -XPOST "http://localhost:3005/reset"

- See indices listing
 curl http://localhost:9210/index_sets/_aliases?pretty=1


## License

Copyright © 2014 NASA

## Sample outputs

- Get all index-sets response
[{:id 3,
  :name "cmr-base-index-set",
  :concepts
  {:collection
   {:c6_Collections "3_c6_collections",
    :C4-collections "3_c4_collections"},
   :granule
   {:g5_prov5 "3_g5_prov5",
    :G4-Prov3 "3_g4_prov3",
    :G2-PROV1 "3_g2_prov1"}}}
 {:id 55,
  :name "cmr-base-index-set",
  :concepts
  {:collection
   {:c6_Collections "55_c6_collections",
    :C4-collections "55_c4_collections"},
   :granule
   {:g5_prov5 "55_g5_prov5",
    :G4-Prov3 "55_g4_prov3",
    :G2-PROV1 "55_g2_prov1"}}}]

- Get an index-set by id response
    {:index-set
 {:concepts
  {:collection
   {:c6_Collections "3_c6_collections",
    :C4-collections "3_c4_collections"},
   :granule
   {:g5_prov5 "3_g5_prov5",
    :G4-Prov3 "3_g4_prov3",
    :G2-PROV1 "3_g2_prov1"}},
  :name "cmr-base-index-set",
  :create-reason
  "include message about reasons for creating this index set",
  :granule
  {:index-names ["G2-PROV1" "G4-Prov3" "g5_prov5"],
   :mapping
   {:granule
    {:_all {:enabled false},
     :properties
     {:collection-concept-id
      {:store "yes",
       :index_options "docs",
       :omit_norms "true",
       :type "string",
       :index "not_analyzed"},
      :concept-id
      {:store "yes",
       :index_options "docs",
       :omit_norms "true",
       :type "string",
       :index "not_analyzed"}},
     :dynamic "strict",
     :_source {:enabled false},
     :_id {:path "concept-id"}}},
   :settings
   {:index
    {:number_of_replicas 0,
     :refresh_interval "10s",
     :number_of_shards 1}}},
  :collection
  {:index-names ["C4-collections" "c6_Collections"],
   :mapping
   {:collection
    {:_all {:enabled false},
     :properties
     {:entry-title
      {:store "yes",
       :index_options "docs",
       :omit_norms "true",
       :type "string",
       :index "not_analyzed"},
      :concept-id
      {:store "yes",
       :index_options "docs",
       :omit_norms "true",
       :type "string",
       :index "not_analyzed"}},
     :dynamic "strict",
     :_source {:enabled false},
     :_id {:path "concept-id"}}},
   :settings
   {:index
    {:number_of_replicas 0,
     :refresh_interval "20s",
     :number_of_shards 1}}},
  :id 3}}
