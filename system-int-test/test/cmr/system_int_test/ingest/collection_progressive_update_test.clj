(ns cmr.system-int-test.ingest.collection-progressive-update-test
  "CMR Ingest collection progressive update integration tests"
  (:require
    [clj-time.core :as time-core]
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as data-core]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest collection-progressive-update-test
  (testing "Invalid additional attributes, temporal extents and data dates"
    ;; first ingest a valid collection.
    (let [coll (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "ERS-2_LEVEL0"
                                                               :ShortName "ERS-2_L0"
                                                               :Version "1"}))]
      (index/wait-until-indexed)
      (testing "Progressive update collection successful case - mimic existing errors"
        (let [additional-attributes [(data-umm-cmn/additional-attribute {:Name "int" :DataType "STRING" :ParameterRangeBegin 1 :ParameterRangeEnd 10})]
              temporal-extents [(data-umm-cmn/temporal-extent {:beginning-date-time "2001-01-01T12:00:00Z"
                                                               :ending-date-time "2000-05-11T12:00:00Z"})]
              data-dates [(umm-cmn/map->DateType {:Date (time-core/date-time 2032) :Type "DELETE"})]
              ;; when :test-existing-errors set to true and the token is mock-echo-system-token, it's mimcing the case when the previous revision collection
              ;; has the same validation errors as the current collection, which allows invalid collection to be ingested.
              options {:test-existing-errors true :token "mock-echo-system-token"}
              response (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                                      {:EntryTitle "ERS-2_LEVEL0"
                                                                       :ShortName "ERS-2_L0"
                                                                       :Version "1"
                                                                       :AdditionalAttributes additional-attributes
                                                                       :TemporalExtents temporal-extents
                                                                       :DataDates data-dates})
                                                             options)
              expected-existing-error-temporal "[:TemporalExtents 0 :RangeDateTimes 0] BeginningDateTime [2001-01-01T12:00:00.000Z] must be no later than EndingDateTime [2000-05-11T12:00:00.000Z]"
              expected-existing-error-additional"[:AdditionalAttributes 0] Parameter Range Begin is not allowed for type [STRING] Parameter Range End is not allowed for type [STRING]"
              {:keys [errors warnings existing-errors concept-id revision-id]} response]
          (is (= concept-id (:concept-id coll)))
          (is (= revision-id (+ 1 (:revision-id coll))))
          (is (= nil errors))
          (is (string/includes? existing-errors expected-existing-error-temporal))
          (is (string/includes? existing-errors expected-existing-error-additional))))

        (testing "Progressive update collection successful case, real existing errors"
          (let [additional-attributes [(data-umm-cmn/additional-attribute {:Name "int" :DataType "STRING" :ParameterRangeBegin 1 :ParameterRangeEnd 10})]
                temporal-extents [(data-umm-cmn/temporal-extent {:beginning-date-time "2001-01-01T12:00:00Z"
                                                                 :ending-date-time "2000-05-11T12:00:00Z"})]
                data-dates [(umm-cmn/map->DateType {:Date (time-core/date-time 2032) :Type "DELETE"})]
                ;; now that the previous concept containing validation errors, we don't need to pass options to mimic existing errors.
                response (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                                        {:EntryTitle "ERS-2_LEVEL0"
                                                                         :ShortName "ERS-2_L0"
                                                                         :Version "1"
                                                                         :AdditionalAttributes additional-attributes
                                                                         :TemporalExtents temporal-extents
                                                                         :DataDates data-dates}))
                expected-existing-error-temporal "[:TemporalExtents 0 :RangeDateTimes 0] BeginningDateTime [2001-01-01T12:00:00.000Z] must be no later than EndingDateTime [2000-05-11T12:00:00.000Z]"
                expected-existing-error-additional "[:AdditionalAttributes 0] Parameter Range Begin is not allowed for type [STRING] Parameter Range End is not allowed for type [STRING]"
                {:keys [errors warnings existing-errors concept-id revision-id]} response]
            (is (= concept-id (:concept-id coll)))
            (is (= revision-id (+ 2 (:revision-id coll))))
            (is (= nil errors))
            (is (string/includes? existing-errors expected-existing-error-temporal))
            (is (string/includes? existing-errors expected-existing-error-additional))))

        (testing "Progressive update collection successful case, real existing errors in json format"
          (let [additional-attributes [(data-umm-cmn/additional-attribute {:Name "int" :DataType "STRING" :ParameterRangeBegin 1 :ParameterRangeEnd 10})]
                temporal-extents [(data-umm-cmn/temporal-extent {:beginning-date-time "2001-01-01T12:00:00Z"
                                                                 :ending-date-time "2000-05-11T12:00:00Z"})]
                data-dates [(umm-cmn/map->DateType {:Date (time-core/date-time 2032) :Type "DELETE"})]
                ;; now that the previous concept containing validation errors, we don't need to pass options to mimic existing errors.
                response (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                                        {:EntryTitle "ERS-2_LEVEL0"
                                                                         :ShortName "ERS-2_L0"
                                                                         :Version "1"
                                                                         :AdditionalAttributes additional-attributes
                                                                         :TemporalExtents temporal-extents
                                                                         :DataDates data-dates})
                                                                       {:accept-format :json})]
            (is (string/includes? (:body response) "\"existing-errors\":"))))

        (testing "Progressive update collection successful case, real existing errors in xml format"
          (let [additional-attributes [(data-umm-cmn/additional-attribute {:Name "int" :DataType "STRING" :ParameterRangeBegin 1 :ParameterRangeEnd 10})]
                temporal-extents [(data-umm-cmn/temporal-extent {:beginning-date-time "2001-01-01T12:00:00Z"
                                                                 :ending-date-time "2000-05-11T12:00:00Z"})]
                data-dates [(umm-cmn/map->DateType {:Date (time-core/date-time 2032) :Type "DELETE"})]
                ;; now that the previous concept containing validation errors, we don't need to pass options to mimic existing errors.
                response (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                                        {:EntryTitle "ERS-2_LEVEL0"
                                                                         :ShortName "ERS-2_L0"
                                                                         :Version "1"
                                                                         :AdditionalAttributes additional-attributes
                                                                         :TemporalExtents temporal-extents
                                                                         :DataDates data-dates}))]
            (is (and (string/includes? (:body response) "<existing-errors>")
                     (string/includes? (:body response) "</existing-errors>")))))

        (testing "Progressive update collection failed case, introducing new data-dates errors"
          (let [additional-attributes [(data-umm-cmn/additional-attribute {:Name "int" :DataType "STRING" :ParameterRangeBegin 1 :ParameterRangeEnd 10})]
                temporal-extents [(data-umm-cmn/temporal-extent {:beginning-date-time "2001-01-01T12:00:00Z"
                                                                 :ending-date-time "2000-05-11T12:00:00Z"})]
                data-dates [(umm-cmn/map->DateType {:Date (time-core/date-time 2000) :Type "DELETE"})]
                ;; now that the previous concept containing validation errors, we don't need to pass options to mimic existing errors.
                response (try (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                                             {:EntryTitle "ERS-2_LEVEL0"
                                                                              :ShortName "ERS-2_L0"
                                                                              :Version "1"
                                                                              :AdditionalAttributes additional-attributes
                                                                              :TemporalExtents temporal-extents
                                                                              :DataDates data-dates}))
                           (catch Exception e
                             (.toString e)))
                exception "java.lang.Exception: Ingest failed when expected to succeed: {:errors (\"DeleteTime 2000-01-01T00:00:00.000Z is before the current time.\"), :status 422}"]
            (is (= response exception)))))))
