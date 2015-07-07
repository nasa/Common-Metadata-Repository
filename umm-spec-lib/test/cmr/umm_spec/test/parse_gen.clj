(ns cmr.umm-spec.test.parse-gen
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.parse-gen :as p]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.json-schema :as js]))

(def example-record
    (umm-c/map->UMM-C {:EntryTitle "The entry title V5"}))

(deftest roundtrip-gen-parse
  (let [mappings (p/load-mappings p/echo10-mappings)
        xml (p/generate-xml mappings example-record)
        umm-c-schema (js/load-schema-for-parsing "umm-c-json-schema.json")
        umm-mappings (p/get-to-umm-mappings umm-c-schema mappings)
        parsed (p/parse-xml umm-mappings xml)]
    (is (= example-record parsed))))