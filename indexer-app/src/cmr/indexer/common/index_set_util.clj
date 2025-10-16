(ns cmr.indexer.common.index-set-util
  "Provide functions to store, retrieve, delete index-sets"
  (:require
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.elastic-utils.config :as es-config]
   [cmr.indexer.config :as config]
   [cmr.indexer.data.index-set-elasticsearch :as es]
   [cmr.indexer.services.messages :as m]
   [cmr.indexer.indexer-util :as indexer-util]))

(defn get-index-sets
  [context es-cluster-name]
  (let [{:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-cluster-name)
        idx-mapping-type (first (keys mapping))
        index-set-array (es/get-index-sets (indexer-util/context->es-store context es-cluster-name) index-name idx-mapping-type)]

    (map #(select-keys (:index-set %) [:id :name :concepts])
         index-set-array)))

;(defn index-set-exists?
;  "Check index-set existence"
;  [context index-set-id]
;  (let [{:keys [index-name mapping]} config/idx-cfg-for-index-sets
;        idx-mapping-type (first (keys mapping))]
;    (es/index-set-exists? (indexer-util/context->es-store context) index-name idx-mapping-type index-set-id)))

(defn get-index-set
  "Fetch index-set associated with an index-set id."
  [context es-cluster-name index-set-id]
  (or (es/get-index-set context es-cluster-name index-set-id)
      (errors/throw-service-error :not-found
                                  (m/index-set-not-found-msg index-set-id))))

(defn get-all-index-sets
  "Fetch all index-sets in elastic and combines it into one json."
  [context]
  (let [{:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-config/elastic-name)
        idx-mapping-type (first (keys mapping))
        non-gran-index-set-array (es/get-index-sets (indexer-util/context->es-store context es-config/elastic-name) index-name idx-mapping-type)

        {:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-config/gran-elastic-name)
        idx-mapping-type (first (keys mapping))

        gran-index-set-array (es/get-index-sets (indexer-util/context->es-store context es-config/gran-elastic-name) index-name idx-mapping-type)

        all-index-set-array (map util/deep-merge gran-index-set-array non-gran-index-set-array)]

    (map #(select-keys (:index-set %) [:id :name :concepts])
         all-index-set-array)))
