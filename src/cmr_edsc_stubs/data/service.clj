(ns cmr-edsc-stubs.data.service
  (:require
   [clj-time.coerce :as tc]
   [clj-time.local :as time]
   [cmr-edsc-stubs.util :as util]))

(defn create
  ([internal-id provider-id concept-id source-fn]
    (let [edn (util/convert-keys [:camel :kebab] (source-fn))
          metadata (source-fn :data)]
      (create internal-id provider-id concept-id (:name edn) metadata)))
  ([internal-id provider-id concept-id service-name metadata]
    (create internal-id provider-id concept-id service-name metadata
            (str (java.util.UUID/randomUUID))))
  ([internal-id provider-id concept-id service-name metadata native-id]
    (let [now (tc/to-sql-time (time/local-now))]
      {:id internal-id
       :concept-id concept-id
       :native-id native-id
       :metadata (.getBytes metadata)
       :format "application/json"
       :revision-id 1
       :revision-date now
       :created-at now
       :deleted 0
       :user-id "cmr-stubbed-data"
       :service-name service-name
       :provider-id provider-id})))
