(ns cmr.elastic-utils.search.es-debug
  "Holds a very strange function which is only used by dev-system/control. Moved to this namespace
   so as to not require any other files from needing to import clojurewerkz."
  (:require
   [clojurewerkz.elastisch.rest.document :as esd]
   [cmr.common.services.errors :as e]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.search.es-index :as common-esi]
   [cmr.elastic-utils.search.es-wrapper :as q]))

(defn- context->conn
  "Pulls out the context from the search index"
  [context es-cluster-name]
  (case es-cluster-name
    es-config/elastic-name (get-in context [:system :search-index :conn])
    es-config/gran-elastic-name (get-in context [:system :gran-search-index :conn])))

(defn get-collection-permitted-groups
  "NOTE: Use for debugging only. Gets collections along with their currently permitted groups. This
   won't work if more than 10,000 collections exist in the CMR.
   Called by dev-system/src/cmr/dev_system/control.clj only
   Originally found in cmr.search.data.elastic-search-index/elastic_search_index.clj"
  [context]
  (let [index-info (common-esi/concept-type->index-info context :collection nil)
        results (esd/search (context->conn context es-config/elastic-name)
                            (:index-name index-info)
                            [(:type-name index-info)]
                            :query (q/match-all)
                            :size 10000
                            :_source ["permitted-group-ids"])
        hits (get-in results [:hits :total :value])]
    (when (> hits (count (get-in results [:hits :hits])))
      (e/internal-error! "Failed to retrieve all hits."))
    (into {} (for [hit (get-in results [:hits :hits])]
               [(:_id hit) (get-in hit [:_source :permitted-group-ids])]))))
