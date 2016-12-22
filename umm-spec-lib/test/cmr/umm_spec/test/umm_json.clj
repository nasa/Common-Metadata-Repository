(ns cmr.umm-spec.test.umm-json
  (:require [clojure.test :refer :all]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [cmr.common.test.test-check-ext :as ext :refer [checking]]
            [cmr.umm-spec.umm-json :as uj]
            [cmr.umm-spec.models.umm-collection-models :as umm-c]
            [cmr.umm-spec.models.umm-service-models :as umm-s]
            [cmr.umm-spec.models.umm-common-models :as umm-cmn]
            [clj-time.core :as t]
            [cmr.umm-spec.util :as u]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.test.umm-generators :as umm-gen]
            [clojure.test.check.generators :as gen]))

(def minimal-example-umm-c-record
  "This is the minimum valid UMM-C."
  (umm-c/map->UMM-C
    {:Platforms [(umm-cmn/map->PlatformType
                   {:ShortName "Platform"
                    :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
     :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URLs ["http://google.com"]})]
     :DataCenters [u/not-provided-data-center]
     :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})

     :ShortName "short"
     :Version "V1"
     :EntryTitle "The entry title V5"
     :CollectionProgress "COMPLETE"
     :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                         :Type "CREATE"})]
     :Abstract "A very abstract collection"
     :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(t/date-time 2012)]})]}))

(def minimal-example-umm-s-record
  "This is the minimum valid UMM-S"
  (umm-s/map->UMM-S
    {:EntryTitle "Test Service"
     :EntryId "Entry ID Goes Here"
     :Abstract "An Abstract UMM-S Test Example"
     :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URLs ["http://google.com"]})]
     :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :ServiceKeywords [(umm-s/map->ServiceKeywordType {:Category "cat" :Topic "top" :Term "ter" :ServiceSpecificName "SSN"})]}))

(def contact-group-example-umm-c-record
  "This is the minimum valid UMM-C with contact groups."
  (umm-c/map->UMM-C
    {:Platforms [(umm-cmn/map->PlatformType
                   {:ShortName "Platform"
                    :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
     :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URLs ["http://google.com"]})]
     :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})

     :ShortName "short"
     :Version "V1"
     :EntryTitle "The entry title V5"
     :CollectionProgress "COMPLETE"
     :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                         :Type "CREATE"})]
     :Abstract "A very abstract collection"
     :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(t/date-time 2012)]})]
     :DataCenters [u/not-provided-data-center]
     :ContactGroups [(umm-cmn/map->ContactGroupType {:Roles ["Investigator"]
                                                     :GroupName "ABC"})]}))

;; This only tests a minimum example record for now. We need to test with larger more complicated
;; records. We will do this as part of CMR-1929

(deftest generate-and-parse-umm-s-json
  (testing "minimal umm-s record"
    (let [json (uj/umm->json  minimal-example-umm-s-record)
          _ (is (empty? (js/validate-umm-json json :service)))
          parsed (uj/json->umm {} :service json)]
      (is (= minimal-example-umm-s-record parsed)))))

(deftest generate-and-parse-umm-c-json
  (testing "minimal umm-c record"
    (let [json (uj/umm->json minimal-example-umm-c-record)
          _ (is (empty? (js/validate-umm-json json :collection)))
          parsed (uj/json->umm {} :collection json)]
      (is (= minimal-example-umm-c-record parsed)))))

(deftest generate-and-parse-contact-group-umm-c-json
  (testing "contact group umm-c record. We add this test mostly for future when we add allOf
           support in umm-spec-lib to verify that validation is done correctly."
    (let [json (uj/umm->json contact-group-example-umm-c-record)
          _ (is (empty? (js/validate-umm-json json :collection)))
          parsed (uj/json->umm {} :collection json)]
      (is (= contact-group-example-umm-c-record parsed)))))

(deftest all-umm-c-records
  (checking "all umm-c records" 1
    [umm-c-record (gen/no-shrink umm-gen/umm-c-generator)]
    (let [json (uj/umm->json umm-c-record)
          _ (is (empty? (js/validate-umm-json json :collection)))
          parsed (uj/json->umm {} :collection json)]
      (is (= umm-c-record parsed)))))

(deftest all-umm-s-records
  (checking "all umm-s records" 100
    [umm-s-record (gen/no-shrink umm-gen/umm-s-generator)]
    (let [json (uj/umm->json umm-s-record)
          _ (is (empty? (js/validate-umm-json json :service)))
          parsed (uj/json->umm {} :service json)]
      (is (= umm-s-record parsed)))))

(deftest validate-json-with-extra-fields
  (let [json (uj/umm->json (assoc minimal-example-umm-c-record :foo "extra"))]
    (is (= ["object instance has properties which are not allowed by the schema: [\"foo\"]"]
           (js/validate-umm-json json :collection)))))

(deftest json-schema-coercion
  (is (= (js/parse-umm-c
          {:EntryTitle "an entry title"
           :Abstract "A very abstract collection"
           :DataLanguage "eng"
           :TemporalExtents [{:TemporalRangeType "temp range"
                              :PrecisionOfSeconds "3"
                              :EndsAtPresentFlag "false"
                              :RangeDateTimes [{:BeginningDateTime "2000-01-01T00:00:00.000Z"
                                                :EndingDateTime "2001-01-01T00:00:00.000Z"}
                                               {:BeginningDateTime "2002-01-01T00:00:00.000Z"
                                                :EndingDateTime "2003-01-01T00:00:00.000Z"}]}]})
         (umm-c/map->UMM-C
          {:EntryTitle "an entry title"
           :Abstract "A very abstract collection"
           :DataLanguage "eng"
           :TemporalExtents [(umm-cmn/map->TemporalExtentType
                              {:TemporalRangeType "temp range"
                               :PrecisionOfSeconds 3
                               :EndsAtPresentFlag false
                               :RangeDateTimes [(umm-cmn/map->RangeDateTimeType
                                                 {:BeginningDateTime (t/date-time 2000)
                                                  :EndingDateTime (t/date-time 2001)})
                                                (umm-cmn/map->RangeDateTimeType
                                                 {:BeginningDateTime (t/date-time 2002)
                                                  :EndingDateTime (t/date-time 2003)})]})]}))))

(deftest json-schema-parsing-errors

  (testing "UMM-C with parsing errors"
    (let [umm-c (js/parse-umm-c {:SpatialExtent {:OrbitParameters {:NumberOfOrbits "foo"
                                                                   :SwathWidth "123"}}
                                 :TemporalExtents [{:SingleDateTimes ["nonsense"
                                                                      "2000-01-01T00:00:00.000Z"]}]})
          orbit-params (-> umm-c :SpatialExtent :OrbitParameters)]
      (is (= 123.0 (:SwathWidth orbit-params)))
      (is (= {:NumberOfOrbits "Could not parse number value: foo"} (:_errors orbit-params)))
      (is (= [nil (t/date-time 2000 1 1)]
             (-> umm-c :TemporalExtents first :SingleDateTimes)))
      (is (= {:SingleDateTimes ["Could not parse date-time value: nonsense" nil]}
             (-> umm-c :TemporalExtents first :_errors)))))

  (testing "UMM-C with no parsing errors"
    (let [umm-c (js/parse-umm-c {:SpatialExtent {:OrbitParameters {:NumberOfOrbits "30"
                                                                   :SwathWidth "123"}}
                                 :TemporalExtents [{:SingleDateTimes ["2000-01-01T00:00:00.000Z"
                                                                      "2005-01-01T00:00:00.000Z"]}]})
          orbit-params (-> umm-c :SpatialExtent :OrbitParameters)]
      (is (= 123.0 (:SwathWidth orbit-params)))
      (is (= nil (:_errors orbit-params)))
      (is (= [(t/date-time 2000) (t/date-time 2005)]
             (-> umm-c :TemporalExtents first :SingleDateTimes)))
      (is (= nil
             (-> umm-c :TemporalExtents first :_errors))))))
