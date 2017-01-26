(ns cmr.system-int-test.ingest.collection-update-test
  "CMR collection update integration tests"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.spatial.point :as p]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.umm.collection.product-specific-attribute :as psa]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       hu/grant-all-humanizers-fixture
                       hu/save-sample-humanizers-fixture]))

(deftest collection-update-additional-attributes-general-test
  (let [a1 (dc/psa {:name "string" :data-type :string})
        a2 (dc/psa {:name "boolean" :data-type :boolean})
        a3 (dc/psa {:name "int" :data-type :int :value 5})
        a4 (dc/psa {:name "float" :data-type :float :min-value 1.0 :max-value 10.0})
        a5 (dc/psa {:name "datetime" :data-type :datetime})
        a6 (dc/psa {:name "date" :data-type :date})
        a7 (dc/psa {:name "time" :data-type :time})
        a8 (dc/psa {:name "dts" :data-type :datetime-string})
        a9 (dc/psa {:name "moo" :data-type :string})

        coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :short-name "S1"
                                  :version-id "V1"
                                  :product-specific-attributes [a1 a2 a3 a4 a5 a6 a7 a8 a9]}))
        gran1 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes
                                                  [(dg/psa "string" ["alpha"])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes
                                                  [(dg/psa "boolean" ["true"])]}))
        gran3 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes
                                                  [(dg/psa "int" ["2"])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes
                                                  [(dg/psa "float" ["2.0"])]}))
        gran5 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes
                                                  [(dg/psa "datetime" ["2012-01-01T01:02:03Z"])]}))
        gran6 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes
                                                  [(dg/psa "date" ["2012-01-02Z"])]}))
        gran7 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes
                                                  [(dg/psa "time" ["01:02:03Z"])]}))
        gran8 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes
                                                  [(dg/psa "dts" ["2012-01-01T01:02:03Z"])]}))
        ;; The following collection and granule are added to verify that the validation is using
        ;; the collection concept id for searching granules. If we don't use the collection concept
        ;; id during granule search the test that changes additional attribute with name "int" to
        ;; a range of [1 10] would have failed.
        coll1 (d/ingest "PROV1" (dc/collection
                                  {:entry-title "parent-collection-1"
                                   :product-specific-attributes [a3]}))
        gran9 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes
                                                   [(dg/psa "int" ["20"])]}))]
    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (are3
        [additional-attributes]
        (let [response (d/ingest "PROV1" (dc/collection
                                           {:entry-title "parent-collection"
                                            :short-name "S1"
                                            :version-id "V1"
                                            :product-specific-attributes additional-attributes}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Not changing any additional attributes is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8 a9]

        "Add an additional attribute is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8 a9 (dc/psa {:name "alpha" :data-type :int})]

        "Removing an additional attribute that is not referenced by any granule is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8]

        "Changing the type of an additional attribute that is not referenced by any granule is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8 (dc/psa {:name "moo" :data-type :int})]

        "Changing the value of an additional attribute is OK."
        [a1 a2 (dc/psa {:name "int" :data-type :int :value 10}) a4 a5 a6 a7 a8 a9]

        "Change additional attribute value to a range is OK."
        [a1 a2 (dc/psa {:name "int" :data-type :int :min-value 1 :max-value 10}) a4 a5 a6 a7 a8 a9]

        "Removing the value/range of an additional attribute is OK."
        [a1 a2 (dc/psa {:name "int" :data-type :int}) a4 a5 a6 a7 a8 a9]

        "Change additional attribute range to a value is OK."
        [a1 a2 a3 (dc/psa {:name "float" :data-type :float :value 1.0}) a5 a6 a7 a8 a9]

        "Extending additional attribute range is OK."
        [a1 a2 a3 (dc/psa {:name "float" :data-type :float :min-value 0.0 :max-value 99.0})
         a5 a6 a7 a8 a9]))

    (testing "Update collection failure cases"
      (are3
        [additional-attributes expected-errors]
        (let [response (d/ingest "PROV1" (dc/collection
                                           {:entry-title "parent-collection"
                                            :short-name "S1"
                                            :version-id "V1"
                                            :product-specific-attributes additional-attributes})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Removing an additional attribute that is referenced by its granules is invalid."
        [a2 a3 a4 a5 a6 a7 a8 a9]
        ["Collection additional attribute [string] is referenced by existing granules, cannot be removed. Found 1 granules."]

        "Multiple validation errors."
        [(dc/psa {:name "float" :data-type :float :min-value 5.0 :max-value 10.0})]
        ["Collection additional attribute [string] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [boolean] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [int] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [datetime] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [date] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [time] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [dts] is referenced by existing granules, cannot be removed. Found 1 granules."
         "Collection additional attribute [float] cannot be changed since there are existing granules outside of the new value range. Found 1 granules."]

        "Changing an additional attribute type that is referenced by its granules is invalid."
        [(dc/psa {:name "string" :data-type :int}) a2 a3 a4 a5 a6 a7 a8 a9]
        ["Collection additional attribute [string] was of DataType [STRING], cannot be changed to [INT]. Found 1 granules."]))

    (testing "Delete the existing collection, then re-create it with any additional attributes is OK."
      (ingest/delete-concept (d/item->concept coll :echo10))
      (index/wait-until-indexed)
      (let [response (d/ingest "PROV1" (dc/collection
                                         {:entry-title "parent-collection"
                                          :short-name "S1"
                                          :version-id "V1"
                                          :product-specific-attributes [a9]}))
            {:keys [status errors]} response]
        (is (= [200 nil] [status errors]))))))

(deftest collection-update-additional-attributes-int-range-test
  (let [a1 (dc/psa {:name "int" :data-type :int :min-value 1 :max-value 10})
        coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :short-name "S1"
                                  :version-id "V1"
                                  :product-specific-attributes [a1]}))
        gran1 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "int" ["2"])]}))
        gran2 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "int" ["5"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes [(dc/psa {:name "int"
                                                                           :data-type :int
                                                                           :min-value (first range)
                                                                           :max-value (second range)})]}))
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
              response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes [(dc/psa {:name "int"
                                                                           :data-type :int
                                                                           :min-value (first range)
                                                                           :max-value (second range)})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" [0 4] 1
        "invalid max no min" [nil 4] 1
        "invalid min" [3 6] 1
        "invalid min no max" [3 nil] 1
        "invalid min & max" [3 4] 2))))

(deftest collection-update-additional-attributes-float-range-test
  (let [a1 (dc/psa {:name "float" :data-type :float :min-value -10.0 :max-value 10.0})
        coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :short-name "S1"
                                  :version-id "V1"
                                  :product-specific-attributes [a1]}))
        gran1 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "float" ["-2.0"])]}))
        gran2 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "float" ["5.0"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes [(dc/psa {:name "float"
                                                                           :data-type :float
                                                                           :min-value (first range)
                                                                           :max-value (second range)})]}))
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
              response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes [(dc/psa {:name "float"
                                                                           :data-type :float
                                                                           :min-value (first range)
                                                                           :max-value (second range)})]})
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
  (let [parse-fn (partial psa/parse-value :datetime)
        a1 (dc/psa {:name "datetime" :data-type :datetime
                    :min-value (parse-fn "2012-02-01T01:02:03Z")
                    :max-value (parse-fn "2012-11-01T01:02:03Z")})
        coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :short-name "S1"
                                  :version-id "V1"
                                  :product-specific-attributes [a1]}))
        gran1 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "datetime" ["2012-04-01T01:02:03Z"])]}))
        gran2 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "datetime" ["2012-08-01T01:02:03Z"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes
                                    [(dc/psa {:name "datetime" :data-type :datetime
                                              :min-value (parse-fn (first range))
                                              :max-value (parse-fn (second range))})]}))
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
              response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes
                                    [(dc/psa {:name "datetime" :data-type :datetime
                                              :min-value (parse-fn (first range))
                                              :max-value (parse-fn (second range))})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" ["2012-02-01T01:02:02Z" "2012-08-01T01:02:02.999Z"] 1
        "invalid max no min" [nil "2012-08-01T01:02:02.999Z"] 1
        "invalid min" ["2012-04-01T01:02:03.001Z" "2012-11-01T01:02:04Z"] 1
        "invalid min no max" ["2012-04-01T01:02:03.001Z" nil] 1
        "invalid min & max" ["2012-04-01T01:02:03.001Z" "2012-08-01T01:02:02.999Z"] 2))))

(deftest collection-update-additional-attributes-date-range-test
  (let [parse-fn (partial psa/parse-value :date)
        a1 (dc/psa {:name "date" :data-type :date
                    :min-value (parse-fn "2012-02-02Z")
                    :max-value (parse-fn "2012-11-02Z")})
        coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :short-name "S1"
                                  :version-id "V1"
                                  :product-specific-attributes [a1]}))
        gran1 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "date" ["2012-04-02Z"])]}))
        gran2 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "date" ["2012-08-02Z"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes
                                    [(dc/psa {:name "date" :data-type :date
                                              :min-value (parse-fn (first range))
                                              :max-value (parse-fn (second range))})]}))
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
              response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes
                                    [(dc/psa {:name "date" :data-type :date
                                              :min-value (parse-fn (first range))
                                              :max-value (parse-fn (second range))})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" ["2012-02-01Z" "2012-08-01Z"] 1
        "invalid max no min" [nil "2012-08-01Z"] 1
        "invalid min" ["2012-04-03Z" "2012-08-03Z"] 1
        "invalid min no max" ["2012-04-03Z" nil] 1
        "invalid min & max" ["2012-04-03Z" "2012-08-01Z"] 2))))

(deftest collection-update-additional-attributes-time-range-test
  (let [parse-fn (partial psa/parse-value :time)
        a1 (dc/psa {:name "time" :data-type :time :min-value (parse-fn "01:02:03Z")
                    :max-value (parse-fn "11:02:03Z")})
        coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :short-name "S1"
                                  :version-id "V1"
                                  :product-specific-attributes [a1]}))
        gran1 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "time" ["04:02:03Z"])]}))
        gran2 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "time" ["06:02:03Z"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are3
        [range]
        (let [response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes
                                    [(dc/psa {:name "time" :data-type :time
                                              :min-value (parse-fn (first range))
                                              :max-value (parse-fn (second range))})]}))
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
              response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :short-name "S1"
                                    :version-id "V1"
                                    :product-specific-attributes
                                    [(dc/psa {:name "time" :data-type :time
                                              :min-value (parse-fn (first range))
                                              :max-value (parse-fn (second range))})]})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 [expected-error]] [status errors])))

        "invalid max" ["01:02:04Z" "06:02:02.999Z"] 1
        "invalid max no min" [nil "06:02:02.999Z"] 1
        "invalid min" ["04:02:03.001Z" "11:02:04Z"] 1
        "invalid min no max" ["04:02:03.001Z" nil] 1
        "invalid min & max" ["04:02:03.001Z" "06:02:02.999Z"] 2))))

(deftest collection-update-project-test
  (let [coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :short-name "S1"
                                  :version-id "V1"
                                  :projects (dc/projects "p1" "p2" "p3" "p4")}))
        coll2 (d/ingest "PROV1" (dc/collection
                                  {:entry-title "parent-collection2"
                                   :projects (dc/projects "p4")}))
        _ (d/ingest "PROV1" (dg/granule coll {:project-refs ["p1"]}))
        _ (d/ingest "PROV1" (dg/granule coll {:project-refs ["p2" "p3"]}))
        _ (d/ingest "PROV1" (dg/granule coll {:project-refs ["p3"]}))
        _ (d/ingest "PROV1" (dg/granule coll2 {:project-refs ["p4"]}))]

    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (are3
        [projects]
        (let [response (d/ingest "PROV1" (dc/collection
                                           {:entry-title "parent-collection"
                                            :short-name "S1"
                                            :version-id "V1"
                                            :projects (apply dc/projects projects)}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Adding an additional project is OK"
        ["p1" "p2" "p3" "p4" "p5"]

        "Removing a project not referenced by any granule in the collection is OK"
        ["p1" "p2" "p3"]))

    (testing "Update collection failure cases"
      (are3
        [projects expected-errors]
        (let [response (d/ingest "PROV1" (dc/collection
                                           {:entry-title "parent-collection"
                                            :short-name "S1"
                                            :version-id "V1"
                                            :projects (apply dc/projects projects)})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Removing a project that is referenced by a granule is invalid."
        ["p1" "p2" "p4"]
        ["Collection Project [p3] is referenced by existing granules, cannot be removed. Found 2 granules."]))))

(deftest collection-update-granule-spatial-representation-test
  (let [make-coll (fn [entry-title spatial-params]
                    (d/ingest "PROV1" (dc/collection {:entry-title entry-title
                                                      :spatial-coverage (when spatial-params
                                                                          (dc/spatial spatial-params))})))
        make-gran (fn [coll spatial]
                    (d/ingest "PROV1" (dg/granule coll {:spatial-coverage
                                                        (when spatial (dg/spatial spatial))})))

        ;; Geodetic test collections
        coll-geodetic-no-grans (make-coll "coll-geodetic-no-grans" {:gsr :geodetic})
        coll-geodetic-with-grans (make-coll "coll-geodetic-with-grans" {:gsr :geodetic})
        gran1 (make-gran coll-geodetic-with-grans (p/point 10 22))

        ;; Cartesian test collections
        coll-cartesian-no-grans (make-coll "coll-cartesian-no-grans" {:gsr :cartesian})
        coll-cartesian-with-grans (make-coll "coll-cartesian-with-grans" {:gsr :cartesian})
        gran2 (make-gran coll-cartesian-with-grans (p/point 10 22))

        ;; Orbit test collections
        orbit-params {:swath-width 1450
                      :period 98.88
                      :inclination-angle 98.15
                      :number-of-orbits 0.5
                      :start-circular-latitude -90}
        coll-orbit-no-grans (make-coll "coll-orbit-no-grans" {:gsr :orbit :orbit orbit-params})
        coll-orbit-with-grans (make-coll "coll-orbit-with-grans" {:gsr :orbit :orbit orbit-params})
        gran3 (make-gran coll-orbit-with-grans (dg/orbit -158.1 81.8 :desc  -81.8 :desc))

        ;; No Spatial test collections
        coll-no-spatial-no-grans  (make-coll "coll-no-spatial-no-grans" nil)
        coll-no-spatial-with-grans  (make-coll "coll-no-spatial-with-grans" nil)
        gran4 (make-gran coll-no-spatial-with-grans nil)

        update-collection (fn [coll new-spatial-params]
                            (let [updated-coll (dissoc coll :revision-id)
                                  updated-coll (assoc updated-coll
                                                      :spatial-coverage (when new-spatial-params
                                                                          (dc/spatial new-spatial-params)))]
                              (d/ingest "PROV1" updated-coll {:allow-failure? true})))]

    (index/wait-until-indexed)
    (testing "Updates allowed with no granules"
      (are [coll new-spatial-params]
           (= 200 (:status (update-collection coll new-spatial-params)))
           coll-geodetic-no-grans {:gsr :cartesian}
           coll-cartesian-no-grans {:gsr :geodetic}
           coll-orbit-no-grans {:gsr :geodetic}
           coll-no-spatial-no-grans {:gsr :geodetic}))

    (testing "Updates not permitted with granules"
      (are [coll new-spatial-params prev-gsr new-gsr]
           (= {:status 422
               :errors [(format (str "Collection changing from %s granule spatial representation to "
                                     "%s is not allowed when the collection has granules."
                                     " Found 1 granules.")
                                prev-gsr new-gsr)]}
              (update-collection coll new-spatial-params))

           coll-geodetic-with-grans {:gsr :cartesian} "GEODETIC" "CARTESIAN"
           coll-geodetic-with-grans nil "GEODETIC" "NO_SPATIAL"
           coll-geodetic-with-grans {:gsr :orbit :orbit orbit-params} "GEODETIC" "ORBIT"
           coll-cartesian-with-grans {:gsr :geodetic} "CARTESIAN" "GEODETIC"
           coll-orbit-with-grans {:gsr :geodetic} "ORBIT" "GEODETIC"
           coll-no-spatial-with-grans {:gsr :geodetic} "NO_SPATIAL" "GEODETIC"))))

(deftest collection-update-unique-identifiers-test
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :short-name "S1"
                                                :version-id "V1"
                                                :native-id "coll1"}))
        collNoGranule (d/ingest "PROV1" (dc/collection {:entry-title "Dataset-No-Granule"
                                                        :short-name "S2"
                                                        :version-id "V2"
                                                        :native-id "coll2"}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))]
    (index/wait-until-indexed)

    (testing "Update unique identifiers of collection without granules is OK"
      (let [response (d/ingest "PROV1" (dc/collection {:entry-title "New Dataset-No-Granule"
                                                       :short-name "S22"
                                                       :version-id "V22"
                                                       :native-id "coll2"}))
            {:keys [status errors]} response]
        (= [200 nil] [status errors])))

    (testing "Update unique identifiers of a collection even with granules is allowed"
      ;; For CMR-2403 we decided to temporary allow collection identifiers to be updated even
      ;; with existing granules for the collection. We will change this with CMR-2485.
      (are3 [identifier-map]
            (let [response (d/ingest "PROV1" (dc/collection (merge {:entry-title "Dataset1"
                                                                    :short-name "S1"
                                                                    :version-id "V1"
                                                                    :native-id "coll1"}
                                                                  identifier-map)))
                  {:keys [status errors]} response]
              (is (= [200 nil] [status errors])))

            "Update entry-title of collection with granules"
            {:entry-title "New Dataset1"}

            "Update short-name of collection with granules"
            {:short-name "S11"}

            "Update version-id of collection with granules"
            {:version-id "V11"}))))

(deftest collection-update-temporal-test
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :beginning-date-time "2001-01-01T12:00:00Z"
                                                :ending-date-time "2010-05-11T12:00:00Z"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset2"
                                                :beginning-date-time "2000-01-01T12:00:00Z"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset3"
                                                :beginning-date-time "2000-01-01T12:00:00Z"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset4"
                                                :beginning-date-time "2001-01-01T12:00:00Z"
                                                :ending-date-time "2010-05-11T12:00:00Z"}))
        collNoGranule (d/ingest "PROV1" (dc/collection {:entry-title "Dataset-No-Granule"
                                                        :beginning-date-time "1999-01-02T12:00:00Z"
                                                        :ending-date-time "1999-05-01T12:00:00Z"}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                   :beginning-date-time "2010-01-01T12:00:00Z"
                                                   :ending-date-time "2010-01-11T12:00:00Z"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"
                                                   :beginning-date-time "2010-01-31T12:00:00Z"
                                                   :ending-date-time "2010-02-12T12:00:00Z"}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule3"
                                                   :beginning-date-time "2010-02-03T12:00:00Z"
                                                   :ending-date-time "2010-03-20T12:00:00Z"}))
        gran4 (d/ingest "PROV1" (dg/granule coll3 {:granule-ur "Granule4"
                                                   :beginning-date-time "2010-03-12T12:00:00Z"}))
        gran5 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule5"}))
        gran6 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule6"
                                                   :beginning-date-time "2000-06-01T12:00:00Z"
                                                   :ending-date-time "2000-08-01T12:00:00Z"}))
        gran7 (d/ingest "PROV1" (dg/granule coll4 {:granule-ur "Granule7"
                                                   :beginning-date-time "2001-01-01T12:00:00Z"
                                                   :ending-date-time "2010-05-11T12:00:00Z"}))
        update-collection (fn [coll new-temporal-params]
                            (let [new-coll (-> coll
                                               (assoc :revision-id nil)
                                               (assoc :temporal
                                                      (when new-temporal-params
                                                        (dc/temporal new-temporal-params))))]
                              (d/ingest "PROV1" new-coll {:allow-failure? true})))]
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
        coll (d/ingest "PROV1" (dc/collection
                                {:entry-title "parent-collection"
                                 :short-name "S1"
                                 :version-id "V1"
                                 :platforms (dc/platforms "p1" "p2" "AM-1" "p4")}))
        coll2 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection2"
                                  :short-name "S2"
                                  :version-id "V2"
                                  :platforms (dc/platforms "p4" "Terra")}))]
    (d/ingest "PROV1" (dg/granule coll {:platform-refs (dg/platform-refs "p1")}))
    (d/ingest "PROV1" (dg/granule coll {:platform-refs (dg/platform-refs "p2" "AM-1")}))
    (d/ingest "PROV1" (dg/granule coll {:platform-refs (dg/platform-refs "AM-1")}))
    (d/ingest "PROV1" (dg/granule coll2 {:platform-refs (dg/platform-refs "p4")}))
    (d/ingest "PROV1" (dg/granule coll2 {:platform-refs (dg/platform-refs "Terra")}))
    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (are3
        [platforms]
        (let [response (d/ingest "PROV1" (dc/collection
                                          {:entry-title "parent-collection"
                                           :short-name "S1"
                                           :version-id "V1"
                                           :platforms (apply dc/platforms platforms)}))
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
        (let [response (d/ingest "PROV1" (dc/collection
                                          {:entry-title "parent-collection2"
                                           :short-name "S2"
                                           :version-id "V2"
                                           :platforms (apply dc/platforms platforms)})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Removing a platform that is referenced by a granule is invalid."
        ["Terra"]
        ["Collection Platform [p4] is referenced by existing granules, cannot be removed. Found 1 granules."]

        "Updating a platform that is referenced by a granule by humanized alias back to its original value is invalid."
        ["AM-1" "p4"]
        ["Collection Platform [Terra] is referenced by existing granules, cannot be removed. Found 1 granules."]))))

(deftest collection-update-tile-test
  (let [;; Tile case-insensitive "REPLACEMENT_TILE" is the humanized alias of "SOURCE_TILE" 
        coll (d/ingest "PROV1" (dc/collection
                                {:entry-title "parent-collection"
                                 :short-name "S1"
                                 :version-id "V1"
                                 :two-d-coordinate-systems (dc/two-ds "Replacement_Tile" "SOURCE_TILE" "Another_Tile" "Foo")}))]
    (d/ingest "PROV1" (dg/granule coll {:two-d-coordinate-system (dg/two-d "Replacement_Tile")}))
    (d/ingest "PROV1" (dg/granule coll {:two-d-coordinate-system (dg/two-d "SOURCE_TILE")}))
    (d/ingest "PROV1" (dg/granule coll {:two-d-coordinate-system (dg/two-d "Another_Tile")}))
    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (are3
        [tile-names]
        (let [response (d/ingest "PROV1" (dc/collection
                                          {:entry-title "parent-collection"
                                           :short-name "S1"
                                           :version-id "V1"
                                           :two-d-coordinate-systems (apply dc/two-ds tile-names)}))
              {:keys [status errors]} response]
          (is (= [200 nil] [status errors])))

        "Adding an additional new tile is OK"
        ["Replacement_Tile" "SOURCE_TILE" "Another_Tile" "Foo" "New_Tile"]

        "Removing a tile not referenced by any granule in the collection is OK"
        ["Replacement_Tile" "SOURCE_TILE" "Another_Tile" "New_Tile"]

        "Updating SOURCE_TILE to Source_Tile_New is ok because the humanized alias Replacement_Tile is in the collection"
        ["Replacement_Tile" "Source_Tile_New" "Another_Tile" "New_Tile"]))

    (testing "Update collection failure cases"
      (are3
        [tile-names expected-errors]
        (let [response (d/ingest "PROV1" (dc/collection
                                          {:entry-title "parent-collection"
                                           :short-name "S2"
                                           :version-id "V2"
                                           :two-d-coordinate-systems (apply dc/two-ds tile-names)})
                                 {:allow-failure? true})
              {:keys [status errors]} response]
          (is (= [422 expected-errors] [status errors])))

        "Removing a tile that is referenced by a granule is invalid."
        ["Replacement_Tile"]
        ["Collection TilingIdentificationSystemName [Another_Tile] is referenced by existing granules, cannot be removed. Found 1 granules."]

        "Updating a tile that is referenced by a granule by humanized alias back to its original value is invalid."
        ["SOURCE_TILE" "Source_Tile_New" "Another_Tile" "New_Tile" ]
        ["Collection TilingIdentificationSystemName [Replacement_Tile] is referenced by existing granules, cannot be removed. Found 1 granules."]))))
