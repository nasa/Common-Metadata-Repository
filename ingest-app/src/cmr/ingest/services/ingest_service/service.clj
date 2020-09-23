(ns cmr.ingest.services.ingest-service.service
  (:require
   [cmr.common.util :refer [defn-timed]]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn- add-extra-fields-for-service
  "Returns service concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept service]
  (assoc concept :extra-fields {:service-name (:Name service)
                                :service-type (:Type service)})) 

(defn-timed save-service
  "Store a service concept in mdb and indexer. Return name, long-name, concept-id, and
  revision-id."
  [context concept]
  (let [metadata (:metadata concept)
        service (spec/parse-metadata context :service (:format concept) metadata)
        concept (add-extra-fields-for-service context concept service)
        {:keys [concept-id revision-id]} (mdb2/save-concept context
                                          (assoc concept :provider-id (:provider-id concept)
                                                         :native-id (:native-id concept)))]
      {:concept-id concept-id
       :revision-id revision-id}))
