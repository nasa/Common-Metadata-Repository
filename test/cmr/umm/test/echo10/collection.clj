(ns cmr.umm.test.echo10.collection
  "Tests parsing and generating ECHO10 Collection XML."
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [cmr.common.joda-time]
            [cmr.common.date-time-parser :as p]
            [cmr.umm.test.generators :as umm-gen]
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.collection :as umm-c]))

(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection umm-gen/collections]
    (let [xml (c/generate-collection collection)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))))))

(defspec generate-and-parse-collection-test 100
  (for-all [collection umm-gen/collections]
    (let [xml (c/generate-collection collection)
          parsed (c/parse-collection xml)]
      (= parsed collection))))

;; This is a made-up include all fields collection xml sample for the parse collection test
(def all-fields-collection-xml
  "<Collection>
    <ShortName>MINIMAL</ShortName>
    <VersionId>1</VersionId>
    <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
    <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
    <LongName>A minimal valid collection</LongName>
    <DataSetId>A minimal valid collection V 1</DataSetId>
    <Description>A minimal valid collection</Description>
    <Orderable>true</Orderable>
    <Visible>true</Visible>
    <Temporal>
      <TimeType>Universal Time</TimeType>
      <DateType>Eastern Daylight</DateType>
      <TemporalRangeType>Long Range</TemporalRangeType>
      <PrecisionOfSeconds>1</PrecisionOfSeconds>
      <EndsAtPresentFlag>false</EndsAtPresentFlag>
      <RangeDateTime>
        <BeginningDateTime>1996-02-24T22:20:41-05:00</BeginningDateTime>
        <EndingDateTime>1997-03-24T22:20:41-05:00</EndingDateTime>
      </RangeDateTime>
      <RangeDateTime>
        <BeginningDateTime>1998-02-24T22:20:41-05:00</BeginningDateTime>
        <EndingDateTime>1999-03-24T22:20:41-05:00</EndingDateTime>
      </RangeDateTime>
      <SingleDateTime>2010-01-05T05:30:30.550-05:00</SingleDateTime>
      <PeriodicDateTime>
        <Name>autumn, southwest</Name>
        <StartDate>1998-08-12T20:00:00-04:00</StartDate>
        <EndDate>1998-09-22T21:32:00-04:00</EndDate>
        <DurationUnit>DAY</DurationUnit>
        <DurationValue>3</DurationValue>
        <PeriodCycleDurationUnit>MONTH</PeriodCycleDurationUnit>
        <PeriodCycleDurationValue>7</PeriodCycleDurationValue>
      </PeriodicDateTime>
    </Temporal>
  </Collection>")

(def valid-collection-xml
  "<Collection>
    <ShortName>MINIMAL</ShortName>
    <VersionId>1</VersionId>
    <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
    <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
    <LongName>A minimal valid collection</LongName>
    <DataSetId>A minimal valid collection V 1</DataSetId>
    <Description>A minimal valid collection</Description>
    <Orderable>true</Orderable>
    <Visible>true</Visible>
  </Collection>")

(deftest parse-collection-test
  (let [expected (umm-c/map->UmmCollection
                   {:entry-id "MINIMAL_1"
                    :entry-title "A minimal valid collection V 1"
                    :product (umm-c/map->Product
                               {:short-name "MINIMAL"
                                :long-name "A minimal valid collection"
                                :version-id "1"})
                    :temporal-coverage (umm-c/map->TemporalCoverage
                                         {:time-type "Universal Time"
                                          :date-type "Eastern Daylight"
                                          :temporal-range-type "Long Range"
                                          :precision-of-seconds 1
                                          :ends-at-present-flag false
                                          :range-date-times [(umm-c/map->RangeDateTime
                                                               {:beginning-date-time (p/string->datetime "1996-02-24T22:20:41-05:00")
                                                                :ending-date-time (p/string->datetime "1997-03-24T22:20:41-05:00")})
                                                             (umm-c/map->RangeDateTime
                                                               {:beginning-date-time (p/string->datetime "1998-02-24T22:20:41-05:00")
                                                                :ending-date-time (p/string->datetime "1999-03-24T22:20:41-05:00")})]
                                          :single-date-times [(p/string->datetime "2010-01-05T05:30:30.550-05:00")]
                                          :periodic-date-times [(umm-c/map->PeriodicDateTime
                                                                  {:name "autumn, southwest"
                                                                   :start-date (p/string->datetime "1998-08-12T20:00:00-04:00")
                                                                   :end-date (p/string->datetime "1998-09-22T21:32:00-04:00")
                                                                   :duration-unit "DAY"
                                                                   :duration-value 3
                                                                   :period-cycle-duration-unit "MONTH"
                                                                   :period-cycle-duration-value 7})]})})
        actual (c/parse-collection all-fields-collection-xml)]
    (is (= expected actual))))

(deftest validate-xml
  (testing "valid xml"
    (is (= 0 (count (c/validate-xml valid-collection-xml)))))
  (testing "invalid xml"
    (is (= ["Line 4 - cvc-datatype-valid.1.2.1: 'XXXX-12-31T19:00:00-05:00' is not a valid value for 'dateTime'."
            "Line 4 - cvc-type.3.1.3: The value 'XXXX-12-31T19:00:00-05:00' of element 'InsertTime' is not valid."
            "Line 5 - cvc-datatype-valid.1.2.1: 'XXXX-12-31T19:00:00-05:00' is not a valid value for 'dateTime'."
            "Line 5 - cvc-type.3.1.3: The value 'XXXX-12-31T19:00:00-05:00' of element 'LastUpdate' is not valid."]
           (c/validate-xml (s/replace valid-collection-xml "1999" "XXXX"))))))

(comment

  (require '[clojure.test.check :as tc])
  (tc/quick-check 1
  (for-all [collection umm-gen/collections]
    (let [xml (c/generate-collection collection)
          _ (println "----xml: " xml)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))))))
  )