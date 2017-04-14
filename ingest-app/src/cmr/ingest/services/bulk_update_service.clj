(ns cmr.ingest.services.bulk-update-service
 (:require
  [cheshire.core :as json]
  [clojure.java.io :as io]
  [cmr.common.validations.json-schema :as js]))

(def bulk-update-schema
 (js/json-string->json-schema (slurp (io/resource "bulk_update_schema.json"))))

(defn validate-bulk-update-post-params
 [json]
 (js/validate-json! bulk-update-schema json))
