(ns cmr.search.services.smart-handoff-service
  "Provides functions for smart handoff"
  (:require
    [cmr.common-app.api.routes :as cr]
    [cmr.common.mime-types :as mt]
    [cmr.transmit.smart-handoff :as smart-handoff]))

(def ^:private smart-handoff-headers
  "smart handoff headers"
  (assoc (:headers cr/options-response)
         cr/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)))

(defn retrieve-schema
  "Retrieve the given smart handoff schema, returns the response."
  [context client]
  (let [schema-filename (str client "-schema.json")
        {:keys [status body]} (smart-handoff/get-smart-handoff-schema context schema-filename)]
    {:status status
     :body body
     :headers smart-handoff-headers}))
