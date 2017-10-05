(ns cmr.umm-spec.test.validation.umm-spec-collection-validation-tests
  "This has tests for UMM validations."
  (:require
   [clj-time.core :as time]
   [clojure.test :refer :all]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.umm-spec.models.umm-common-models :as c]
   [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]))

(defn- coll-with-data-dates
  [data-dates]
  (coll/map->UMM-C {:DataDates
                    (map c/map->DateType data-dates)}))

(defn- coll-with-meta-data-dates
  [meta-data-dates]
  (coll/map->UMM-C {:MetadataDates
                    (map c/map->DateType meta-data-dates)}))

(deftest collection-data-date-validation
  (time-keeper/set-time-override! (time/date-time 2017 8 14))
  (testing "valid data dates"
    (testing "all nil cases for CREATE, UPDATE, REVIEW and DELETE"
      (h/assert-warnings-valid (coll-with-data-dates [{:Date (time/date-time 2000) :Type "UPDATE"}
                                                      {:Date (time/date-time 2018) :Type "REVIEW"}
                                                      {:Date (time/date-time 2019) :Type "DELETE"}]))
      (h/assert-warnings-valid (coll-with-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                      {:Date (time/date-time 2018) :Type "REVIEW"}
                                                      {:Date (time/date-time 2019) :Type "DELETE"}]))
      (h/assert-warnings-valid (coll-with-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                      {:Date (time/date-time 2011) :Type "UPDATE"}
                                                      {:Date (time/date-time 2019) :Type "DELETE"}]))
      (h/assert-warnings-valid (coll-with-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                      {:Date (time/date-time 2011) :Type "UPDATE"}
                                                      {:Date (time/date-time 2019) :Type "REVIEW"}])))
    (testing "all multiple value cases for CREATE, UPDATE, REVIEW and DELETE"
      (h/assert-warnings-valid (coll-with-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                      {:Date (time/date-time 2001) :Type "CREATE"}
                                                      {:Date (time/date-time 2002) :Type "UPDATE"}
                                                      {:Date (time/date-time 2003) :Type "UPDATE"}
                                                      {:Date (time/date-time 2018) :Type "REVIEW"}
                                                      {:Date (time/date-time 2019) :Type "REVIEW"}
                                                      {:Date (time/date-time 2020) :Type "DELETE"}
                                                      {:Date (time/date-time 2021) :Type "DELETE"}]))))
  (testing "invalid data dates"
    (h/assert-warnings-multiple-invalid (coll-with-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                               {:Date (time/date-time 2020) :Type "CREATE"}
                                                               {:Date (time/date-time 2000) :Type "UPDATE"}
                                                               {:Date (time/date-time 2021) :Type "UPDATE"}
                                                               {:Date (time/date-time 2000) :Type "REVIEW"}
                                                               {:Date (time/date-time 2020) :Type "REVIEW"}
                                                               {:Date (time/date-time 2000) :Type "DELETE"}
                                                               {:Date (time/date-time 2020) :Type "DELETE"}])
    [{:path [:DataDates]
     :errors ["CREATE date value: [2020-01-01T00:00:00.000Z] should be in the past. "
              "latest UPDATE date value: [2021-01-01T00:00:00.000Z] should be in the past. "
              "earliest REVIEW date value: [2000-01-01T00:00:00.000Z] should be in the future. "
              "DELETE date value: [2000-01-01T00:00:00.000Z] should be in the future. "
              "Earliest UPDATE date value: [2000-01-01T00:00:00.000Z] should be equal or later than CREATE date value: [2020-01-01T00:00:00.000Z]."
              "DELETE date value: [2000-01-01T00:00:00.000Z] should be equal or later than latest REVIEW date value: [2020-01-01T00:00:00.000Z]."]}]))

  (testing "valid meta data dates"
    (testing "all nil cases for CREATE, UPDATE, REVIEW and DELETE"
      (h/assert-warnings-valid (coll-with-meta-data-dates [{:Date (time/date-time 2000) :Type "UPDATE"}
                                                           {:Date (time/date-time 2018) :Type "REVIEW"}
                                                           {:Date (time/date-time 2019) :Type "DELETE"}]))
      (h/assert-warnings-valid (coll-with-meta-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                           {:Date (time/date-time 2018) :Type "REVIEW"}
                                                           {:Date (time/date-time 2019) :Type "DELETE"}]))
      (h/assert-warnings-valid (coll-with-meta-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                           {:Date (time/date-time 2011) :Type "UPDATE"}
                                                           {:Date (time/date-time 2019) :Type "DELETE"}]))
      (h/assert-warnings-valid (coll-with-meta-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                           {:Date (time/date-time 2011) :Type "UPDATE"}
                                                           {:Date (time/date-time 2019) :Type "REVIEW"}])))
    (testing "all multiple value cases for CREATE, UPDATE, REVIEW and DELETE"
      (h/assert-warnings-valid (coll-with-meta-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                           {:Date (time/date-time 2001) :Type "CREATE"}
                                                           {:Date (time/date-time 2002) :Type "UPDATE"}
                                                           {:Date (time/date-time 2003) :Type "UPDATE"}
                                                           {:Date (time/date-time 2018) :Type "REVIEW"}
                                                           {:Date (time/date-time 2019) :Type "REVIEW"}
                                                           {:Date (time/date-time 2020) :Type "DELETE"}
                                                           {:Date (time/date-time 2021) :Type "DELETE"}]))))
  (testing "invalid meta data dates"
    (h/assert-warnings-multiple-invalid (coll-with-meta-data-dates [{:Date (time/date-time 2000) :Type "CREATE"}
                                                                    {:Date (time/date-time 2020) :Type "CREATE"}
                                                                    {:Date (time/date-time 2000) :Type "UPDATE"}
                                                                    {:Date (time/date-time 2021) :Type "UPDATE"}
                                                                    {:Date (time/date-time 2000) :Type "REVIEW"}
                                                                    {:Date (time/date-time 2020) :Type "REVIEW"}
                                                                    {:Date (time/date-time 2000) :Type "DELETE"}
                                                                    {:Date (time/date-time 2020) :Type "DELETE"}])
    [{:path [:MetadataDates]
     :errors ["CREATE date value: [2020-01-01T00:00:00.000Z] should be in the past. "
              "latest UPDATE date value: [2021-01-01T00:00:00.000Z] should be in the past. "
              "earliest REVIEW date value: [2000-01-01T00:00:00.000Z] should be in the future. "
              "DELETE date value: [2000-01-01T00:00:00.000Z] should be in the future. "
              "Earliest UPDATE date value: [2000-01-01T00:00:00.000Z] should be equal or later than CREATE date value: [2020-01-01T00:00:00.000Z]."
              "DELETE date value: [2000-01-01T00:00:00.000Z] should be equal or later than latest REVIEW date value: [2020-01-01T00:00:00.000Z]."]}]))
) 

(deftest collection-temporal-validation
  (testing "valid temporal"
    (let [r1 (h/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:01Z")
          r2 (h/range-date-time "1999-12-30T19:00:00Z" nil)
          r3 (h/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")
          s1 (c/map->TemporalExtentType {:SingleDateTimes [(time/date-time 2014 12 1)]})]
      (h/assert-valid (h/coll-with-range-date-times [[r1]]))
      (h/assert-valid (h/coll-with-range-date-times [[r2]]))
      (h/assert-valid (h/coll-with-range-date-times [[r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1] [r2]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1 r2] [r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1 r2 r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1]] true)) ; EndsAtPresentFlag = true
      (h/assert-warnings-valid (h/coll-with-range-date-times [[r1 r2] [r3]]))
      (h/assert-warnings-valid (coll/map->UMM-C {:TemporalExtents [s1]}))))

  (testing "invalid temporal"
    (testing "single error"
      (let [r1 (h/range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (h/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
        (h/assert-invalid
         (h/coll-with-range-date-times [[r1]])
         [:TemporalExtents 0 :RangeDateTimes 0]
         ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])
        (h/assert-invalid
         (h/coll-with-range-date-times [[r2] [r1]])
         [:TemporalExtents 1 :RangeDateTimes 0]
         ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])))

    (testing "multiple errors"
      (let [r1 (h/range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (h/range-date-time "2000-12-30T19:00:02Z" "2000-12-30T19:00:01Z")]
        (h/assert-multiple-invalid
         (h/coll-with-range-date-times [[r1 r2]])
         [{:path [:TemporalExtents 0 :RangeDateTimes 0],
           :errors
           ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
          {:path [:TemporalExtents 0 :RangeDateTimes 1],
           :errors
           ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])
        (h/assert-multiple-invalid
         (h/coll-with-range-date-times [[r1] [r2]])
         [{:path [:TemporalExtents 0 :RangeDateTimes 0],
           :errors
           ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
          {:path [:TemporalExtents 1 :RangeDateTimes 0],
           :errors
           ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])))

    (testing "dates in past"
      (time-keeper/set-time-override! (time/date-time 2017 8 1))
      (testing "range date times"
        (let [r1 (h/range-date-time "2020-12-30T19:00:02Z" "2021-12-30T19:00:01Z")]
          (h/assert-warnings-multiple-invalid
           (h/coll-with-range-date-times [[r1]])
           [{:path [:TemporalExtents 0 :RangeDateTimes 0 :BeginningDateTime],
             :errors
             ["Date should be in the past."]}
            {:path [:TemporalExtents 0 :RangeDateTimes 0 :EndingDateTime],
             :errors
             [(str "Ending date should be in the past. Either set ending date to a date "
                   "in the past or remove end date and set the ends at present flag to true.")]}])))
      (testing "single date time"
        (let [c1 (c/map->TemporalExtentType {:SingleDateTimes [(time/date-time 2014 12 1)
                                                               (time/date-time 2020 12 30)]})]
          (h/assert-warnings-invalid
            (coll/map->UMM-C {:TemporalExtents [c1]})
            [:TemporalExtents 0 :SingleDateTimes 1]
            ["Date should be in the past."]))))))

(deftest collection-projects-validation
  (time-keeper/set-time-override! (time/date-time 2017 8 1))
  (let [c1 (c/map->ProjectType {:ShortName "C1"
                                :StartDate (time/date-time 2014 10 1)})
        c2 (c/map->ProjectType {:ShortName "C2"
                                :EndDate (time/date-time 2013 4 26)})
        c3 (c/map->ProjectType {:ShortName "C3"
                                :StartDate (time/date-time 2013 12 1)
                                :EndDate (time/date-time 2020 3 20)})
        c4 (c/map->ProjectType {:ShortName "C4"
                                :StartDate (time/date-time 2020 1 1)
                                :EndDate (time/date-time 2021 1 1)})
        c5 (c/map->ProjectType {:ShortName "C5"
                                :StartDate (time/date-time 2014 1 1)
                                :EndDate (time/date-time 2013 1 1)})]
    (testing "valid projects"
      (h/assert-valid (coll/map->UMM-C {:Projects [c1 c2 c3]}))
      (h/assert-warnings-valid (coll/map->UMM-C {:Projects [c1 c2 c3]})))

    (testing "invalid projects"
      (testing "duplicate names"
        (let [coll (coll/map->UMM-C {:Projects [c1 c1 c2 c2 c3]})]
          (h/assert-invalid
            coll
            [:Projects]
            ["Projects must be unique. This contains duplicates named [C1, C2]."])))
      (testing "start date not in the past"
        (let [coll (coll/map->UMM-C {:Projects [c4]})]
          (h/assert-warnings-invalid
            coll
            [:Projects 0 :StartDate]
            ["Date should be in the past."])))
      (testing "start date after end date"
        (let [coll (coll/map->UMM-C {:Projects [c5]})]
          (h/assert-invalid
            coll
            [:Projects 0]
            ["StartDate [2014-01-01T00:00:00.000Z] must be no later than EndDate [2013-01-01T00:00:00.000Z]"]))))))

(deftest metadata-associations-validation
  (testing "valid metadata associations"
    (h/assert-valid (coll/map->UMM-C
                     {:MetadataAssociations
                       [(c/map->MetadataAssociationType {:EntryId "S1"
                                                         :Version "V1"})
                        (c/map->MetadataAssociationType {:EntryId "S2"
                                                         :Version "V1"})
                        (c/map->MetadataAssociationType {:EntryId "S1"
                                                         :Version "V2"})]})))

  (testing "invalid metadata associations"
    (testing "duplicate names"
      (let [coll (coll/map->UMM-C
                  {:MetadataAssociations
                    [(c/map->MetadataAssociationType {:EntryId "S1"
                                                      :Version "V1"})
                     (c/map->MetadataAssociationType {:EntryId "S2"
                                                      :Version "V1"})
                     (c/map->MetadataAssociationType {:EntryId "S1"
                                                      :Version "V1"})]})]
        (h/assert-invalid
          coll
          [:MetadataAssociations]
          ["Metadata Associations must be unique. This contains duplicates named [(EntryId [S1] & Version [V1])]."])))))

(deftest collection-tiling-identification-systems-validation
  (let [t1 (c/map->TilingIdentificationSystemType {:TilingIdentificationSystemName "T1"
                                                   :Coordinate1 (c/map->TilingCoordinateType {:MinimumValue 0.0
                                                                                              :MaximumValue 6.0})
                                                   :Coordinate2 (c/map->TilingCoordinateType {:MinimumValue 10.0
                                                                                              :MaximumValue 10.0})})
        t2 (c/map->TilingIdentificationSystemType {:TilingIdentificationSystemName "T2"
                                                   :Coordinate1 (c/map->TilingCoordinateType {:MinimumValue 0.0})
                                                   :Coordinate2 (c/map->TilingCoordinateType {:MaximumValue 26.0})})
        t3 (c/map->TilingIdentificationSystemType {:TilingIdentificationSystemName "T3"
                                                   :Coordinate1 (c/map->TilingCoordinateType {:MinimumValue 10.0
                                                                                              :MaximumValue 6.0})})
        t4 (c/map->TilingIdentificationSystemType {:TilingIdentificationSystemName "T4"
                                                   :Coordinate1 (c/map->TilingCoordinateType {:MinimumValue 0.0
                                                                                              :MaximumValue 6.0})
                                                   :Coordinate2 (c/map->TilingCoordinateType {:MinimumValue 50.0
                                                                                              :MaximumValue 26.0})})]
    (testing "valid tiling identification systems"
      (h/assert-valid (coll/map->UMM-C {:TilingIdentificationSystems [t1 t2]})))

    (testing "invalid tiling identification systems"
      (testing "duplicate names"
        (let [coll (coll/map->UMM-C {:TilingIdentificationSystems [t1 t1]})]
          (h/assert-invalid
            coll
            [:TilingIdentificationSystems]
            ["Tiling Identification Systems must be unique. This contains duplicates named [T1]."])))
      (testing "invalid coordinate"
        (let [coll (coll/map->UMM-C {:TilingIdentificationSystems [t3]})]
          (h/assert-invalid
            coll
            [:TilingIdentificationSystems 0 :Coordinate1]
            ["Coordinate 1 minimum [10.0] must be less than or equal to the maximum [6.0]."])))
      (testing "multiple validation errors"
        (let [coll (coll/map->UMM-C {:TilingIdentificationSystems [t1 t1 t3 t4]})]
          (h/assert-multiple-invalid
            coll
            [{:path [:TilingIdentificationSystems],
              :errors
              ["Tiling Identification Systems must be unique. This contains duplicates named [T1]."]}
             {:path [:TilingIdentificationSystems 2 :Coordinate1],
              :errors
              ["Coordinate 1 minimum [10.0] must be less than or equal to the maximum [6.0]."]}
             {:path [:TilingIdentificationSystems 3 :Coordinate2],
              :errors
              ["Coordinate 2 minimum [50.0] must be less than or equal to the maximum [26.0]."]}]))))))
