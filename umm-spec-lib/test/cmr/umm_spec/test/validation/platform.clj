(ns cmr.umm-spec.test.validation.platform
  "This has tests for UMM collection platform validations."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.models.umm-common-models :as c]
            [cmr.umm-spec.models.umm-collection-models :as coll]
            [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]))

(deftest collection-platforms-validation
  (let [s1 (c/map->InstrumentChildType {:ShortName "S1"})
        s2 (c/map->InstrumentChildType {:ShortName "S2"})
        i1 (c/map->InstrumentType {:ShortName "I1"
                                   :ComposedOf [s1 s2]})
        i2 (c/map->InstrumentType {:ShortName "I2"
                                   :ComposedOf [s1 s2]})
        c1 (c/map->CharacteristicType {:Name "C1"})
        c2 (c/map->CharacteristicType {:Name "C2"})
        c3 (c/map->CharacteristicType {:Name "AircraftID"
                                       :Value "C-FSJB"})
        c4 (c/map->CharacteristicType {:Name "AircraftID"
                                       :Value "N572AR"})]
    (testing "valid platforms"
      (h/assert-valid (coll/map->UMM-C
                       {:Platforms [(c/map->PlatformType {:ShortName "P1"
                                                          :Instruments [i1 i2]
                                                          :Characteristics [c1 c2]})
                                    (c/map->PlatformType {:ShortName "P2"
                                                          :Instruments [i1 i2]
                                                          :Characteristics [c1 c2]})]}))
      (h/assert-valid (coll/map->UMM-C
                       {:Platforms [(c/map->PlatformType {:ShortName "DHC-6"
                                                          :Characteristics [c3]})
                                    (c/map->PlatformType {:ShortName "DHC-6"
                                                          :Characteristics [c4]})]}))
      (h/assert-valid (coll/map->UMM-C
                       {:Platforms [(c/map->PlatformType {:ShortName "DHC-6"
                                                          :Characteristics [c3]})
                                    (c/map->PlatformType {:ShortName "DHC-6"
                                                          :Characteristics [{:Name "AircraftID"}]})]})))

    (testing "invalid platforms"
      (testing "duplicate platform short names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "P1"})
                                  (c/map->PlatformType {:ShortName "P1"})]})]
          (h/assert-invalid
            coll
            [:Platforms]
            ["The Platform ShortName [P1] must be unique. This record contains duplicates."])))
      (testing "double duplicate platform short names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "P1"})
                                  (c/map->PlatformType {:ShortName "P1"})
                                  (c/map->PlatformType {:ShortName "P2"})
                                  (c/map->PlatformType {:ShortName "P2"})]})]
          (h/assert-invalid
            coll
            [:Platforms]
            ["The Platform ShortName [P1] must be unique. This record contains duplicates."
             "The Platform ShortName [P2] must be unique. This record contains duplicates."])))

      (testing "duplicate platform short names with characteristics name"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "DHC-6"
                                                        :Characteristics [{:Name "AircraftID"}]})
                                  (c/map->PlatformType {:ShortName "DHC-6"
                                                        :Characteristics [{:Name "AircraftID"}]})]})]
          (h/assert-invalid
            coll
            [:Platforms]
            ["The combination of Platform ShortName [DHC-6] along with its Characteristic Name [AircraftID] must be unique. This record contains duplicates."])))
      (testing "duplicate platform short names with characteristics"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "DHC-6"
                                                        :Characteristics [c3]})
                                  (c/map->PlatformType {:ShortName "DHC-6"
                                                        :Characteristics [c3]})]})]
          (h/assert-invalid
            coll
            [:Platforms]
            ["The combination of Platform ShortName [DHC-6] along with its Characteristic Name [AircraftID] and Characteristic Value [C-FSJB] must be unique. This record contains duplicates."])))
      (testing "duplicate platform characteristics names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "P1"
                                                        :Characteristics [c1 c1]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Characteristics]
            ["Characteristics must be unique. This contains duplicates named [C1]."])))
      (testing "duplicate platform characteristics values"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "P1"
                                                        :Characteristics [c3 c3]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Characteristics]
            ["Characteristics must be unique. This contains duplicates named [AircraftID]."])))

      (testing "duplicate instrument short names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "P1"
                                                        :Instruments [i1 i1]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Instruments]
            ["Instruments must be unique. This contains duplicates named [I1]."])))
      (testing "duplicate instrument characteristics names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType
                                    {:ShortName "P1"
                                     :Instruments [(c/map->InstrumentType
                                                     {:ShortName "I1"
                                                      :Characteristics [c1 c1]})]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Instruments 0 :Characteristics]
            ["Characteristics must be unique. This contains duplicates named [C1]."])))
      (testing "duplicate sensor short names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType
                                    {:ShortName "P1"
                                     :Instruments [(c/map->InstrumentType {:ShortName "I1"
                                                                           :ComposedOf [s1 s1]})]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Instruments 0 :ComposedOf]
            ["Composed Of must be unique. This contains duplicates named [S1]."])))
      (testing "duplicate sensor characteristics names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType
                                    {:ShortName "P1"
                                     :Instruments [(c/map->InstrumentType
                                                     {:ShortName "I1"
                                                      :ComposedOf [(c/map->InstrumentChildType
                                                                    {:ShortName "S1"
                                                                     :Characteristics [c2 c2]})]})]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Instruments 0 :ComposedOf 0 :Characteristics]
            ["Characteristics must be unique. This contains duplicates named [C2]."])))
      (testing "multiple errors"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType
                                    {:ShortName "P1"})
                                  (c/map->PlatformType
                                    {:ShortName "P1"
                                     :Instruments [(c/map->InstrumentType {:ShortName "I1"
                                                                           :ComposedOf [s1 s1]})
                                                   (c/map->InstrumentType {:ShortName "I1"
                                                                           :ComposedOf [s1 s2 s2]})]})]})
              expected-errors [{:path [:Platforms 1 :Instruments 0 :ComposedOf]
                                :errors ["Composed Of must be unique. This contains duplicates named [S1]."]}
                               {:path [:Platforms 1 :Instruments 1 :ComposedOf]
                                :errors ["Composed Of must be unique. This contains duplicates named [S2]."]}
                               {:path [:Platforms 1 :Instruments]
                                :errors ["Instruments must be unique. This contains duplicates named [I1]."]}
                               {:path [:Platforms]
                                :errors ["The Platform ShortName [P1] must be unique. This record contains duplicates."]}]]
          (h/assert-multiple-invalid coll expected-errors))))))
