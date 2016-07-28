(ns cmr.search.test.data.metadata-retrieval.test-metadata
  (require [clojure.java.io :as io]
           [cmr.search.data.metadata-retrieval.metadata-transformer :as t]
           [cmr.common.mime-types :as mt]))

;; Define some test metadata
(def dif-resource
  (io/resource "data/dif/sample_collection.xml"))

(def dif-concept
  "A fake concept map with dif metadata"
  {:concept-id "C1-PROV1"
   :revision-id 1
   :metadata (slurp dif-resource)
   :format mt/dif
   :concept-type :collection})

(defn test-metadata-in-format
  [format]
  (t/transform nil dif-concept format))

(defn concept-in-format
  [format]
  (assoc dif-concept
         :metadata (test-metadata-in-format format)
         :format (mt/format->mime-type format)))

(def echo10-concept
  (concept-in-format :echo10))

(def iso19115-concept
  (concept-in-format :iso19115))

(def dif10-concept
  (concept-in-format :dif10))

(def umm-json-1.3-concept
  (concept-in-format {:format :umm-json :version "1.3"}))

(def umm-json-1.2-concept
  (concept-in-format {:format :umm-json :version "1.2"}))
