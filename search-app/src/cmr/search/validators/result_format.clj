(ns cmr.search.validators.result-format
  "Contains function for validating Query result-format attribute"
  (:require [clojure.set]
            [cmr.common.mime-types :as mt]))

;; Records search result supported mime-types by concept.
;; TODO - its hard to discover supported search result formats are maintained here. Find a better way.
(def concept-type->supported-formats
  {:collection #{:xml, :json, :echo10, :dif, :atom, :iso19115, :csv, :kml}
   :granule #{:xml, :json, :echo10, :atom, :iso19115, :csv, :kml}})

(defn validate
  "Validate requested search result format."
  [concept-type result-format]
  (let [mime-type (mt/format->mime-type result-format)
        valid-mime-types (set (map #(mt/format->mime-type %) (concept-type->supported-formats concept-type)))]
    (mt/validate-request-mime-type mime-type valid-mime-types)))