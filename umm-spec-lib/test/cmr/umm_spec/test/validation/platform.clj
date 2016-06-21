(ns cmr.umm-spec.test.validation.platform
  "This has tests for UMM collection platform validations."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.models.common :as c]
            [cmr.umm-spec.models.collection :as coll]
            [cmr.umm-spec.test.validation.helpers :as h]))

(deftest collection-platforms-validation
  (let [s1 (c/map->SensorType {:ShortName "S1"})
        s2 (c/map->SensorType {:ShortName "S2"})
        i1 (c/map->InstrumentType {:ShortName "I1"
                                   :Sensors [s1 s2]})
        i2 (c/map->InstrumentType {:ShortName "I2"
                                   :Sensors [s1 s2]})
        c1 (c/map->CharacteristicType {:Name "C1"})
        c2 (c/map->CharacteristicType {:Name "C2"})]
    (testing "valid platforms"
      (h/assert-valid (coll/map->UMM-C
                      {:Platforms [(c/map->PlatformType {:ShortName "P1"
                                                         :Instruments [i1 i2]
                                                         :Characteristics [c1 c2]})
                                   (c/map->PlatformType {:ShortName "P2"
                                                         :Instruments [i1 i2]
                                                         :Characteristics [c1 c2]})]})))

    (testing "invalid platforms"
      (testing "duplicate platform short names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "P1"})
                                  (c/map->PlatformType {:ShortName "P1"})]})]
          (h/assert-invalid
            coll
            [:Platforms]
            ["Platforms must be unique. This contains duplicates named [P1]."])))
      (testing "duplicate platform characteristics names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType {:ShortName "P1"
                                                        :Characteristics [c1 c1]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Characteristics]
            ["Characteristics must be unique. This contains duplicates named [C1]."])))
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
                                                                           :Sensors [s1 s1]})]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Instruments 0 :Sensors]
            ["Sensors must be unique. This contains duplicates named [S1]."])))
      (testing "duplicate sensor characteristics names"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType
                                    {:ShortName "P1"
                                     :Instruments [(c/map->InstrumentType
                                                     {:ShortName "I1"
                                                      :Sensors [(c/map->SensorType
                                                                  {:ShortName "S1"
                                                                   :Characteristics [c2 c2]})]})]})]})]
          (h/assert-invalid
            coll
            [:Platforms 0 :Instruments 0 :Sensors 0 :Characteristics]
            ["Characteristics must be unique. This contains duplicates named [C2]."])))
      (testing "multiple errors"
        (let [coll (coll/map->UMM-C
                     {:Platforms [(c/map->PlatformType
                                    {:ShortName "P1"})
                                  (c/map->PlatformType
                                    {:ShortName "P1"
                                     :Instruments [(c/map->InstrumentType {:ShortName "I1"
                                                                           :Sensors [s1 s1]})
                                                   (c/map->InstrumentType {:ShortName "I1"
                                                                           :Sensors [s1 s2 s2]})]})]})
              expected-errors [{:path [:Platforms 1 :Instruments 0 :Sensors]
                                :errors ["Sensors must be unique. This contains duplicates named [S1]."]}
                               {:path [:Platforms 1 :Instruments 1 :Sensors]
                                :errors ["Sensors must be unique. This contains duplicates named [S2]."]}
                               {:path [:Platforms 1 :Instruments]
                                :errors ["Instruments must be unique. This contains duplicates named [I1]."]}
                               {:path [:Platforms]
                                :errors ["Platforms must be unique. This contains duplicates named [P1]."]}]]
          (h/assert-multiple-invalid coll expected-errors))))))

