(ns cmr.indexer.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.indexer.data.metadata-db :as meta-db]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.umm.echo10.collection :as collection]
            [cmr.umm.echo10.granule :as granule]
            [cheshire.core :as cheshire]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.indexer.data.cache :as cache]
            [cmr.common.concepts :as cs]
            [cmr.system-trace.core :refer [deftracefn]]))

(defn- concept-id->type
  "Returns concept type for the given concept-id"
  [concept-id]
  (cs/concept-prefix->concept-type (subs concept-id 0 1)))

(defmulti parse-concept
  "Returns the UMM model of the concept by parsing its metadata field"
  (fn [concept]
    (concept-id->type (:concept-id concept))))

(defmulti concept->elastic-doc
  "Returns elastic json that can be used to insert into Elasticsearch for the given concept"
  (fn [concept umm-concept]
    (concept-id->type (:concept-id concept))))

(deftracefn index-concept
  "Index the given concept and revision-id"
  [context concept-id revision-id ignore-conflict]
  (info (format "Indexing concept %s, revision-id %s" concept-id revision-id))
  (let [concept (meta-db/get-concept context concept-id revision-id)
        concept-type (concept-id->type concept-id)
        concept-indices (idx-set/get-concept-type-index-names context)
        concept-mapping-types (idx-set/get-concept-mapping-types context)
        umm-concept (parse-concept concept)
        es-doc (concept->elastic-doc concept umm-concept)]
    (es/save-document-in-elastic
      context
      (concept-indices concept-type)
      (concept-mapping-types concept-type) es-doc (Integer. revision-id) ignore-conflict)))


(deftracefn delete-concept
  "Delete the concept with the given id"
  [context id revision-id ignore-conflict]
  (info (format "Deleting concept %s, revision-id %s" id revision-id))
  ;; Assuming ingest will pass enough info for deletion
  ;; We should avoid making calls to metadata db to get the necessary info if possible
  (let [elastic-config (idx-set/get-elastic-config context)
        concept-type (concept-id->type id)
        concept-indices (idx-set/get-concept-type-index-names context)
        concept-mapping-types (idx-set/get-concept-mapping-types context)]
    (es/delete-document-in-elastic
      context elastic-config
      (concept-indices concept-type)
      (concept-mapping-types concept-type) id revision-id ignore-conflict)))


(deftracefn reset-indexes
  "Delegate reset elastic indices operation to index-set app"
  [context]
  (cache/reset-cache (-> context :system :cache))
  (es/reset-es-store))


