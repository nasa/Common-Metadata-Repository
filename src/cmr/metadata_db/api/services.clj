(ns cmr.metadata-db.api.services
  (:require [cmr.metadata-db.data :as data]))

(defn save-concept
  "Store a concept record and return the revision"
  [system concept]
  (let [{:keys [db]} system
        revision-id (data/save-concept db concept)]
    {:status 201
     :body {:revision-id revision-id}
     :headers {"Content-Type" "json"}}))



;; Ingest update collection
 ;;- provider -id, concept type, native id, metadata
 ;; get latest collection by provider id, concept type, and native id
 ;; if new
 ;;   generate new concept id (ask for a new id from metadata db )
 ;;      provider-id, concept-type, native-id -> concept id
 
 ;;      concept-id must match <character> <digit>+ "-" <provider-id>
 ;;   validate collection
 ;;   save concept with concept id
 
 ;; 2 changes
 ;; 1. Operation to map provider-id, concept-type, native-id -> concept id
 ;; 2. Revision id can be provided during concept save. If it is unexpected an exception is thrown.