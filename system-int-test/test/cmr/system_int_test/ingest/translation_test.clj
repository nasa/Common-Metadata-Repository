(ns cmr.system-int-test.ingest.translation-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [clj-time.core :as t]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.common.mime-types :as mt]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]))

(def example-record
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
     :DataLanguage "English"
     :TemporalExtents [(umm-cmn/map->TemporalExtentType
                         {:TemporalRangeType "temp range"
                          :PrecisionOfSeconds 3
                          :EndsAtPresentFlag false
                          :RangeDateTimes (mapv umm-cmn/map->RangeDateTimeType
                                                [{:BeginningDateTime (t/date-time 2000)
                                                  :EndingDateTime (t/date-time 2001)}
                                                 {:BeginningDateTime (t/date-time 2002)
                                                  :EndingDateTime (t/date-time 2003)}])})
                       (umm-cmn/map->TemporalExtentType
                         {:TemporalRangeType "temp range"
                          :PrecisionOfSeconds 3
                          :EndsAtPresentFlag false
                          :SingleDateTimes [(t/date-time 2003) (t/date-time 2004)]})
                       (umm-cmn/map->TemporalExtentType
                         {:TemporalRangeType "temp range"
                          :PrecisionOfSeconds 3
                          :EndsAtPresentFlag false
                          :PeriodicDateTimes (mapv umm-cmn/map->PeriodicDateTimeType
                                                   [{:Name "period1"
                                                     :StartDate (t/date-time 2000)
                                                     :EndDate (t/date-time 2001)
                                                     :DurationUnit "YEAR"
                                                     :DurationValue 4
                                                     :PeriodCycleDurationUnit "DAY"
                                                     :PeriodCycleDurationValue 3}
                                                    {:Name "period2"
                                                     :StartDate (t/date-time 2000)
                                                     :EndDate (t/date-time 2001)
                                                     :DurationUnit "YEAR"
                                                     :DurationValue 4
                                                     :PeriodCycleDurationUnit "DAY"
                                                     :PeriodCycleDurationValue 3}])})]}))


(comment

  (do
    (def input :dif)
    (def output :echo10)


    (def metadata (umm-spec/generate-metadata :collection input example-record))

    (def parsed-from-metadata (umm-spec/parse-metadata :collection input metadata))

    (def metadata-regen (umm-spec/generate-metadata :collection output parsed-from-metadata))

    (def parsed-from-metadata-regen (umm-spec/parse-metadata :collection output metadata-regen))
    )

  (println metadata)

  (println metadata-regen)

  (= metadata-regen metadata)


  (println (:body (ingest/translate-metadata :collection :echo10 metadata :echo10)))

  (def expected (-> example-record
                    (expected-conversion/convert input)
                    (expected-conversion/convert output)))

  )


(def valid-formats
  [:umm-json
   :iso-smap
   :iso19115
   :dif
   :dif10
   :echo10])

(defn assert-translate-failure
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 400 status))
    (is (re-find error-regex body))))

(deftest translate-metadata
  (doseq [input-format valid-formats
          output-format valid-formats]
    (testing (format "Translating %s to %s" (name input-format) (name output-format))
      (let [input-str (umm-spec/generate-metadata :collection input-format example-record)
            expected (-> example-record
                         (expected-conversion/convert input-format)
                         (expected-conversion/convert output-format))
            {:keys [status headers body]} (ingest/translate-metadata :collection input-format input-str output-format)
            content-type (:content-type headers)]
        (is (= 200 status))
        (is (= (mt/format->mime-type output-format) content-type))
        (is (= expected (umm-spec/parse-metadata :collection output-format body))))))

  (testing "Failure cases"
    (testing "unsupported input format"
      (assert-translate-failure
        #"The mime types specified in the content-type header \[application/xml\] are not supported"
        :collection :xml "notread" :umm-json))

    (testing "not specified input format"
      (assert-translate-failure
        #"The mime types specified in the content-type header \[\] are not supported"
        :collection nil "notread" :umm-json))

    (testing "unsupported output format"
      (assert-translate-failure
        #"The mime types specified in the accept header \[application/xml\] are not supported"
        :collection :echo10 "notread" :xml))

    (testing "not specified output format"
      (assert-translate-failure
        #"The mime types specified in the accept header \[\] are not supported"
        :collection :echo10 "notread" nil))

    (testing "invalid metadata"
      (testing "bad xml"
        (assert-translate-failure
          #"Cannot find the declaration of element 'this'"
          :collection :echo10 "<this> is not good XML</this>" :umm-json))

      (testing "wrong xml format"
        (assert-translate-failure
          #"Invalid content was found starting with element 'Version'"
          :collection :dif (umm-spec/generate-metadata :collection :dif10 example-record) :umm-json))

      (testing "bad json"
        (assert-translate-failure #"object has missing required properties"
                                  :collection :umm-json "{}" :echo10)))))


