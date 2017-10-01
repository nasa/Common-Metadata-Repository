(ns cmr-edsc-stubs.data.service
  (:require [clj-time.coerce :as tc]
            [clj-time.local :as time]))

(defn create
  ([provider-id concept-id source-fn]
    (let [edn (source-fn)
          metadata (source-fn :data)]
      (create provider-id concept-id (:name edn) metadata)))
  ([provider-id concept-id service-name metadata]
    (create provider-id concept-id service-name metadata
                    (str (java.util.UUID/randomUUID))))
  ([provider-id concept-id service-name metadata native-id]
    (let [now (tc/to-sql-time (time/local-now))]
      {;:id
       :concept-id concept-id
       :native-id native-id
       :metadata metadata
       :format "application/json"
       :revision-id 1
       :revision-date now
       :created-at now
       :deleted 0
       :user-id "cmr-stubbed-data"
       :service-name service-name
       ;:transaction-id
       :provider-id provider-id})))
