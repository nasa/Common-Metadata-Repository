(ns cmr.umm-spec.test.validation.umm-spec-collection-validation-tests
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.models.common :as c]
            [cmr.umm-spec.models.collection :as coll]
            [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]))

(deftest collection-temporal-validation
  (testing "valid temporal"
    (let [r1 (h/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:01Z")
          r2 (h/range-date-time "1999-12-30T19:00:00Z" nil)
          r3 (h/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
      (h/assert-valid (h/coll-with-range-date-times [[r1]]))
      (h/assert-valid (h/coll-with-range-date-times [[r2]]))
      (h/assert-valid (h/coll-with-range-date-times [[r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1] [r2]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1 r2] [r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1 r2 r3]]))
      (h/assert-valid (h/coll-with-range-date-times [[r1]] true)))) ; EndsAtPresentFlag = true

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
           ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])))))

(deftest collection-projects-validation
  (let [c1 (c/map->ProjectType {:ShortName "C1"})
        c2 (c/map->ProjectType {:ShortName "C2"})
        c3 (c/map->ProjectType {:ShortName "C3"})]
    (testing "valid projects"
      (h/assert-valid (coll/map->UMM-C {:Projects [c1 c2]})))

    (testing "invalid projects"
      (testing "duplicate names"
        (let [coll (coll/map->UMM-C {:Projects [c1 c1 c2 c2 c3]})]
          (h/assert-invalid
            coll
            [:Projects]
            ["Projects must be unique. This contains duplicates named [C1, C2]."]))))))

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
