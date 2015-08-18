(ns cmr.umm-spec.test.umm-json
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.umm-json :as uj]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.umm-spec.json-schema :as js]))

(def minimal-example-record
  "This is the minimum valid UMM."
  (umm-c/map->UMM-C
    {:Platform [(umm-cmn/map->PlatformType
                  {:ShortName "Platform"
                   :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {})
     :RelatedUrl [(umm-cmn/map->RelatedUrlType {:URL ["http://google.com"]})]
     :ResponsibleOrganization [(umm-cmn/map->ResponsibilityType {:Role "RESOURCEPROVIDER"
                                                                 :Party (umm-cmn/map->PartyType {})})]
     :ScienceKeyword [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent [(umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})]

     :EntryId "short"
     :EntryTitle "The entry title V5"
     :DataDate [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                        :Type "CREATE"})]
     :Abstract "A very abstract collection"
     :TemporalExtent [(umm-cmn/map->TemporalExtentType {})]}))

;; This only tests a minimum example record for now. We need to test with larger more complicated
;; records. We will do this as part of CMR-1929

(deftest generate-and-parse-umm-json
  (let [json (uj/umm->json minimal-example-record)
        parsed (uj/json->umm js/umm-c-schema json)]
    (is (empty? (js/validate-umm-json json)))
    (is (= minimal-example-record parsed))))

(deftest validate-json-with-extra-fields
  (let [json (uj/umm->json (assoc minimal-example-record :foo "extra"))]
    (is (= ["object instance has properties which are not allowed by the schema: [\"foo\"]"]
           (js/validate-umm-json json)))))