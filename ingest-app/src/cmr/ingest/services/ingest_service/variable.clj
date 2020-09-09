(ns cmr.ingest.services.ingest-service.variable
  (:require
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.fingerprint-util :as fingerprint]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn add-extra-fields-for-variable
  "Returns collection concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept variable]
  (assoc concept
         :extra-fields
         {:variable-name (:Name variable)
          :measurement (:LongName variable)
          :fingerprint (fingerprint/get-variable-fingerprint (:metadata concept))}))

(defn-timed save-variable
  "Store a variable concept in mdb and indexer. Return name, long-name, concept-id, and
  revision-id."
  [context concept]
  (let [metadata (:metadata concept)
        variable (spec/parse-metadata context :variable (:format concept) metadata)
        concept (add-extra-fields-for-variable context concept variable)
        {:keys [concept-id revision-id variable-association associated-item]}
          (mdb2/save-concept context
            (assoc concept :provider-id (:provider-id concept)
                           :native-id (:native-id concept)))]
     (util/remove-nil-keys
       {:concept-id concept-id
        :revision-id revision-id
        :variable-association variable-association
        :associated-item associated-item})))
