(ns cmr.metadata-db.api.route-helpers
  (:require [cheshire.core :as json]))

(def json-header
  {"Content-Type" "application/json; charset=utf-8"})

(defn to-json
  "Converts the object to JSON. If the pretty parameter is passed with true formats the response for
  easy reading"
  [obj params]
  (json/generate-string obj {:pretty (= "true" (get params :pretty))}))