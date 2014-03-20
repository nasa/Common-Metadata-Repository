(ns cmr.ingest.services.ingest
  (:require [cmr.ingest.data.mdb :as mdb]
            [cmr.ingest.data.indexer :as indexer]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))

(deftracefn save-concept
  "Store a concept in mdb and indexer and return concept-id and revision-id."
  [context concept]
  (let [{:keys [concept-type provider-id native-id]} concept
        concept-id (mdb/get-concept-id context concept-type provider-id native-id)
        revision-id (mdb/save-concept context (assoc concept :concept-id  concept-id))]
    (indexer/index-concept context concept-id revision-id)
    {:concept-id concept-id, :revision-id revision-id}))

(deftracefn delete-concept
  "Delete a concept from mdb and indexer."
  [context concept-attribs]
  (let [{:keys [concept-type provider-id native-id]}  concept-attribs
        concept-id (mdb/get-concept-id context concept-type provider-id native-id)
        revision-id (mdb/delete-concept context concept-id)]
    (indexer/delete-concept-from-index context concept-id revision-id)
    {:concept-id concept-id, :revision-id revision-id}))