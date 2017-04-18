(ns cmr.ingest.services.bulk-update-service
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.json-schema :as js]))

(def bulk-update-schema
  (js/json-string->json-schema (slurp (io/resource "bulk_update_schema.json"))))

(defn validate-bulk-update-post-params
  "Validate post body for bulk update. Validate against schema and validation
  rules."
  [json]
  (js/validate-json! bulk-update-schema json)
  (let [body (json/parse-string json true)
        {:keys [update-type update-value find-value]} body]
    (when (and (not= "CLEAR_FIELD" update-type)
               (not= "FIND_AND_REMOVE" update-type)
               (nil? update-value))
      (errors/throw-service-errors :bad-request
                                   [(format "An update value must be supplied when the update is of type %s"
                                            update-type)]))
    (when (and (or (= "FIND_AND_REPLACE" update-type)
                   (= "FIND_AND_REMOVE" update-type))
               (nil? find-value))
      (errors/throw-service-errors :bad-request
                                   [(format "A find value must be supplied when the update is of type %s"
                                            update-type)]))))
