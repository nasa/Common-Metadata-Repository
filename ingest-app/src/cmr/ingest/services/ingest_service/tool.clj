(ns cmr.ingest.services.ingest-service.tool
  (:require
   [cmr.common.util :refer [defn-timed]]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn- add-extra-fields-for-tool
  "Returns tool concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept tool]
  (assoc concept :extra-fields {:tool-name (:Name tool)
                                :tool-type (:Type tool)}))
(defn-timed save-tool
  "Store a tool concept in mdb and indexer."
  [context concept]
  (let [metadata (:metadata concept)
        tool (spec/parse-metadata context :tool (:format concept) metadata)
        concept (add-extra-fields-for-tool context concept tool)
        {:keys [concept-id revision-id]} (mdb2/save-concept context
                                          (assoc concept :provider-id (:provider-id concept)
                                                         :native-id (:native-id concept)))]
      {:concept-id concept-id
       :revision-id revision-id}))
