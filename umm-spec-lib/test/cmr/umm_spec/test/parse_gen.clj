(ns cmr.umm-spec.test.parse-gen
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.parse-gen :as p]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.json-schema :as js]))

(def example-record
  (umm-c/map->UMM-C
    {
     :EntryId (umm-cmn/map->EntryIdType
                {:Id "short_V1"
                 ;; TODO instroduce these when testing ISO
                 ; :Version
                 ; :Authority
                 })
     :Product (umm-c/map->ProductType {:ShortName "short" :VersionId "V1"})
     :EntryTitle "The entry title V5"
     :Abstract "A very abstract collection"

     }))

(comment

  (p/get-to-umm-mappings
    (js/load-schema-for-parsing "umm-c-json-schema.json")
    (p/load-mappings p/echo10-mappings))


  )

(deftest roundtrip-gen-parse
  (let [mappings (p/load-mappings p/echo10-mappings)
        xml (p/generate-xml mappings example-record)
        umm-c-schema (js/load-schema-for-parsing "umm-c-json-schema.json")
        umm-mappings (p/get-to-umm-mappings umm-c-schema mappings)
        _ (cmr.common.dev.capture-reveal/capture umm-mappings mappings)
        parsed (p/parse-xml umm-mappings xml)]
    (is (= example-record parsed))))