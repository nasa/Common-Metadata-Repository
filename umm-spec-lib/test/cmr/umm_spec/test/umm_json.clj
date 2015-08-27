(ns cmr.umm-spec.test.umm-json
  (:require [clojure.test :refer :all]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [cmr.common.test.test-check-ext :as ext :refer [defspec]]
            [cmr.umm-spec.umm-json :as uj]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.test.umm-generators :as umm-gen]))

(def minimal-example-record
  "This is the minimum valid UMM."
  (umm-c/map->UMM-C
    {:Platforms [(umm-cmn/map->PlatformType
                   {:ShortName "Platform"
                    :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {})
     :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URLs ["http://google.com"]})]
     :ResponsibleOrganizations [(umm-cmn/map->ResponsibilityType {:Role "RESOURCEPROVIDER"
                                                                  :Party (umm-cmn/map->PartyType {})})]
     :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})

     :EntryId "short"
     :EntryTitle "The entry title V5"
     :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                         :Type "CREATE"})]
     :Abstract "A very abstract collection"
     :TemporalExtents [(umm-cmn/map->TemporalExtentType {})]}))

;; This only tests a minimum example record for now. We need to test with larger more complicated
;; records. We will do this as part of CMR-1929

(deftest generate-and-parse-umm-json
  (testing "minimal record"
    (let [json (uj/umm->json minimal-example-record)
          _ (is (empty? (js/validate-umm-json json)))
          parsed (uj/json->umm js/umm-c-schema json)]
      (is (= minimal-example-record parsed)))))

(defspec all-umm-records 100
  (for-all [umm-record umm-gen/umm-c-generator]
    (let [json (uj/umm->json umm-record)
          _ (is (empty? (js/validate-umm-json json)))
          parsed (uj/json->umm js/umm-c-schema json)]
      (is (= umm-record parsed)))))

(comment

  ;; After you see a failing value execute the def then use this to see what failed.
  ;; We will eventually update try to update test_check_ext to see if it can automatically take
  ;; the smallest failing value and then execute the let block so that the failure will be displayed
  ;; as normal.

  (let [json (uj/umm->json user/failing-value)
          _ (is (empty? (js/validate-umm-json json)))
          parsed (uj/json->umm js/umm-c-schema json)]
      (is (= user/failing-value parsed)))

  )

(deftest validate-json-with-extra-fields
  (let [json (uj/umm->json (assoc minimal-example-record :foo "extra"))]
    (is (= ["object instance has properties which are not allowed by the schema: [\"foo\"]"]
           (js/validate-umm-json json)))))

