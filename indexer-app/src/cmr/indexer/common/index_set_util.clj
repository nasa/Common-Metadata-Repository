(ns cmr.indexer.common.index-set-util
  "Provide functions to store, retrieve, delete index-sets"
  (:require
   [cmr.common.services.errors :as errors]
   [cmr.indexer.config :as config]
   [cmr.indexer.data.index-set-elasticsearch :as es]
   [cmr.indexer.services.messages :as m]
   [cmr.indexer.indexer-util :as indexer-util]))

(defn get-index-sets
  "Fetch all index-sets in elastic."
  [context]
  (let [{:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))
        index-sets (es/get-index-sets (indexer-util/context->es-store context) index-name idx-mapping-type)]
    (map #(select-keys (:index-set %) [:id :name :concepts])
         index-sets)))

(defn index-set-exists?
  "Check index-set existence"
  [context index-set-id]
  (let [{:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (es/index-set-exists? (indexer-util/context->es-store context) index-name idx-mapping-type index-set-id)))

(defn get-index-set
  "Fetch index-set associated with an index-set id."
  [context index-set-id]
  (or (es/get-index-set context index-set-id)
      (errors/throw-service-error :not-found
                                  (m/index-set-not-found-msg index-set-id))))

