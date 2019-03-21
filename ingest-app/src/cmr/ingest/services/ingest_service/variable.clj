(ns cmr.ingest.services.ingest-service.variable
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.util :refer [defn-timed]]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]
   [digest]))

(defn- normalized-string
  "Returns the given string with leading and trailing whitespaces trimmed
  and converted to lowercase."
  [value]
  (some-> value
          string/trim
          string/lower-case))

(defn- get-variable-fingerprint
  "Returns the fingerprint of the given variable concept."
  [variable-concept]
  (let [parsed (json/decode (:metadata variable-concept) true)
        {:keys [AcquisitionSourceName Name Units Dimensions]} parsed
        id-string (format "%s|%s|%s|%s"
                          (normalized-string AcquisitionSourceName)
                          (normalized-string Name)
                          (normalized-string Units)
                          (pr-str (mapv #(update % :Name normalized-string) Dimensions)))]
    (digest/md5 id-string)))

(defn add-extra-fields-for-variable
  "Returns collection concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept variable]
  (assoc concept :extra-fields {:variable-name (:Name variable)
                                :measurement (:LongName variable)
                                :fingerprint (get-variable-fingerprint concept)}))

(defn-timed save-variable
  "Store a variable concept in mdb and indexer. Return name, long-name, concept-id, and
  revision-id."
  [context concept]
  (let [metadata (:metadata concept)
        variable (spec/parse-metadata context :variable (:format concept) metadata)
        concept (add-extra-fields-for-variable context concept variable)
        {:keys [concept-id revision-id]} (mdb2/save-concept context
                                          (assoc concept :provider-id (:provider-id concept)
                                                         :native-id (:native-id concept)))]
      {:concept-id concept-id
       :revision-id revision-id}))
