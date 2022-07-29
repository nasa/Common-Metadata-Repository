(ns cmr.system-int-test.ingest.collection-update-test
  "CMR collection update integration tests"
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.spatial.point :as p]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.umm-spec.additional-attribute :as aa]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       hu/grant-all-humanizers-fixture
                       hu/save-sample-humanizers-fixture]))

(deftest collection-update-additional-attributes-general-test
  (let [a1 (data-umm-cmn/additional-attribute {:Name "string" :DataType "STRING"})
        a2 (data-umm-cmn/additional-attribute {:Name "boolean" :DataType "BOOLEAN"})
        a3 (data-umm-cmn/additional-attribute {:Name "int" :DataType "INT" :value 5})
        a4 (data-umm-cmn/additional-attribute {:Name "float" :DataType "FLOAT" :min-value 1.0 :max-value 10.0})
        a5 (data-umm-cmn/additional-attribute {:Name "datetime" :DataType "DATETIME"})
        a6 (data-umm-cmn/additional-attribute {:Name "date" :DataType "DATE"})
        a7 (data-umm-cmn/additional-attribute {:Name "time" :DataType "TIME"})
        a8 (data-umm-cmn/additional-attribute {:Name "dts" :DataType "DATETIME_STRING"})
        a9 (data-umm-cmn/additional-attribute {:Name "moo" :DataType "STRING"})

        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :AdditionalAttributes [a1 a2 a3 a4 a5 a6 a7 a8 a9]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "string" ["alpha"])]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "boolean" ["true"])]}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "int" ["2"])]}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "float" ["2.0"])]}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "datetime" ["2012-01-01T01:02:03Z"])]}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "date" ["2012-01-02Z"])]}))
        gran7 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "time" ["01:02:03Z"])]}))
        gran8 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "dts" ["2012-01-01T01:02:03Z"])]}))
        ;; The following collection and granule are added to verify that the validation is using
        ;; the collection concept id for searching granules. If we don't use the collection concept
        ;; id during granule search the test that changes additional attribute with name "int" to
        ;; a range of [1 10] would have failed.
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                     {:EntryTitle "parent-collection-1"
                                                      :AdditionalAttributes [a3]}))
        gran9 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 "C1-PROV1" {:product-specific-attributes
                                                                                       [(dg/psa "int" ["20"])]}))]
    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (are3
        [additional-attributes]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection"
                                                               :ShortName "S1"
                                                               :Version "V1"
                                                               :AdditionalAttributes additional-attributes}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Not changing any additional attributes is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8 a9]

        "Add an additional attribute is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8 a9 (data-umm-cmn/additional-attribute {:Name "alpha" :DataType "INT"})]

        "Removing an additional attribute that is not referenced by any granule is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8]

        "Changing the type of an additional attribute that is not referenced by any granule is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8 (data-umm-cmn/additional-attribute {:Name "moo" :DataType "INT"})]

        "Changing the value of an additional attribute is OK."
        [a1 a2 (data-umm-cmn/additional-attribute {:Name "int" :DataType "INT" :Value 10}) a4 a5 a6 a7 a8 a9]

        "Change additional attribute value to a range is OK."
        [a1 a2 (data-umm-cmn/additional-attribute {:Name "int" :DataType "INT" :ParameterRangeBegin 1 :ParameterRangeEnd 10}) a4 a5 a6 a7 a8 a9]

        "Removing the value/range of an additional attribute is OK."
        [a1 a2 (data-umm-cmn/additional-attribute {:Name "int" :DataType "INT"}) a4 a5 a6 a7 a8 a9]

        "Change additional attribute range to a value is OK."
        [a1 a2 a3 (data-umm-cmn/additional-attribute {:Name "float" :DataType "FLOAT" :Value 1.0}) a5 a6 a7 a8 a9]

        "Extending additional attribute range is OK."
        [a1 a2 a3 (data-umm-cmn/additional-attribute {:Name "float" :DataType "FLOAT" :ParameterRangeBegin 0.0 :ParameterRangeEnd 99.0})
         a5 a6 a7 a8 a9]))

    (testing "Update collection failure cases"
      (are3
        [additional-attributes expected-errors]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection"
                                                               :ShortName "S1"
                                                               :Version "V1"
                                                               :AdditionalAttributes additional-attributes})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Removing an additional attribute that is referenced by its granules is invalid."
        [a2 a3 a4 a5 a6 a7 a8 a9]
        ["Collection additional attribute [string] is referenced by existing granules, cannot be removed. Found 1 granules."]

        "Multiple validation errors."
        [(data-umm-cmn/additional-attribute {:Name "float" :DataType "FLOAT" :ParameterRangeBegin 5.0 :ParameterRangeEnd 10.0})]
        ["Collection additional attribute [string] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [boolean] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [int] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [datetime] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [date] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [time] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [dts] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [float] cannot be changed since there are existing granules outside of the new value range. Found 1 granules."]

        "Changing an additional attribute type that is referenced by its granules is invalid."
        [(data-umm-cmn/additional-attribute {:Name "string" :DataType "INT"}) a2 a3 a4 a5 a6 a7 a8 a9]
        ["Collection additional attribute [string] was of DataType [STRING], cannot be changed to [INT]. Found 1 granules."]))

    (testing "Delete the existing collection, then re-create it with any additional attributes is OK."
      (ingest/delete-concept (d/item->concept coll :echo10))
      (index/wait-until-indexed)
      (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                            {:EntryTitle "parent-collection"
                                                             :ShortName "S1"
                                                             :Version "V1"
                                                             :AdditionalAttributes [a9]}))
            {:keys [status errors]} response]
        (is (= [200 nil] [status errors]))))))

(deftest collection-update-additional-attributes-int-range-test
  (let [a1 (data-umm-cmn/additional-attribute {:Name "int" :DataType "INT" :ParameterRangeBegin 1 :ParameterRangeEnd 10})
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :AdditionalAttributes [a1]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "int" ["2"])]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "int" ["5"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "int"
                                                                                               :DataType "INT"
                                                                                               :ParameterRangeBegin (first range)
                                                                                               :ParameterRangeEnd (second range)})]}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "expanded range" [0 11]
        "same range" [1 10]
        "removed min" [nil 10]
        "removed max" [1 nil]
        "no range" []
        "minimal range" [2 5]))

    (testing "failure cases"
      (are3
        [range num-grans]
        (let [expected-error (->> (format " Found %d granules." num-grans)
                                  (str "Collection additional attribute [int] cannot be changed since there are existing granules outside of the new value range."))
              response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "int"
                                                                                               :DataType "INT"
                                                                                               :ParameterRangeBegin (first range)
                                                                                               :ParameterRangeEnd (second range)})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" [0 4] 1
        "invalid max no min" [nil 4] 1
        "invalid min" [3 6] 1
        "invalid min no max" [3 nil] 1
        "invalid min & max" [3 4] 2))))

(deftest collection-update-additional-attributes-float-range-test
  (let [a1 (data-umm-cmn/additional-attribute {:Name "float" :DataType "FLOAT" :ParameterRangeBegin -10.0 :ParameterRangeEnd 10.0})
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :AdditionalAttributes [a1]}))
        gran1 (d/ingest "PROV1"(dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                     [(dg/psa "float" ["-2.0"])]}))
        gran2 (d/ingest "PROV1"(dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                     [(dg/psa "float" ["5.0"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "float"
                                                                                               :DataType "FLOAT"
                                                                                               :ParameterRangeBegin (first range)
                                                                                               :ParameterRangeEnd (second range)})]}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "expanded range" [-11.0 11.0]
        "same range" [-10.0 10.0]
        "removed min" [nil 10.0]
        "removed max" [-10.0 nil]
        "no range"[]
        "minimal range" [-2.0 5.0]))
    (testing "failure cases"
      (are3
        [range num-grans]
        (let [expected-error (->> (format " Found %d granules." num-grans)
                                  (str "Collection additional attribute [float] cannot be changed since there are existing granules outside of the new value range."))
              response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "float"
                                                                                               :DataType "FLOAT"
                                                                                               :ParameterRangeBegin (first range)
                                                                                               :ParameterRangeEnd (second range)})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" [-3.0 4.99] 1
        "invalid max no min" [nil 4.99] 1
        "invalid min" [-1.99 6] 1
        "invalid min no max" [-1.99 nil] 1
        "invalid min & max" [-1.99 4.99] 2
        "invalid & very close to min" [(Double/longBitsToDouble (dec (Double/doubleToLongBits -2.0))) nil] 1
        "invalid & very cleose to max" [nil (Double/longBitsToDouble (dec (Double/doubleToLongBits 5.0)))] 1))))

(deftest collection-update-additional-attributes-datetime-range-test
  (let [parse-fn (partial aa/parse-value "DATETIME")
        a1 (data-umm-cmn/additional-attribute {:Name "datetime" :DataType "DATETIME"
                                               :ParameterRangeBegin (parse-fn "2012-02-01T01:02:03Z")
                                               :ParameterRangeEnd (parse-fn "2012-11-01T01:02:03Z")})
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :AdditionalAttributes [a1]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "datetime" ["2012-04-01T01:02:03Z"])]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "datetime" ["2012-08-01T01:02:03Z"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "datetime"
                                                                                               :DataType "DATETIME"
                                                                                               :ParameterRangeBegin (parse-fn (first range))
                                                                                               :ParameterRangeEnd (parse-fn (second range))})]}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "expanded range" ["2012-02-01T01:02:02Z" "2012-11-01T01:02:04Z"]
        "same range" ["2012-02-01T01:02:03Z" "2012-11-01T01:02:03Z"]
        "removed min" [nil "2012-08-01T01:02:03Z"]
        "removed max" ["2012-04-01T01:02:03Z" nil]
        "no range" []
        "minimal range" ["2012-04-01T01:02:03Z" "2012-08-01T01:02:03Z"]))
    (testing "failure cases"
      (are3
        [range num-grans]
        (let [expected-error (->> (format " Found %d granules." num-grans)
                                  (str "Collection additional attribute [datetime] cannot be changed since there are existing granules outside of the new value range."))
              response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "datetime"
                                                                                               :DataType "DATETIME"
                                                                                               :ParameterRangeBegin (parse-fn (first range))
                                                                                               :ParameterRangeEnd (parse-fn (second range))})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" ["2012-02-01T01:02:02Z" "2012-08-01T01:02:02.999Z"] 1
        "invalid max no min" [nil "2012-08-01T01:02:02.999Z"] 1
        "invalid min" ["2012-04-01T01:02:03.001Z" "2012-11-01T01:02:04Z"] 1
        "invalid min no max" ["2012-04-01T01:02:03.001Z" nil] 1
        "invalid min & max" ["2012-04-01T01:02:03.001Z" "2012-08-01T01:02:02.999Z"] 2))))

(deftest collection-update-additional-attributes-date-range-test
  (let [parse-fn (partial aa/parse-value "DATE")
        a1 (data-umm-cmn/additional-attribute {:Name "date" :DataType "DATE"
                                               :ParameterRangeBegin (parse-fn "2012-02-02Z")
                                               :ParameterRangeEnd (parse-fn "2012-11-02Z")})
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :AdditionalAttributes [a1]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "date" ["2012-04-02Z"])]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "date" ["2012-08-02Z"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "date"
                                                                                               :DataType "DATE"
                                                                                               :ParameterRangeBegin (parse-fn (first range))
                                                                                               :ParameterRangeEnd (parse-fn (second range))})]}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "expanded range" ["2012-02-01Z" "2012-11-03Z"]
        "same range" ["2012-02-02Z" "2012-11-02Z"]
        "removed min" [nil "2012-11-02Z"]
        "removed max" ["2012-02-02Z" nil]
        "no range" []
        "minimal range" ["2012-04-02Z" "2012-08-02Z"]))
    (testing "failure cases"
      (are3
        [range num-grans]
        (let [expected-error (->> (format " Found %d granules." num-grans)
                                  (str "Collection additional attribute [date] cannot be changed since there are existing granules outside of the new value range."))
              response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "date"
                                                                                               :DataType "DATE"
                                                                                               :ParameterRangeBegin (parse-fn (first range))
                                                                                               :ParameterRangeEnd (parse-fn (second range))})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" ["2012-02-01Z" "2012-08-01Z"] 1
        "invalid max no min" [nil "2012-08-01Z"] 1
        "invalid min" ["2012-04-03Z" "2012-08-03Z"] 1
        "invalid min no max" ["2012-04-03Z" nil] 1
        "invalid min & max" ["2012-04-03Z" "2012-08-01Z"] 2))))

(deftest collection-update-additional-attributes-time-range-test
  (let [parse-fn (partial aa/parse-value "TIME")
        a1 (data-umm-cmn/additional-attribute {:Name "time" :DataType "TIME"
                                               :ParameterRangeBegin (parse-fn "01:02:03Z")
                                               :ParameterRangeEnd (parse-fn "11:02:03Z")})
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :AdditionalAttributes [a1]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "time" ["04:02:03Z"])]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:product-specific-attributes
                                                                                      [(dg/psa "time" ["06:02:03Z"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "time"
                                                                                               :DataType "TIME"
                                                                                               :ParameterRangeBegin (parse-fn (first range))
                                                                                               :ParameterRangeEnd (parse-fn (second range))})]}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "expanded range" ["01:02:02Z" "11:02:04Z"]
        "same range" ["01:02:03Z" "11:02:03Z"]
        "removed min" [nil "11:02:03Z"]
        "removed max" ["01:02:03Z" nil]
        "no range" []
        "minimal range" ["04:02:03Z" "06:02:03Z"]))
    (testing "failure cases"
      (are3
        [range num-grans]
        (let [expected-error (->> (format " Found %d granules." num-grans)
                                  (str "Collection additional attribute [time] cannot be changed since there are existing granules outside of the new value range."))
              response (d/ingest-umm-spec-collection "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "parent-collection"
                                    :ShortName "S1"
                                    :Version "V1"
                                    :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "time"
                                                                                               :DataType "TIME"
                                                                                               :ParameterRangeBegin (parse-fn (first range))
                                                                                               :ParameterRangeEnd (parse-fn (second range))})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" ["01:02:04Z" "06:02:02.999Z"] 1
        "invalid max no min" [nil "06:02:02.999Z"] 1
        "invalid min" ["04:02:03.001Z" "11:02:04Z"] 1
        "invalid min no max" ["04:02:03.001Z" nil] 1
        "invalid min & max" ["04:02:03.001Z" "06:02:02.999Z"] 2))))

(deftest collection-update-project-test
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :Projects (data-umm-cmn/projects "p1" "p2" "p3" "p4")}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                     {:EntryTitle "parent-collection2"
                                                      :Projects (data-umm-cmn/projects "p4")}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                     {:EntryTitle "parent-collection3"
                                                      :ShortName "S3"
                                                      :Projects (data-umm-cmn/projects "USGS_SOFIA")}))
        _ (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:project-refs ["p1"]}))
        _ (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:project-refs ["p2" "p3"]}))
        _ (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:project-refs ["p3"]}))
        _ (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 "C1-PROV1" {:project-refs ["p4"]}))
        _ (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll3 "C1-PROV1" {:project-refs ["USGS_SOFIA"]}))]

    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (are3
        [projects]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection"
                                                               :ShortName "S1"
                                                               :Version "V1"
                                                               :Projects (apply data-umm-cmn/projects projects)}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Adding an additional project is OK"
        ["p1" "p2" "p3" "p4" "p5"]

        "Removing a project not referenced by any granule in the collection is OK"
        ["p1" "p2" "p3"]))

    (testing "Update collection failure cases"
      (are3
        [projects expected-errors]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection"
                                                               :ShortName "S1"
                                                               :Version "V1"
                                                               :Projects (apply data-umm-cmn/projects projects)})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Removing a project that is referenced by a granule is invalid."
        ["p1" "p2" "p4"]
        ["Collection Project [p3] is referenced by existing granules, cannot be removed. Found 2 granules."]))

    (testing "Update collection where granule has old value the collection has humanized new value."
      (are3
        [projects]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection3"
                                                               :ShortName "S3"
                                                               :Projects (apply data-umm-cmn/projects projects)})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Changing a project that is referenced by a granule is valid because of the humanizer."
        ["USGS SOFIA"]))))


(deftest collection-update-granule-spatial-representation-test
  (let [make-coll (fn [entry-title spatial-params]
                    (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle entry-title
                                                                                  :ShortName (d/unique-str "short-name")
                                                                                  :SpatialExtent (when spatial-params
                                                                                                   (data-umm-c/spatial spatial-params))})))
        make-gran (fn [coll spatial]
                    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:spatial-coverage
                                                                                            (when spatial (dg/spatial spatial))})))

        ;; Geodetic test collections
        coll-geodetic-no-grans (make-coll "coll-geodetic-no-grans" {:gsr "GEODETIC"})
        coll-geodetic-with-grans (make-coll "coll-geodetic-with-grans" {:gsr "GEODETIC"})
        gran1 (make-gran coll-geodetic-with-grans (p/point 10 22))

        ;; Cartesian test collections
        coll-cartesian-no-grans (make-coll "coll-cartesian-no-grans" {:gsr "CARTESIAN"})
        coll-cartesian-with-grans (make-coll "coll-cartesian-with-grans" {:gsr "CARTESIAN"})
        gran2 (make-gran coll-cartesian-with-grans (p/point 10 22))
        ;; Orbit test collections
        orbit-params {:SwathWidth 1450
                      :SwathWidthUnit "Kilometer"
                      :OrbitPeriod 98.88
                      :OrbitPeriodUnit "Decimal Minute" 
                      :InclinationAngle 98.15
                      :InclinationAngleUnit "Degree" 
                      :NumberOfOrbits 0.5
                      :StartCircularLatitude -90
                      :StartCircularLatitudeUnit "Degree"}
        coll-orbit-no-grans (make-coll "coll-orbit-no-grans" {:gsr "ORBIT" :orbit orbit-params})
        coll-orbit-with-grans (make-coll "coll-orbit-with-grans" {:gsr "ORBIT" :orbit orbit-params})
        gran3 (make-gran coll-orbit-with-grans (dg/orbit -158.1 81.8 :desc  -81.8 :desc))
        ;; No Spatial test collections
        coll-no-spatial-no-grans  (make-coll "coll-no-spatial-no-grans" {:gsr "NO_SPATIAL"})
        coll-no-spatial-with-grans  (make-coll "coll-no-spatial-with-grans" {:gsr "NO_SPATIAL"})
        gran4 (make-gran coll-no-spatial-with-grans nil)
        update-collection (fn [coll new-spatial-params]
                            (let [updated-coll (dissoc coll :revision-id)
                                  updated-coll (assoc updated-coll
                                                      :SpatialExtent (when new-spatial-params
                                                                       (data-umm-c/spatial new-spatial-params)))]
                              (d/ingest-umm-spec-collection "PROV1" updated-coll {:allow-failure? true})))]

    (index/wait-until-indexed)
    (testing "Updates allowed with no granules"
      (are [coll new-spatial-params]
           (= 200 (:status (update-collection coll new-spatial-params)))
           coll-geodetic-no-grans {:gsr "CARTESIAN"}
           coll-cartesian-no-grans {:gsr "GEODETIC"}
           coll-orbit-no-grans {:gsr "GEODETIC"}
           coll-no-spatial-no-grans {:gsr "GEODETIC"}))

    (testing "Updates not permitted with granules"
      (are [coll new-spatial-params prev-gsr new-gsr]
           (= {:status 422
               :errors [(format (str "Collection changing from %s granule spatial representation to "
                                     "%s is not allowed when the collection has granules."
                                     " Found 1 granules.")
                                prev-gsr new-gsr)]}
              (update-collection coll new-spatial-params))

           coll-geodetic-with-grans {:gsr "CARTESIAN"} "GEODETIC" "CARTESIAN"
           coll-geodetic-with-grans {:gsr "NO_SPATIAL"} "GEODETIC" "NO_SPATIAL"
           coll-geodetic-with-grans {:gsr "ORBIT" :orbit orbit-params} "GEODETIC" "ORBIT"
           coll-cartesian-with-grans {:gsr "GEODETIC"} "CARTESIAN" "GEODETIC"
           coll-orbit-with-grans {:gsr "GEODETIC"} "ORBIT" "GEODETIC"
           coll-no-spatial-with-grans {:gsr "GEODETIC"} "NO_SPATIAL" "GEODETIC"))))

(deftest collection-update-unique-identifiers-test
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Dataset1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"
                                                                            :native-id "coll1"}))
        collNoGranule (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Dataset-No-Granule"
                                                                                    :ShortName "S2"
                                                                                    :Version "V2"
                                                                                    :native-id "coll2"}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 "C1-PROV1" {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 "C1-PROV1" {:granule-ur "Granule2"}))]
    (index/wait-until-indexed)

    (testing "Update unique identifiers of collection without granules is OK"
      (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "New Dataset-No-Granule"
                                                                                   :ShortName "S22"
                                                                                   :Version "V22"
                                                                                   :native-id "coll2"}))
            {:keys [status errors]} response]
        (= [200 nil] [status errors])))

    (testing "Update unique identifiers of a collection even with granules is allowed"
      ;; For CMR-2403 we decided to temporary allow collection identifiers to be updated even
      ;; with existing granules for the collection. We will change this with CMR-2485.
      (are3 [identifier-map]
            (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection (merge {:EntryTitle "Dataset1"
                                                                                                :ShortName "S1"
                                                                                                :Version "V1"
                                                                                                :native-id "coll1"}
                                                                                         identifier-map)))
                  {:keys [status errors]} response]
              (is (= [200 nil] [status errors])))

            "Update EntryTitle of collection with granules"
            {:EntryTitle "New Dataset1"}

            "Update ShortName of collection with granules"
            {:ShortName "S11"}

            "Update Version of collection with granules"
            {:Version "V11"}))))

(deftest collection-update-temporal-test
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Entry-Title "Dataset1"
                                                                            :ShortName "S1"
                                                                            :TemporalExtents [(data-umm-cmn/temporal-extent
                                                                                                {:beginning-date-time "2001-01-01T12:00:00Z"
                                                                                                 :ending-date-time "2010-05-11T12:00:00Z"})]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Dataset2"
                                                                            :ShortName "S2"
                                                                            :TemporalExtents [(data-umm-cmn/temporal-extent
                                                                                                {:beginning-date-time "2000-01-01T12:00:00Z"})]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Dataset3"
                                                                            :ShortName "S3"
                                                                            :TemporalExtents [(data-umm-cmn/temporal-extent
                                                                                                {:beginning-date-time "2000-01-01T12:00:00Z"})]}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Dataset4"
                                                                            :ShortName "S4"
                                                                            :TemporalExtents [(data-umm-cmn/temporal-extent
                                                                                                {:beginning-date-time "2001-01-01T12:00:00Z"
                                                                                                 :ending-date-time "2010-05-11T12:00:00Z"})]}))
        collNoGranule (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Dataset-No-Granule"
                                                                                    :ShortName "SNo"
                                                                                    :TemporalExtents [(data-umm-cmn/temporal-extent
                                                                                                        {:beginning-date-time "1999-01-02T12:00:00Z"
                                                                                                         :ending-date-time "1999-05-01T12:00:00Z"})]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 "C1-PROV1" {:granule-ur "Granule1"
                                                                                       :beginning-date-time "2010-01-01T12:00:00Z"
                                                                                       :ending-date-time "2010-01-11T12:00:00Z"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 "C1-PROV1" {:granule-ur "Granule2"
                                                                                       :beginning-date-time "2010-01-31T12:00:00Z"
                                                                                       :ending-date-time "2010-02-12T12:00:00Z"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 "C1-PROV1" {:granule-ur "Granule3"
                                                                                       :beginning-date-time "2010-02-03T12:00:00Z"
                                                                                       :ending-date-time "2010-03-20T12:00:00Z"}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll3 "C1-PROV1" {:granule-ur "Granule4"
                                                                                       :beginning-date-time "2010-03-12T12:00:00Z"}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 "C1-PROV1" {:granule-ur "Granule5"}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 "C1-PROV1" {:granule-ur "Granule6"
                                                                                       :beginning-date-time "2000-06-01T12:00:00Z"
                                                                                       :ending-date-time "2000-08-01T12:00:00Z"}))
        gran7 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll4 "C1-PROV1" {:granule-ur "Granule7"
                                                                                       :beginning-date-time "2001-01-01T12:00:00Z"
                                                                                       :ending-date-time "2010-05-11T12:00:00Z"}))
        update-collection (fn [coll new-temporal-params]
                            (let [new-coll (-> coll
                                               (assoc :revision-id nil)
                                               (assoc :TemporalExtents
                                                      (when (not= {:beginning-date-time nil :ending-date-time nil}
                                                                  new-temporal-params)
                                                        [(data-umm-cmn/temporal-extent new-temporal-params)])))]
                              (d/ingest-umm-spec-collection "PROV1" new-coll {:allow-failure? true})))]
    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (are3
        [coll beginning-date-time ending-date-time]
        (let [response (update-collection
                         coll
                          {:beginning-date-time beginning-date-time
                           :ending-date-time ending-date-time})
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Update dataset with the same temporal coverage"
        coll1 "2010-01-01T12:00:00Z" "2010-05-01T12:00:00Z"

        "Update dataset with no temporal coverage"
        coll1 nil nil

        "Update dataset with bigger temporal coverage"
        coll1 "2009-12-01T12:00:00Z" "2010-05-02T12:00:00Z"

        "Update dataset with bigger temporal coverage, no end date time"
        coll1 "2009-12-01T12:00:00Z" nil

        "Update dataset with smaller temporal coverage, but still contains all existing granules"
        coll1 "2010-01-01T12:00:00Z" "2010-04-01T12:00:00Z"

        "Update dataset (no end_date_time) to one with end_date_time that covers all existing granules"
        coll2 "2000-06-01T12:00:00Z" "2011-03-01T12:00:00Z"

        "Update dataset (with no granules) to one with bigger temporal coverage"
        collNoGranule "1999-01-01T00:00:00Z" "1999-09-01T12:00:00Z"

        "Update dataset (with no granules) to one with smaller temporal coverage"
        collNoGranule "1999-02-01T00:00:00Z" "1999-03-01T12:00:00Z"

        "Update dataset (with no granules) to one with no temporal coverage"
        collNoGranule nil nil

        "Update dataset with same temporal coverage and granule having same temporal coverage as collection"
        coll4 "2001-01-01T12:00:00Z" "2010-05-11T12:00:00Z"))

    (testing "Update collection failure cases"
      (are3
        [coll beginning-date-time ending-date-time expected-errors]
        (let [response (update-collection
                         coll
                          {:beginning-date-time beginning-date-time
                           :ending-date-time ending-date-time})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Update dataset with smaller temporal coverage and does not contain all existing granules, begin date time too late"
        coll1 "2010-01-02T12:00:00Z" "2010-04-01T12:00:00Z"
        ["Found granules earlier than collection start date [2010-01-02T12:00:00.000Z]. Found 1 granules."]

        "Update dataset with smaller temporal coverage and does not contain all existing granules, end date time too early"
        coll1 "2010-01-01T12:00:00Z" "2010-03-19T12:00:00Z"
        ["Found granules later than collection end date [2010-03-19T12:00:00.000Z]. Found 1 granules."]

        "Update dataset (no end_date_time) to one with begin_date_time that does not cover all existing granules"
        coll2 "2000-06-02T12:00:00Z" nil
        ["Found granules earlier than collection start date [2000-06-02T12:00:00.000Z]. Found 1 granules."]

        "Update dataset (no end_date_time) to one with end_date but having granules with no end_date"
        coll3 "2000-06-02T12:00:00Z" "2011-06-02T12:00:00Z"
        ["Found granules later than collection end date [2011-06-02T12:00:00.000Z]. Found 1 granules."]

        "Update dataset (no end_date_time) to one with end_date_time that does not cover all existing granules"
        coll2 "2000-05-01T12:00:00Z" "2000-07-01T12:00:00Z"
        ["Found granules later than collection end date [2000-07-01T12:00:00.000Z]. Found 1 granules."]))))

(deftest collection-update-platform-test
  (let [;; Platform Terra is the humanized alias of AM-1
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :Platforms (data-umm-cmn/platforms "p1" "p2" "AM-1" "p4")}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                     {:EntryTitle "parent-collection2"
                                                      :ShortName "S2"
                                                      :Version "V2"
                                                      :Platforms (data-umm-cmn/platforms "p4" "Terra")}))]
    ;; CMR-3926 We need to make sure granules with no platfrom ref do not inherit their parent collection's instrument
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1"))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:platform-refs (dg/platform-refs "p1")}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:platform-refs (dg/platform-refs "p2" "AM-1")}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:platform-refs (dg/platform-refs "AM-1")}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 "C1-PROV1" {:platform-refs (dg/platform-refs "p4")}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 "C1-PROV1" {:platform-refs (dg/platform-refs "Terra")}))
    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (are3
        [platforms]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection"
                                                               :ShortName "S1"
                                                               :Version "V1"
                                                               :Platforms (apply data-umm-cmn/platforms platforms)}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Adding an additional platform is OK"
        ["p1" "p2" "AM-1" "p4" "p5"]

        "Removing a platform not referenced by any granule in the collection is OK"
        ["p1" "p2" "AM-1"]

        "Updating a platform to humanized alias(case insensitively) referenced by granule on the original value is OK"
        ["p1" "p2" "tErra"]))

    (testing "Update collection failure cases"
      (are3
        [platforms expected-errors]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection2"
                                                               :ShortName "S2"
                                                               :Version "V2"
                                                               :Platforms (apply data-umm-cmn/platforms platforms)})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Removing a platform that is referenced by a granule is invalid."
        ["Terra"]
        ["Collection Platform [p4] is referenced by existing granules, cannot be removed. Found 1 granules."]

        "Updating a platform that is referenced by a granule by humanized alias back to its original value is invalid."
        ["AM-1" "p4"]
        ["Collection Platform [terra] is referenced by existing granules, cannot be removed. Found 1 granules."]))))

(deftest collection-update-tile-test
  (let [;; Tile case-insensitive "REPLACEMENT_TILE" is the humanized alias of "SOURCE_TILE"
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :TilingIdentificationSystems (data-umm-c/tiling-identification-systems "CALIPSO" "MISR" "WRS-1" "WRS-2")}))]
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:two-d-coordinate-system (dg/two-d "MISR")}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:two-d-coordinate-system (dg/two-d "MISR")}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:two-d-coordinate-system (dg/two-d "CALIPSO")}))
    (index/wait-until-indexed)
    (testing "Update collection successful cases"
      (are3
        [tile-names]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection"
                                                               :ShortName "S1"
                                                               :Version "V1"
                                                               :TilingIdentificationSystems (apply data-umm-c/tiling-identification-systems tile-names)}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Adding an additional new tile is OK"
        ["MISR" "CALIPSO" "WRS-1" "WRS-2" "WELD Alaska Tile"]

        "Removing a tile not referenced by any granule in the collection is OK"
        ["MISR" "CALIPSO" "WRS-1" "WRS-2"])

      "Updating SOURCE_TILE to Source_Tile_New is ok because the humanized alias Replacement_Tile is in the collection"
        ["Replacement_Tile" "Source_Tile_New" "Another_Tile" "New_Tile"]))

  (testing "Update collection failure cases"
    (are3
      [tile-names expected-errors]
      (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                            {:EntryTitle "parent-collection"
                                                             :ShortName "S2"
                                                             :Version "V2"
                                                             :TilingIdentificationSystems (apply data-umm-c/tiling-identification-systems tile-names)})
                               {:allow-failure? true})
            {:keys [status errors]} response]
        (is (= [422 expected-errors] [status errors])))

      "Removing a tile that is referenced by a granule is invalid."
      ["CALIPSO"]
      ["Collection TilingIdentificationSystemName [misr] is referenced by existing granules, cannot be removed. Found 2 granules."]

      "Updating a tile that is referenced by a granule by humanized alias back to its original value is invalid."
      ["MODIS Tile EASE" "WRS-2" "CALIPSO" "WELD Alaska Tile"]
      ["Collection TilingIdentificationSystemName [misr] is referenced by existing granules, cannot be removed. Found 2 granules."])))

(deftest collection-update-instrument-test
  (let [;; Instrument "GPS RECEIVERS" is the humanized alias of "GPS"
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                    {:EntryTitle "parent-collection"
                                                     :ShortName "S1"
                                                     :Version "V1"
                                                     :Platforms [(data-umm-cmn/platform-with-instruments "p1-1" "i1" "i2" "GPS" "i4")
                                                                 (data-umm-cmn/platform-with-instruments "p1-2" "i1" "i2" "GPS" "i4")]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                     {:EntryTitle "parent-collection2"
                                                      :ShortName "S2"
                                                      :Version "V2"
                                                      :Platforms [(data-umm-cmn/platform-with-instrument-and-childinstruments "p2" "i2" "s1" "GPS RECEIVERS")]}))]
    ;; CMR-3926 We need to make sure granules with no instrument ref or sensor ref do not inherit their parent collection's instrument or sensor
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1"))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:platform-refs [(dg/platform-ref-with-instrument-refs "p1-1" "i1")]}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:platform-refs [(dg/platform-ref-with-instrument-refs "p1-1" "i2" "GPS")]}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" {:platform-refs [(dg/platform-ref-with-instrument-refs "p1-1" "GPS")]}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 "C1-PROV1" {:platform-refs [(dg/platform-ref-with-instrument-ref-and-sensor-refs "p2" "i2" "s1")]}))
    (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 "C1-PROV1" {:platform-refs [(dg/platform-ref-with-instrument-ref-and-sensor-refs "p2" "i2" "GPS RECEIVERS")]}))
    (index/wait-until-indexed)
    (testing "Update collection successful cases"
      (are3
        [plat-instruments-1 plat-instruments-2]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection"
                                                                :ShortName "S1"
                                                                :Version "V1"
                                                                :Platforms [(apply data-umm-cmn/platform-with-instruments plat-instruments-1)
                                                                            (apply data-umm-cmn/platform-with-instruments plat-instruments-2)]}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Removing an instrument referenced by granules is invalid once hierarchical search is supported.
         Currently it's okay if it exists under other platforms."
        ["p1-1" "i2" "GPS" "i4"]
        ["p1-2" "i1" "i2" "GPS" "i4"]

        "Adding an additional instrument is OK"
        ["p1-1" "i1" "i2" "GPS" "i4" "i5"]
        ["p1-2" "i1" "i2" "GPS" "i4" "i5"]

        "Removing an instrument not referenced by any granule in the collection is OK"
        ["p1-1" "i1" "i2" "GPS"]
        ["p1-2" "i1" "i2" "GPS"]

        "Updating an instrument  to humanized alias(case insensitively) referenced by granule on the original value is OK"
        ["p1-1" "i1" "i2" "Gps Receivers"]
        ["p1-2" "i1" "i2" "Gps Receivers"]))

    (testing "Update collection failure cases"
      (are3
        [plat-instr-sensors expected-errors]
        (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                                              {:EntryTitle "parent-collection2"
                                                               :ShortName "S2"
                                                               :Version "V2"
                                                               :Platforms [(apply data-umm-cmn/platform-with-instrument-and-childinstruments plat-instr-sensors)]})
                                                     {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Removing an instrument  that is referenced by a granule is invalid."
        ["p2" "i2" "GPS RECEIVERS"]
        ["Collection Child Instrument [s1] is referenced by existing granules, cannot be removed. Found 1 granules."]

        "Updating an instrument  that is referenced by a granule by humanized alias back to its original value is invalid."
        ["p2" "i2" "GPS" "s1"]
        ["Collection Child Instrument [gps receivers] is referenced by existing granules, cannot be removed. Found 1 granules."]))))

(deftest collection-update-case-insensitivity-test
  (let [a1 (data-umm-cmn/additional-attribute {:Name "STRING" :DataType "STRING"})
        a2 (data-umm-cmn/additional-attribute {:Name "BOOLEAN" :DataType "BOOLEAN"})
        a3 (data-umm-cmn/additional-attribute {:Name "INT" :DataType "INT" :value 5})
        a4 (data-umm-cmn/additional-attribute {:Name "FLOAT" :DataType "FLOAT" :min-value 1.0 :max-value 10.0})
        a5 (data-umm-cmn/additional-attribute {:Name "DATETIME" :DataType "DATETIME"})
        a6 (data-umm-cmn/additional-attribute {:Name "DATE" :DataType "DATE"})
        a7 (data-umm-cmn/additional-attribute {:Name "TIME" :DataType "TIME"})
        a8 (data-umm-cmn/additional-attribute {:Name "DTS" :DataType "DATETIME_STRING"})
        collection-map {:EntryTitle "parent-collection"
                        :ShortName "S1"
                        :Version "V1"
                        :Platforms [(data-umm-cmn/platform-with-instrument-and-childinstruments "PLATFORM" "INSTRUMENT" "CHILDINSTRUMENT")]
                        :TilingIdentificationSystems (data-umm-c/tiling-identification-systems "MISR")
                        :Projects (data-umm-cmn/projects "PROJECT")
                        :AdditionalAttributes [a1 a2 a3 a4 a5 a6 a7 a8]}

        granule-map {:project-refs ["PROJECT"]
                     :two-d-coordinate-system (dg/two-d "MISR")
                     :platform-refs [(dg/platform-ref-with-instrument-ref-and-sensor-refs "PLATFORM" "INSTRUMENT" "CHILDINSTRUMENT")]
                     :product-specific-attributes [(dg/psa "STRING" ["alpha"])
                                                   (dg/psa "BOOLEAN" ["true"])
                                                   (dg/psa "INT" ["2"])
                                                   (dg/psa "FLOAT" ["2.0"])
                                                   (dg/psa "DATETIME" ["2012-01-01T01:02:03Z"])
                                                   (dg/psa "DATE" ["2012-01-02Z"])
                                                   (dg/psa "TIME" ["01:02:03Z"])
                                                   (dg/psa "DTS" ["2012-01-01T01:02:03Z"])]}

        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection collection-map))
        gran (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" granule-map))]

    (are3
      [coll-map gran-map]
      (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection coll-map))
            response2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll "C1-PROV1" gran-map))
            {:keys [status errors]} response]
        (is (= [200 nil] [status errors]))
        (is (= [201 nil] [(:status response2) (:errors response2)])))

      "Projects"
      (assoc collection-map :Projects (data-umm-cmn/projects "ProJecT"))
      (assoc granule-map :project-refs ["prOJecT"])

      "Tiling Identification Systems"
      (assoc collection-map :TilingIdentificationSystems (data-umm-c/tiling-identification-systems "MISR"))
      (assoc granule-map :two-d-coordinate-system (dg/two-d "MISR"))

      "Platforms Instruments Child Instruments"
      (assoc collection-map :Platforms [(data-umm-cmn/platform-with-instrument-and-childinstruments "plAtfoRM" "inStrUmEnt" "CHildinSTrument")])
      (assoc granule-map :platform-refs [(dg/platform-ref-with-instrument-ref-and-sensor-refs "platFORM" "instRUMENT" "childINSTRUMENT")])

      "Additional Attributes"
      (assoc collection-map :AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "strinG" :DataType "STRING"})
                                                   (data-umm-cmn/additional-attribute {:Name "booleaN" :DataType "BOOLEAN"})
                                                   (data-umm-cmn/additional-attribute {:Name "inT" :DataType "INT" :value 5})
                                                   (data-umm-cmn/additional-attribute {:Name "floaT" :DataType "FLOAT" :min-value 1.0 :max-value 10.0})
                                                   (data-umm-cmn/additional-attribute {:Name "datetimE" :DataType "DATETIME"})
                                                   (data-umm-cmn/additional-attribute {:Name "datE" :DataType "DATE"})
                                                   (data-umm-cmn/additional-attribute {:Name "timE" :DataType "TIME"})
                                                   (data-umm-cmn/additional-attribute {:Name "dtS" :DataType "DATETIME_STRING"})])
      (assoc granule-map :product-specific-attributes [(dg/psa "strING" ["alpha"])
                                                       (dg/psa "booLEAN" ["true"])
                                                       (dg/psa "inT" ["2"])
                                                       (dg/psa "flOAT" ["2.0"])
                                                       (dg/psa "dateTIME" ["2012-01-01T01:02:03Z"])
                                                       (dg/psa "daTE" ["2012-01-02Z"])
                                                       (dg/psa "tiME" ["01:02:03Z"])
                                                       (dg/psa "dTS" ["2012-01-01T01:02:03Z"])]))))

(deftest CMR-5871-collection-child-instrument-update-test
  (let [coll-metadata (-> "iso-samples/cmr-5871-coll.xml" io/resource slurp)
        updated-coll-metadata (-> "iso-samples/cmr-5871-coll-updated.xml" io/resource slurp)
        gran-metadata (-> "iso-samples/cmr-5871-gran.xml" io/resource slurp)]
    (ingest/ingest-concept
     (ingest/concept :collection "PROV1" "coll1" :iso19115 coll-metadata))
    (let [{:keys [status]} (ingest/ingest-concept
                            (ingest/concept
                             :granule "PROV1" "gran1" :iso-smap gran-metadata))]
      (is (= 201 status)))
    (index/wait-until-indexed)
    (testing "Update collection child instrument name that not referenced in its granule is OK"
      (let [{:keys [status errors]} (ingest/ingest-concept
                                     (ingest/concept
                                      :collection "PROV1" "coll1" :iso19115 updated-coll-metadata))]
        (is (= [200 nil] [status errors]))))))
