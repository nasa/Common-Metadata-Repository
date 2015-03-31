(ns cmr.system-int-test.ingest.collection-update-test
  "CMR collection update integration tests"
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest collection-update-additional-attributes-general-test
  (let [a1 (dc/psa "string" :string)
        a2 (dc/psa "boolean" :boolean)
        a3 (dc/psa "int" :int 5)
        a4 (dc/psa "float" :float 1.0 10.0)
        a5 (dc/psa "datetime" :datetime)
        a6 (dc/psa "date" :date)
        a7 (dc/psa "time" :time)
        a8 (dc/psa "dts" :datetime-string)
        a9 (dc/psa "moo" :string)

        coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :product-specific-attributes [a1 a2 a3 a4 a5 a6 a7 a8 a9]}))
        gran1 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "string" ["alpha"])]}))
        gran2 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "boolean" ["true"])]}))
        gran3 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "int" ["2"])]}))
        gran4 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "float" ["2.0"])]}))
        gran5 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "datetime" ["2012-01-01T01:02:03Z"])]}))
        gran6 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "date" ["2012-01-02Z"])]}))
        gran7 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "time" ["01:02:03Z"])]}))
        gran8 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "dts" ["2012-01-01T01:02:03Z"])]}))]
    (index/wait-until-indexed)

    (testing "Update collection successful cases"
      (util/are2
        [additional-attributes]
        (let [response (d/ingest "PROV1" (dc/collection
                                           {:entry-title "parent-collection"
                                            :product-specific-attributes additional-attributes}))
              {:keys [status errors]} response]
          (= [200 nil] [status errors]))

        "Not changing any additional attributes is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8 a9]

        "Removing an additional attribute that is not referenced by any granule is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8]

        "Changing the type of an additional attribute that is not referenced by any granule is OK."
        [a1 a2 a3 a4 a5 a6 a7 a8 (dc/psa "moo" :int)]

        "Changing the value of an additional attribute is OK."
        [a1 a2 (dc/psa "int" :int 10) a4 a5 a6 a7 a8 a9]

        "Chage additional attribute value to a range is OK."
        [a1 a2 (dc/psa "int" :int 1 10) a4 a5 a6 a7 a8 a9]

        "Removing the value/range of an additional attribute is OK."
        [a1 a2 (dc/psa "int" :int) a4 a5 a6 a7 a8 a9]

        "Chage additional attribute range to a value is OK."
        [a1 a2 a3 (dc/psa "float" :float 1.0) a5 a6 a7 a8 a9]))

    (testing "Update collection failure cases"
      (util/are2
        [additional-attributes expected-errors]
        (let [response (d/ingest "PROV1" (dc/collection
                                           {:entry-title "parent-collection"
                                            :product-specific-attributes additional-attributes}))
              {:keys [status errors]} response]
          (= [400 expected-errors] [status errors]))

        "Removing an additional attribute that is referenced by its granules is invalid."
        [a2 a3 a4 a5 a6 a7 a8 a9]
        ["Collection additional attribute [string] is referenced by existing granules, cannot be removed."]

        "Removing an additional attribute that is referenced by its granules is invalid.
        And the granule search is terminated on the first has granule search error."
        []
        ["Collection additional attribute [string] is referenced by existing granules, cannot be removed."]

        "Changing an additional attribute type that is referenced by its granules is invalid."
        [(dc/psa "string" :int) a2 a3 a4 a5 a6 a7 a8 a9]
        ["Collection additional attribute [string] was of DataType [STRING], cannot be changed to [INT]."]))

    (testing "Delete the existing collection, then re-create it with any additional attributes is OK."
      (ingest/delete-concept (d/item->concept coll :echo10))
      (index/wait-until-indexed)
      (let [response (d/ingest "PROV1" (dc/collection
                                         {:entry-title "parent-collection"
                                          :product-specific-attributes [a9]}))
            {:keys [status errors]} response]
        (is (= [200 nil] [status errors]))))))

(deftest collection-update-additional-attributes-range-test
  (testing "int range")
  (let [a1 (dc/psa "int" :int 1 10)
        coll (d/ingest "PROV1" (dc/collection
                                 {:entry-title "parent-collection"
                                  :product-specific-attributes [a1]}))
        gran1 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "int" ["2"])]}))
        gran2 (d/ingest "PROV1"(dg/granule coll {:product-specific-attributes
                                                 [(dg/psa "int" ["5"])]}))]
    (index/wait-until-indexed)

    (testing "successful cases"
      (are
        [range]
        (let [response (d/ingest "PROV1"
                                 (dc/collection
                                   {:entry-title "parent-collection"
                                    :product-specific-attributes [(apply (partial dc/psa "int" :int) range)]}))
              {:keys [status errors]} response]
          (= [200 nil] [status errors]))

        [0 11]
        [1 10]
        [nil 10]
        [1 nil]
        []
        [2 5]


        ))

    ))