(ns cmr.indexer.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.indexer.data.metadata-db :as meta-db]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.umm.echo10.collection :as collection]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.indexer.services.temporal :as temporal]))

;; hard-code the index name and mapping type for now
(def es-index "collections")
(def es-mapping-type "collection")

(defn concept->elastic-doc
  "Returns elasticsearch json that can be used to insert into Elasticsearch
  for the given concept"
  [concept umm-concept]
  (let [{:keys [concept-id provider-id]} concept
        {{:keys [short-name version-id]} :product
         entry-title :entry-title
         temporal-coverage :temporal-coverage} umm-concept
        start-date (temporal/start-date temporal-coverage)
        end-date (temporal/end-date temporal-coverage)]
    {:concept-id concept-id
     :entry-title entry-title
     :entry-title.lowercase (s/lower-case entry-title)
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :short-name short-name
     :short-name.lowercase (s/lower-case short-name)
     :version-id version-id
     :version-id.lowercase (s/lower-case version-id)
     :start-date (f/unparse (f/formatters :date-time) start-date)
     :end-date (f/unparse (f/formatters :date-time) end-date)}))

(deftracefn index-concept
  "Index the given concept and revision-id"
  [context concept-id revision-id ignore-conflict]
  (info (format "Indexing concept %s, revision-id %s" concept-id revision-id))
  (let [concept (meta-db/get-concept context concept-id revision-id)
        ; TODO: should replace echo10.collection with a generic namesapce once umm-lib is complete
        umm-concept (collection/parse-collection (concept "metadata"))
        es-doc (concept->elastic-doc concept umm-concept)]
    (es/save-document-in-elastic context es-index es-mapping-type es-doc (Integer. revision-id) ignore-conflict)))

(deftracefn delete-concept
  "Delete the concept with the given id"
  [context id revision-id ignore-conflict]
  (info (format "Deleting concept %s, revision-id %s" id revision-id))
  ;; Assuming ingest will pass enough info for deletion
  ;; We should avoid making calls to metadata db to get the necessary info if possible
  (let [es-config (-> context :system :db :config)]
    (es/delete-document-in-elastic context es-config es-index es-mapping-type id revision-id ignore-conflict)))

(deftracefn reset-indexes
  "Reset elastic indexes"
  [context]
  (info (format "Recreating elastic index: %s" es-index))
  (let [es-config (-> context :system :db :config)]
    (es/reset-es-store context es-config)))

