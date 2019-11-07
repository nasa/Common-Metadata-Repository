(ns cmr.umm-spec.test.umm-spec-core-test
  "Tests for cmr.umm-spec.core functions"
  (:require
   [clojure.test :refer :all]
   [clj-time.core :as t]
   [cmr.common.util :as util :refer [are3]]
   [cmr.common.mime-types :as mt]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]))

(def context (lkt/setup-context-for-test))

(def umm-c-record
  "This is the minimum valid UMM-C."
  (umm-c/map->UMM-C
    {:Platforms [(umm-cmn/map->PlatformType
                   {:ShortName "Platform"
                    :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
     :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URL "http://google.com"
                                                 :URLContentType "DistributionURL"
                                                 :Type "GET DATA"})]
     :DataCenters [u/not-provided-data-center]
     :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent (umm-c/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})
     :AccessConstraints (umm-cmn/map->AccessConstraintsType {:Description "Test AccessConstraints"
                                                             :Value 2.0})
     :ShortName "short"
     :Version "V1"
     :EntryTitle "The entry title V5"
     :CollectionProgress "COMPLETE"
     :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                         :Type "CREATE"})]
     :Abstract "A very abstract collection"
     :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes ["2012-01-01T00:00:00.000Z"]
                                                         :EndsAtPresentFlag false})]}))

(defn- clean-parse
  "Cleans up the return of parse to remove empty sequences and nil values."
  [coll]
  (->> coll
       (map
        #(util/remove-map-keys
           (fn [v]
             (if (seq? v)
               (empty? v)
               false))
           %))
       (map util/remove-nil-keys)))

(deftest parse-concept-temporal-test
  (testing "parse collection temporal"
    (are3 [format expected]
      (is (= (clean-parse expected)
             (clean-parse
              (core/parse-concept-temporal
               {:metadata (core/generate-metadata context umm-c-record format)
                :format (mt/format->mime-type format)
                :concept-type :collection}))))

      "echo10"
      :echo10
      (:TemporalExtents umm-c-record)

      ;; dif is coded to return a RangeDateTimes even when the umm record only contains SingleDateTimes,
      "dif"
      :dif
      [{:RangeDateTimes
        [{:BeginningDateTime "2012-01-01T00:00:00.000Z",
          :EndingDateTime
          #=(cmr.common.joda-time/date-time 1325376000000 "UTC")}]}]

      "dif10"
      :dif10
      (:TemporalExtents umm-c-record)

      "iso19115"
      :iso19115
      (:TemporalExtents umm-c-record)

      "iso-smap"
      :iso-smap
      (:TemporalExtents umm-c-record)

      "umm-json"
      :umm-json
      (:TemporalExtents umm-c-record))))

(deftest parse-concept-access-value-test
  (testing "parse collection access value"
    (are3 [format expected]
      (is (= (umm-cmn/map->AccessConstraintsType expected)
             (umm-cmn/map->AccessConstraintsType
               (core/parse-concept-access-value
                 {:metadata (core/generate-metadata context umm-c-record format)
                  :format (mt/format->mime-type format)
                  :concept-type :collection}))))

      "echo10"
      :echo10
      (:AccessConstraints umm-c-record)

      "dif"
      :dif
      (:AccessConstraints umm-c-record)


      "dif10"
      :dif10
      (:AccessConstraints umm-c-record)


      "iso19115"
      :iso19115
      (:AccessConstraints umm-c-record)


      "iso-smap"
      :iso-smap
      (:AccessConstraints umm-c-record)

      "umm-json"
      :umm-json
      (:AccessConstraints umm-c-record))))
