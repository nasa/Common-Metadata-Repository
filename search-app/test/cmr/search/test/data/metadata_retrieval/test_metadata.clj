(ns cmr.search.test.data.metadata-retrieval.test-metadata
  (:require
   [clojure.java.io :as io]
   [cmr.common.mime-types :as mt]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as t]))

;; Define some test metadata
(def dif10-concept
  "A fake concept map with dif10 metadata"
  {:concept-id "C1-PROV1"
   :revision-id 1
   :metadata (slurp (io/resource "example-data/dif10/sample_collection.xml"))
   :format mt/dif10
   :concept-type :collection})

(defn test-metadata-in-format
  [format]
  (t/transform nil dif10-concept format))

(defn concept-in-format
  [format]
  (assoc dif10-concept
         :metadata (test-metadata-in-format format)
         :format (mt/format->mime-type format)))

(def echo10-concept
  (concept-in-format :echo10))

(def umm-json-1.3-concept
  (concept-in-format {:format :umm-json :version "1.3"}))
