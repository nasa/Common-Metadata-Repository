(ns cmr.system-int-test.search.collection-psa-search-test
  "Tests searching for granules by product specific attributes."
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.search.services.messages.attribute-messages :as am]
            [cmr.common.util :as util]
            [clj-http.client :as client]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; These are for boolean, datetime_string, time_string, and date_string attribute types which are
;; all indexed and searchable as strings.
(deftest indexed-as-string-psas-search-test
  (let [psa1 (dc/psa {:name "bool" :data-type :boolean :value true})
        psa2 (dc/psa {:name "dts" :data-type :datetime-string :value "2012-01-01T01:02:03Z"})
        psa3 (dc/psa {:name "ts" :data-type :time-string :value "01:02:03Z"})
        psa4 (dc/psa {:name "ds" :data-type :date-string :value "2012-01-01"})
        coll1 (d/ingest "PROV1" (dc/collection-dif {:product-specific-attributes [psa1 psa2 psa3]})
                        {:format :dif})
        coll2 (d/ingest "PROV1" (dc/collection-dif {:product-specific-attributes [psa2 psa3]})
                        {:format :dif})
        coll3 (d/ingest "PROV1" (dc/collection-dif {:product-specific-attributes [psa3 psa4]})
                        {:format :dif})]
    (index/wait-until-indexed)
    (are [v items]
         (d/refs-match? items (search/find-refs :collection {"attribute[]" v}))
         "string,bool,true" [coll1]
         "string,bool,false" []
         "string,dts,2012-01-01T01:02:03Z" [coll1 coll2]
         "string,ts,01:02:03Z" [coll1 coll2 coll3]
         "string,ds,2012-01-01" [coll3])))

(deftest string-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :group "G1" :data-type :string :value "ab"})
        psa2 (dc/psa {:name "bravo" :group "G1" :data-type :string :value "bf"})
        psa3 (dc/psa {:name "charlie" :group "G2" :data-type :string :value "foo"})
        psa4 (dc/psa {:name "case" :group "G3" :data-type :string :value "up"})

        coll1 (d/ingest "PROV1" (dc/collection-dif {:product-specific-attributes [psa1 psa2]})
                        {:format :dif})
        coll2 (d/ingest "PROV1" (dc/collection-dif {:product-specific-attributes [psa2 psa3]})
                        {:format :dif})
        coll3 (d/ingest "PROV1" (dc/collection-dif {:product-specific-attributes [psa4]})
                        {:format :dif})]
    (index/wait-until-indexed)
    (testing "search by value"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection {"attribute[]" v}))
           "string,alpha,ab" [coll1]
           "string,alpha,AB" [coll1]
           "string,alpha,c" []
           "string,bravo,bf" [coll1 coll2]
           "string,case,UP" [coll3]
           "string,case,up" [coll3]))

    (testing "search by value catalog-rest style"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection (search/csv->tuples v)))
           "string,alpha,ab" [coll1]
           "string,alpha,c" []
           "string,alpha,c" []
           "string,bravo,bf" [coll1 coll2]))

    (testing "search collections by additionalAttributes string value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :string :name "alpha" :value "ab"}]
           [coll1] [{:type :string :name "'alpha'" :value "'ab'"}]
           [coll1] [{:type :string :name "alpha" :value "AB"}]
           [] [{:type :string :name "alpha" :value "c"}]
           [coll1 coll2] [{:type :string :name "bravo" :value "bf"}]
           [coll3] [{:type :string :name "case" :value "UP"}]
           [coll3] [{:type :string :name "case" :value "up"}]
           [coll1] [{:type :string :name "alpha" :value ["ab" "cd"]}]
           [coll1] [{:type :string :name "alpha" :value "a%" :pattern true}]
           [coll1] [{:type :string :name "alpha" :value "a_" :pattern true}]))

    (testing "search collections by additionalAttributeNames with AQL."
      (are [items attrib-names options]
           (let [condition (merge {:additionalAttributeNames attrib-names} options)]
             (d/refs-match? items
                            (search/find-refs-with-aql :collection [condition])))
           [coll1] "alpha" {}
           [coll1] "'alpha'" {}
           [coll1 coll2] "bravo" {}
           [coll2] "charlie" {}
           [coll3] "case" {}
           [] "BLAH" {}
           [coll1 coll3] ["alpha" "case"] {}
           [coll1 coll3] ["'alpha'" "'case'"] {}

           [coll1 coll2 coll3] ["alpha" "charlie" "case"] {}
           [coll2 coll3] "c%" {:pattern true}
           [coll3] "cas_" {:pattern true}))

    (testing "searching with multiple attribute conditions"
      (are [v items operation]
           (d/refs-match?
             items
             (search/find-refs
               :collection
               (merge
                 {"attribute[]" v}
                 (when operation
                   {"options[attribute][or]" (= operation :or)}))))

           ["string,alpha,ab" "string,bravo,,bc"] [coll1] :or
           ["string,alpha,ab" "string,bravo,,bc"] [] :and
           ["string,alpha,ab" "string,bravo,bc,"] [coll1] :and
           ; and is the default
           ["string,alpha,ab" "string,bravo,,bc"] [] nil ))

    (testing "searching with multiple attribute conditions catalog-rest style"
      (are [v items operation]
           (let [query (mapcat search/csv->tuples v)
                 query (if operation
                         (merge query ["options[attribute][or]" (= operation :or)])
                         query)]
             (d/refs-match?
               items
               (search/find-refs
                 :collection
                 query
                 {:snake-kebab? false})))

           ["string,alpha,ab" "string,bravo,,bc"] [coll1] :or
           ["string,alpha,ab" "string,bravo,,bc"] [] :and
           ["string,alpha,ab" "string,bravo,bc,"] [coll1] :and
           ; and is the default
           ["string,alpha,ab" "string,bravo,,bc"] [] nil ))

    (testing "search collections by additionalAttributes multiple string values with aql"
      (are [items additional-attribs options]
           (let [condition (merge {:additionalAttributes additional-attribs} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :string :name "alpha" :value "ab"}
                    {:type :range :name "bravo" :value [nil "bc"]}] {:or true}
           ;; single quotes
           [coll1] [{:type :string :name "'alpha'" :value "'ab'"}
                    {:type :range :name "'bravo'" :value [nil "'bc'"]}] {:or true}

           [] [{:type :string :name "alpha" :value "ab"}
               {:type :range :name "bravo" :value [nil "bc"]}] {:and true}
           [] [{:type :string :name "alpha" :value "ab"}
               {:type :range :name "bravo" :value [nil "bc"]}] {}
           [coll1] [{:type :string :name "alpha" :value "ab"}
                    {:type :range :name "bravo" :value ["bc" nil]}] {:and true}))

    (testing "search by range"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection {"attribute[]" v}))

           ;; inside range
           "string,alpha,aa,ac" [coll1]
           ;; beginning edge of range
           "string,alpha,ab,ac" [coll1]
           ;; ending edge of range
           "string,alpha,aa,ab" [coll1]

           ;; only min range provided
           "string,bravo,bc," [coll1 coll2]

           ;; only max range provided
           "string,bravo,,bg" [coll1 coll2]))

    (testing "search by range catalog-rest style"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection (search/csv->tuples v) {:snake-kebab? false}))

           ;; inside range
           "string,alpha,aa,ac"  [coll1]
           ;; beginning edge of range
           "string,alpha,ab,ac"[coll1]
           ;; ending edge of range
           "string,alpha,aa,ab" [coll1]

           ;; only min range provided
           "string,bravo,bc," [coll1 coll2]

           ;; only max range provided
           "string,bravo,,bg" [coll1 coll2]))

    (testing "search collections by additionalAttributes string range with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :range :name "alpha" :value ["aa" "ac"]}]
           [coll1] [{:type :range :name "'alpha'" :value ["'aa'" "'ac'"]}]
           [coll1] [{:type :range :name "alpha" :value ["ab" "ac"]}]
           [coll1] [{:type :range :name "alpha" :value ["aa" "ab"]}]
           [coll1 coll2] [{:type :range :name "bravo" :value ["bc" nil]}]
           [coll1 coll2] [{:type :range :name "bravo" :value [nil "bg"]}]))

    (testing "search collections by string attribute using JSON Query Language"
      (are [items search]
           (let [condition {:attribute search}]
             (d/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "string" :name "alpha" :min_value "aa" :max_value "ac"}
           [coll1] {:type "string" :name "alpha" :min_value "ab" :max_value "ac"}
           [coll1] {:type "string" :name "alpha" :min_value "aa" :max_value "ab"}
           [coll1 coll2] {:type "string" :name "bravo" :min_value "bc"}
           [coll1 coll2] {:type "string" :name "bravo" :max_value "bg"}

           ;; Exact value
           [coll1] {:type "string" :name "alpha" :value "ab"}

           ;; Searching by type without a value
           [coll1 coll2] {:type "string" :name "bravo"}

           ;; Test exclude boundary
           [coll1] {:type "string" :name "alpha" :min_value "aa" :max_value "ab"
                    :exclude_boundary false}
           [] {:type "string" :name "alpha" :min_value "aa" :max_value "ab"
               :exclude_boundary true}

           ;; Test searching by group
           [coll3] {:group "G3"}
           [coll3] {:group "*3" :pattern true}
           [coll1 coll2 coll3] {:group "G?" :pattern true}))))

(deftest float-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :float :value 10})
        psa2 (dc/psa {:name "bravo" :data-type :float :value -12})
        psa3 (dc/psa {:name "charlie" :data-type :float :value 45})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa2 psa3]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection {"attribute[]" v}))
           "float,alpha,10" [coll1]
           "float,alpha,11" []
           "float,bravo,-12" [coll1 coll2]
           "float,charlie,45" [coll2]))

    (testing "search by value legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection (search/csv->tuples v)))
           "float,alpha,10" [coll1]
           "float,alpha,11" []
           "float,bravo,-12" [coll1 coll2]
           "float,charlie,45" [coll2]))

    (testing "search collections by additionalAttributes float value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :float :name "alpha" :value 10}]
           [] [{:type :float :name "alpha" :value 11}]
           [coll1 coll2] [{:type :float :name "bravo" :value -12}]
           [coll2] [{:type :float :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection {"attribute[]" v}))

           ;; inside range
           "float,alpha,9.2,11" [coll1]
           ;; beginning edge of range
           "float,alpha,10.0,10.6" [coll1]
           ;; ending edge of range
           "float,alpha,9.2,10.0" [coll1]

           ;; only min range provided
           "float,bravo,-120," [coll1 coll2]

           ;; only max range provided
           "float,bravo,,13.6" [coll1 coll2]
           "float,charlie,44,45.1" [coll2]))

    (testing "search by range legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection (search/csv->tuples v) {:snake-kebab? false}))

           ;; inside range
           "float,alpha,9.2,11" [coll1]
           ;; beginning edge of range
           "float,alpha,10.0,10.6" [coll1]
           ;; ending edge of range
           "float,alpha,9.2,10.0" [coll1]

           ;; only min range provided
           "float,bravo,-120," [coll1 coll2]

           ;; only max range provided
           "float,bravo,,13.6" [coll1 coll2]
           "float,charlie,44,45.1" [coll2]))

    (testing "search collections by additionalAttributes float range with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :floatRange :name "alpha" :value [9.2 11]}]
           [coll1] [{:type :floatRange :name "alpha" :value [10.0 10.6]}]
           [coll1] [{:type :floatRange :name "alpha" :value [9.2 10.0]}]
           [coll1 coll2] [{:type :floatRange :name "bravo" :value [-120 nil]}]
           [coll1 coll2] [{:type :floatRange :name "bravo" :value [nil 13.6]}]
           [coll2] [{:type :floatRange :name "charlie" :value [44 45.1]}]))

    (testing "search collections by float attribute using JSON Query Language"
      (are [items search]
           (let [condition {:attribute search}]
             (d/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "float" :name "alpha" :min_value 9.2 :max_value 11}
           [coll1] {:type "float" :name "alpha" :min_value 10.0 :max_value 10.6}
           [coll1] {:type "float" :name "alpha" :min_value 9.2 :max_value 10.0}
           [coll1 coll2] {:type "float" :name "bravo" :min_value -120}
           [coll1 coll2] {:type "float" :name "bravo" :max_value 13.6}
           [coll2] {:type "float" :name "charlie" :min_value 44 :max_value 45.1}

           ;; Exact value
           [coll1 coll2] {:type "float" :name "bravo" :value -12}

           ;; Test exclude boundary
           [coll1 coll2] {:type "float" :name "bravo" :min_value -12 :max_value -5
                          :exclude_boundary false}
           [] {:type "float" :name "bravo" :min_value -12 :max_value -5 :exclude_boundary true}))))

(deftest int-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :int :value 10})
        psa2 (dc/psa {:name "bravo" :data-type :int :value -12})
        psa3 (dc/psa {:name "charlie" :data-type :int :value 45})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa2 psa3]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection {"attribute[]" v}))
           "int,alpha,10" [coll1]
           "int,alpha,11" []
           "int,bravo,-12" [coll1 coll2]))

    (testing "search by value legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection (search/csv->tuples v)))
           "int,alpha,10" [coll1]
           "int,alpha,11" []
           "int,bravo,-12" [coll1 coll2]))

    (testing "search collections by additionalAttributes int value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :int :name "alpha" :value 10}]
           [] [{:type :int :name "alpha" :value 11}]
           [coll1 coll2] [{:type :int :name "bravo" :value -12}]))

    (testing "search by range"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection {"attribute[]" v}))

           ;; inside range
           "int,alpha,9,11" [coll1]
           ;; beginning edge of range
           "int,alpha,10,11" [coll1]
           ;; ending edge of range
           "int,alpha,9,10" [coll1]

           ;; only min range provided
           "int,bravo,-120," [coll1 coll2]

           ;; only max range provided
           "int,bravo,,12" [coll1 coll2]
           "int,charlie,44,46" [coll2]))

    (testing "search by range legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :collection (search/csv->tuples v) {:snake-kebab? false}))

           ;; inside range
           "int,alpha,9,11" [coll1]
           ;; beginning edge of range
           "int,alpha,10,11" [coll1]
           ;; ending edge of range
           "int,alpha,9,10" [coll1]

           ;; only min range provided
           "int,bravo,-120," [coll1 coll2]

           ;; only max range provided
           "int,bravo,,12" [coll1 coll2]

           ;; range inheritance
           "int,charlie,44,46" [coll2]))

    (testing "search collections by additionalAttributes int range with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :intRange :name "alpha" :value [9 11]}]
           [coll1] [{:type :intRange :name "alpha" :value [10 11]}]
           [coll1] [{:type :intRange :name "alpha" :value [9 10]}]
           [coll1 coll2] [{:type :intRange :name "bravo" :value [-120 nil]}]
           [coll1 coll2] [{:type :intRange :name "bravo" :value [nil 12]}]
           [coll2] [{:type :intRange :name "charlie" :value [44 46]}]))

    (testing "search collections by int attribute using JSON Query Language"
      (are [items search]
           (let [condition {:attribute search}]
             (d/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "int" :name "alpha" :min_value 9 :max_value 11}
           [coll1] {:type "int" :name "alpha" :min_value 10 :max_value 11}
           [coll1] {:type "int" :name "alpha" :min_value 9 :max_value 10}
           [coll1 coll2] {:type "int" :name "bravo" :min_value -120}
           [coll1 coll2] {:type "int" :name "bravo" :max_value 12}
           [coll2] {:type "int" :name "charlie" :min_value 44 :max_value 46}

           ;; Exact value
           [coll1 coll2] {:type "int" :name "bravo" :value -12}

           ;; Test exclude boundary
           [coll1 coll2] {:type "int" :name "bravo" :min_value -12 :max_value -5
                          :exclude_boundary false}
           [] {:type "int" :name "bravo" :min_value -12 :max_value -5 :exclude_boundary true}))))

(deftest datetime-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :datetime :value (d/make-datetime 10 false)})
        psa2 (dc/psa {:name "bravo" :data-type :datetime :value (d/make-datetime 14 false)})
        psa3 (dc/psa {:name "charlie" :data-type :datetime :value (d/make-datetime 45 false)})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa2 psa3]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (d/refs-match?
             items (search/find-refs :collection {"attribute[]" (str v (d/make-datetime n))}))
           "datetime,bravo," 14 [coll1, coll2]
           "datetime,alpha," 11 []
           "datetime,alpha," 10 [coll1]
           "datetime,charlie," 45 [coll2]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (d/refs-match? items
                          (search/find-refs :collection (search/csv->tuples (str v (d/make-datetime n)))))
           "datetime,bravo," 14 [coll1, coll2]
           "datetime,alpha," 11 []
           "datetime,alpha," 10 [coll1]
           "datetime,charlie," 45 [coll2]))

    (testing "search collections by additionalAttributes datetime value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] d/make-datetime) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1, coll2] [{:type :date :name "bravo" :value 14}]
           [] [{:type :date :name "alpha" :value 11}]
           [coll1] [{:type :date :name "alpha" :value 10}]
           [coll2] [{:type :date :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (d/make-datetime min-n)
                 max-v (d/make-datetime max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :collection {"attribute[]" full-value})))

           ;; inside range
           "datetime,alpha," 9 11 [coll1]
           ;; beginning edge of range
           "datetime,alpha," 10 11 [coll1]
           ;; ending edge of range
           "datetime,alpha," 9 10 [coll1]

           ;; only min range provided
           "datetime,bravo," 10 nil [coll1 coll2]

           ;; only max range provided
           "datetime,bravo," nil 17 [coll1 coll2]

           "datetime,charlie," 44 45 [coll2]))

    (testing "search by range legacy parameters"
      (are [v min-n max-n items]
           (let [min-v (d/make-datetime min-n)
                 max-v (d/make-datetime max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :collection (search/csv->tuples full-value) {:snake-kebab? false})))

           ;; inside range
           "datetime,alpha," 9 11 [coll1]
           ;; beginning edge of range
           "datetime,alpha," 10 11 [coll1]
           ;; ending edge of range
           "datetime,alpha," 9 10 [coll1]

           ;; only min range provided
           "datetime,bravo," 10 nil [coll1 coll2]

           ;; only max range provided
           "datetime,bravo," nil 17 [coll1 coll2]

           "datetime,charlie," 44 45 [coll2]))

    (testing "search collections by additionalAttributes date range with aql"
      (are [items additional-attribs]
           (let [value-fn (fn [v] (map d/make-datetime v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :dateRange :name "alpha" :value [9 11]}]
           [coll1] [{:type :dateRange :name "alpha" :value [10 11]}]
           [coll1] [{:type :dateRange :name "alpha" :value [9 10]}]
           [coll1 coll2] [{:type :dateRange :name "bravo" :value [10 nil]}]
           [coll1 coll2] [{:type :dateRange :name "bravo" :value [nil 17]}]
           [coll2] [{:type :dateRange :name "charlie" :value [44 45]}]))

    (testing "search collections by datetime attribute using JSON Query Language"
      (are [items search]
           (let [date-search (util/remove-nil-keys
                               (assoc search
                                      :value (d/make-datetime (:value search))
                                      :min_value (d/make-datetime (:min_value search))
                                      :max_value (d/make-datetime (:max_value search))))
                 condition {:attribute date-search}]
             (d/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "datetime" :name "alpha" :min_value 9 :max_value 11}
           [coll1] {:type "datetime" :name "alpha" :min_value 10 :max_value 11}
           [coll1] {:type "datetime" :name "alpha" :min_value 9 :max_value 10}
           [coll1 coll2] {:type "datetime" :name "bravo" :min_value 10}
           [coll1 coll2] {:type "datetime" :name "bravo" :max_value 17}
           [coll2] {:type "datetime" :name "charlie" :min_value 44 :max_value 45}

           ;; Exact value
           [coll1 coll2] {:type "datetime" :name "bravo" :value 14}

           ;; Test exclude boundary
           [coll1 coll2] {:type "datetime" :name "bravo" :min_value 0 :max_value 14
                          :exclude_boundary false}
           [coll1 coll2] {:type "datetime" :name "bravo" :min_value 0 :max_value 15
                          :exclude_boundary true}
           [] {:type "datetime" :name "bravo" :min_value 0 :max_value 14 :exclude_boundary true}))))

(deftest time-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :time :value (d/make-time 10 false)})
        psa2 (dc/psa {:name "bravo" :data-type :time :value (d/make-time 23 false)})
        psa3 (dc/psa {:name "charlie" :data-type :time :value (d/make-time 45 false)})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa2 psa3]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (d/refs-match?
             items (search/find-refs :collection {"attribute[]" (str v (d/make-time n))}))
           "time,alpha," 10 [coll1]
           "time,alpha," 11 []
           "time,bravo," 23 [coll1 coll2]
           "time,charlie," 45 [coll2]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (d/refs-match? items
                          (search/find-refs :collection (search/csv->tuples (str v (d/make-time n)))))
           "time,alpha," 10 [coll1]
           "time,alpha," 11 []
           "time,bravo," 23 [coll1 coll2]
           "time,charlie," 45 [coll2]))

    (testing "search collections by additionalAttributes time value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] d/make-time) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :time :name "alpha" :value 10}]
           [] [{:type :time :name "alpha" :value 11}]
           [coll1 coll2] [{:type :time :name "bravo" :value 23}]
           [coll2] [{:type :time :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (d/make-time min-n)
                 max-v (d/make-time max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :collection {"attribute[]" full-value})))

           ;; inside range
           "time,alpha," 9 11 [coll1]
           ;; beginning edge of range
           "time,alpha," 10 11 [coll1]
           ;; ending edge of range
           "time,alpha," 9 10 [coll1]

           ;; only min range provided
           "time,bravo," 20 nil [coll1 coll2]

           ;; only max range provided
           "time,bravo," nil 24 [coll1 coll2]

           "time,charlie," 44 45 [coll2]))

    (testing "search by range legacy parameters"
      (are [v min-n max-n items]
           (let [min-v (d/make-time min-n)
                 max-v (d/make-time max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :collection (search/csv->tuples full-value) {:snake-kebab? false})))

           ;; inside range
           "time,alpha," 9 11 [coll1]
           ;; beginning edge of range
           "time,alpha," 10 11 [coll1]
           ;; ending edge of range
           "time,alpha," 9 10 [coll1]

           ;; only min range provided
           "time,bravo," 20 nil [coll1 coll2]

           ;; only max range provided
           "time,bravo," nil 24 [coll1 coll2]

           "time,charlie," 44 45 [coll2]))

    (testing "search collections by additionalAttributes time range with aql"
      (are [items additional-attribs]
           (let [value-fn (fn [v] (map d/make-time v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :timeRange :name "alpha" :value [9 11]}]
           [coll1] [{:type :timeRange :name "alpha" :value [10 11]}]
           [coll1] [{:type :timeRange :name "alpha" :value [9 10]}]
           [coll1 coll2] [{:type :timeRange :name "bravo" :value [20 nil]}]
           [coll1 coll2] [{:type :timeRange :name "bravo" :value [nil 24]}]
           [coll2] [{:type :timeRange :name "charlie" :value [44 45]}]))

    (testing "search collections by time attribute using JSON Query Language"
      (are [items search]
           (let [date-search (util/remove-nil-keys
                               (assoc search
                                      :value (d/make-time (:value search))
                                      :min_value (d/make-time (:min_value search))
                                      :max_value (d/make-time (:max_value search))))
                 condition {:attribute date-search}]
             (d/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "time" :name "alpha" :min_value 9 :max_value 11}
           [coll1] {:type "time" :name "alpha" :min_value 10 :max_value 11}
           [coll1] {:type "time" :name "alpha" :min_value 9 :max_value 10}
           [coll1 coll2] {:type "time" :name "bravo" :min_value 20}
           [coll1 coll2] {:type "time" :name "bravo" :max_value 24}
           [coll2] {:type "time" :name "charlie" :min_value 44 :max_value 45}

           ;; Exact value
           [coll1 coll2] {:type "time" :name "bravo" :value 23}

           ;; Test exclude boundary
           [coll1 coll2] {:type "time" :name "bravo" :min_value 0 :max_value 23
                          :exclude_boundary false}
           [coll1 coll2] {:type "time" :name "bravo" :min_value 0 :max_value 24
                          :exclude_boundary true}
           [] {:type "time" :name "bravo" :min_value 0 :max_value 23 :exclude_boundary true}))))

(deftest date-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :date :value (d/make-date 10 false)})
        psa2 (dc/psa {:name "bravo" :data-type :date :value (d/make-date 23 false)})
        psa3 (dc/psa {:name "charlie" :data-type :date :value (d/make-date 45 false)})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa2 psa3]}))]

    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (d/refs-match?
             items (search/find-refs :collection {"attribute[]" (str v (d/make-date n))}))
           "date,bravo," 23 [coll1 coll2]
           "date,alpha," 11 []
           "date,alpha," 10 [coll1]
           "date,charlie," 45 [coll2]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (d/refs-match? items
                          (search/find-refs :collection (search/csv->tuples (str v (d/make-date n)))))
           "date,bravo," 23 [coll1 coll2]
           "date,alpha," 11 []
           "date,alpha," 10 [coll1]
           "date,charlie," 45 [coll2]))

    (testing "search collections by additionalAttributes date value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] d/make-date) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1 coll2] [{:type :date :name "bravo" :value 23}]
           [coll1] [{:type :date :name "alpha" :value 10}]
           [] [{:type :date :name "alpha" :value 11}]
           [coll2] [{:type :date :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (d/make-date min-n)
                 max-v (d/make-date max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :collection {"attribute[]" full-value})))

           ;; inside range
           "date,alpha," 9 11 [coll1]
           ;; beginning edge of range
           "date,alpha," 10 11 [coll1]
           ;; ending edge of range
           "date,alpha," 9 10 [coll1]

           ;; only min range provided
           "date,bravo," 20 nil [coll1 coll2]

           ;; only max range provided
           "date,bravo," nil 24 [coll1 coll2]

           "date,charlie," 44 45 [coll2]))

    (testing "search by range legacy parameters"
      (are [v min-n max-n items]
           (let [min-v (d/make-date min-n)
                 max-v (d/make-date max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :collection (search/csv->tuples full-value) {:snake-kebab? false})))

           ;; inside range
           "date,alpha," 9 11 [coll1]
           ;; beginning edge of range
           "date,alpha," 10 11 [coll1]
           ;; ending edge of range
           "date,alpha," 9 10 [coll1]

           ;; only min range provided
           "date,bravo," 20 nil [coll1 coll2]

           ;; only max range provided
           "date,bravo," nil 24 [coll1 coll2]

           "date,charlie," 44 45 [coll2]))

    (testing "search collections by additionalAttributes date range with aql"
      (are [items additional-attribs]
           (let [value-fn (fn [v] (map d/make-date v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :dateRange :name "alpha" :value [9 11]}]
           [coll1] [{:type :dateRange :name "alpha" :value [10 11]}]
           [coll1] [{:type :dateRange :name "alpha" :value [9 10]}]
           [coll1 coll2] [{:type :dateRange :name "bravo" :value [20 nil]}]
           [coll1 coll2] [{:type :dateRange :name "bravo" :value [nil 24]}]
           [coll2] [{:type :dateRange :name "charlie" :value [44 45]}]))

    (testing "search collections by date attribute using JSON Query Language"
      (are [items search]
           (let [date-search (util/remove-nil-keys
                               (assoc search
                                      :value (d/make-date (:value search))
                                      :min_value (d/make-date (:min_value search))
                                      :max_value (d/make-date (:max_value search))))
                 condition {:attribute date-search}]
             (d/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "date" :name "alpha" :min_value 9 :max_value 11}
           [coll1] {:type "date" :name "alpha" :min_value 10 :max_value 11}
           [coll1] {:type "date" :name "alpha" :min_value 9 :max_value 10}
           [coll1 coll2] {:type "date" :name "bravo" :min_value 20}
           [coll1 coll2] {:type "date" :name "bravo" :max_value 24}
           [coll2] {:type "date" :name "charlie" :min_value 44 :max_value 45}

           ;; Exact value
           [coll1 coll2] {:type "date" :name "bravo" :value 23}

           ;; Test exclude boundary
           [coll1 coll2] {:type "date" :name "bravo" :min_value 0 :max_value 23
                          :exclude_boundary false}
           [coll1 coll2] {:type "date" :name "bravo" :min_value 0 :max_value 24
                          :exclude_boundary true}
           [] {:type "date" :name "bravo" :min_value 0 :max_value 23 :exclude_boundary true}))))

(deftest range-validation-test
  (testing "empty parameter range"
    (are [attrib-value]
         (= {:errors [(am/one-of-min-max-msg)]
             :status 400}
            (search/get-search-failure-xml-data
              (search/find-refs :collection {"attribute[]" attrib-value})))

         "string,alpha,,"
         "int,alpha,,"
         "float,alpha,,"
         "date,alpha,,"
         "time,alpha,,"
         "datetime,alpha,,"))

  (testing "invalid parameter range values"
    (are [v min-n max-n]
         (= {:errors [(am/max-must-be-greater-than-min-msg min-n max-n)]
             :status 400}
            (search/get-search-failure-xml-data
              (search/find-refs :collection
                                {"attribute[]" (format "%s,%s,%s" v (str min-n) (str max-n))})))

         "string,alpha" "y" "m"
         "int,alpha" 10 6
         "float,alpha" 10.0 6.0))

  (testing "empty aql range"
    (are [attrib-value]
         (= {:errors [(am/one-of-min-max-msg)]
             :status 400}
            (search/get-search-failure-xml-data
              (search/find-refs-with-aql
                :collection [{:additionalAttributes [attrib-value]}])))

         {:type :range :name "alpha" :value [nil nil]}
         {:type :intRange :name "alpha" :value [nil nil]}
         {:type :floatRange :name "alpha" :value [nil nil]}
         {:type :dateRange :name "alpha" :value [nil nil]}
         {:type :timeRange :name "alpha" :value [nil nil]}))

  (testing "invalid aql range values"
    (are [attrib-value]
         (= {:errors [(apply am/max-must-be-greater-than-min-msg (:value attrib-value))]
             :status 400}
            (search/get-search-failure-xml-data
              (search/find-refs-with-aql
                :collection [{:additionalAttributes [attrib-value]}])))

         {:type :range :name "alpha" :value ["y" "m"]}
         {:type :intRange :name "alpha" :value [10 6]}
         {:type :floatRange :name "alpha" :value [10.0 6.0]})))

(deftest search-by-name-and-group-test
  (let [psa1 (dc/psa {:name "foo" :group "g1" :data-type :boolean :value true})
        psa2 (dc/psa {:name "two" :group "g2" :data-type :datetime-string
                      :value "2012-01-01T01:02:03Z"})
        coll1 (d/ingest "PROV1" (dc/collection-dif {:product-specific-attributes [psa1]})
                        {:format :dif})
        coll2 (d/ingest "PROV1" (dc/collection-dif {:product-specific-attributes [psa2]})
                        {:format :dif})]
    (index/wait-until-indexed)
    (are [matches a-name group pattern?]
         (d/refs-match? matches (search/find-refs-with-json-query :collection {}
                                                             {:attribute
                                                              (util/remove-nil-keys
                                                                {:name a-name
                                                                 :group group
                                                                 :pattern pattern?})}))
         [coll1] "foo" nil false
         [coll1] "f?o" nil true
         [] "f?o" nil false
         [coll1] nil "g1" false
         [coll1 coll2] nil "g*" true
         [] nil "g*" false
         [] "t*o" "*" false
         [coll2] "t*o" "*" true)))

(deftest json-query-validate-types-test
  (are [type value]
       (d/refs-match? [] (search/find-refs-with-json-query
                           :collection {} {:attribute {:name "a" :type type :value value}}))
       "string" "abc"
       "string" 1.5
       "int" 12
       "float" 1.23
       "datetime" "2012-01-11T10:00:00.000Z"
       "date" "2012-01-11"
       "time" "10:00:00.000Z"))

(deftest json-query-validation-errors-test
  (util/are2
    [search errors]
    (let [condition {:attribute search}]
      ;; Use is since we are in a let within are2, otherwise diffs would not be printed correctly
      (is (= {:status 400 :errors errors}
             (search/find-refs-with-json-query :collection {} condition))))

    "Invalid data type"
    {:type "bad" :value "c" :name "n"}
    ["/condition/attribute instance failed to match exactly one schema (matched 0 out of 3)"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"value\"]"
     "/condition/attribute/type instance value (\"bad\") not found in enum (possible values: [\"string\",\"boolean\",\"time\",\"date\",\"datetime\",\"int\",\"float\"])"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"type\",\"value\"]"]

    "Invalid int"
    {:type "int" :name "B" :value "1"}
    ["[\"1\"] is an invalid value for type [int]"]

    "Invalid float"
    {:type "float" :name "B" :value "1.42"}
    ["[\"1.42\"] is an invalid value for type [float]"]

    "Invalid datetime"
    {:type "datetime" :name "B" :value "2012-01-11 10:00:00"}
    ["[\"2012-01-11 10:00:00\"] is an invalid value for type [datetime]"]

    "Invalid date"
    {:type "date" :name "B" :value "10:00:00Z"}
    ["[\"10:00:00Z\"] is an invalid value for type [date]"]

    "Invalid time"
    {:type "time" :name "B" :value "2012-01-11"}
    ["[\"2012-01-11\"] is an invalid value for type [time]"]

    "Invalid use of exclude-boundary"
    {:type "string" :name "a" :value "b" :exclude_boundary true}
    ["/condition/attribute instance failed to match exactly one schema (matched 0 out of 3)"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"value\"]"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"exclude_boundary\"]"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"exclude_boundary\",\"type\",\"value\"]"]

    "Invalid use of pattern"
    {:type "string" :group "g" :name "a*" :value "b" :pattern true}
    ["/condition/attribute instance failed to match exactly one schema (matched 0 out of 3)"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"pattern\",\"value\"]"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"pattern\"]"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"type\",\"value\"]"]

    "Invalid range max < min"
    {:type "int" :name "foo" :min_value 25 :max_value 14}
    ["The maximum value [14] must be greater than the minimum value [25]"]

    "Cannot include both range and an exact value"
    {:type "int" :name "foo" :min_value 25 :value 37}
    ["/condition/attribute instance failed to match exactly one schema (matched 0 out of 3)"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"value\"]"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"min_value\"]"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"min_value\",\"type\",\"value\"]"]

    "Name is required when doing an exact value search"
    {:type "int" :value 25 :group "abc"}
    ["/condition/attribute instance failed to match exactly one schema (matched 0 out of 3)"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"value\"]"
     "/condition/attribute object has missing required properties ([\"name\"])"
     "/condition/attribute object has missing required properties ([\"name\"])"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"type\",\"value\"]"]

    "Name is required when doing a range search"
    {:type "int" :min_value 25 :max_value 35 :group "abc"}
    ["/condition/attribute instance failed to match exactly one schema (matched 0 out of 3)"
     "/condition/attribute object has missing required properties ([\"name\"])"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"max_value\",\"min_value\"]"
     "/condition/attribute object has missing required properties ([\"name\",\"value\"])"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"max_value\",\"min_value\",\"type\"]"]

    "Type is required when doing an exact value search"
    {:value 25 :group "abc"}
    ["/condition/attribute instance failed to match exactly one schema (matched 0 out of 3)"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"value\"]"
     "/condition/attribute object has missing required properties ([\"name\",\"type\"])"
     "/condition/attribute object has missing required properties ([\"name\",\"type\"])"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"value\"]"]

    "Type is required when doing a range search"
    {:min_value 25 :max_value 35 :group "abc"}
    ["/condition/attribute instance failed to match exactly one schema (matched 0 out of 3)"
     "/condition/attribute object has missing required properties ([\"name\",\"type\"])"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"max_value\",\"min_value\"]"
     "/condition/attribute object has missing required properties ([\"name\",\"type\",\"value\"])"
     "/condition/attribute object instance has properties which are not allowed by the schema: [\"max_value\",\"min_value\"]"]

    "One of group or name is required"
    {:pattern true}
    ["One of 'group' or 'name' must be provided."]

    "Multiple errors can be returned"
    {:type "float" :name "B" :min_value "1.42" :max_value "foo"}
    ["[\"foo\"] is an invalid value for type [float]"
     "[\"1.42\"] is an invalid value for type [float]"]))

(deftest dif-extended-metadata-test
  (let [dif9-coll (d/ingest-concept-with-metadata-file
                    "data/dif/sample_collection_with_extended_metadata.xml"
                    {:provider-id "PROV1"
                     :concept-type :collection
                     :format-key :dif
                     :native-id "dif9-coll"})
        dif10-coll (d/ingest-concept-with-metadata-file
                    "data/dif10/sample_collection.xml"
                    {:provider-id "PROV1"
                     :concept-type :collection
                     :format-key :dif10
                     :native-id "dif10-coll"})]
    (index/wait-until-indexed)

    (testing "search for extended metadata fields"
      (util/are2
        [items search]
        (let [condition {:attribute search}]
          (d/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

        "By group"
        [dif9-coll dif10-coll] {:group "gov.nasa.gsfc.gcmd"}

        "By group - pattern"
        [dif9-coll dif10-coll] {:group "*gcmd" :pattern true}

        "By name - link_notification.contact"
        [dif9-coll] {:name "link_notification.contact"}

        "By name - metadata.extraction_date"
        [dif9-coll] {:name "metadata.extraction_date"}

        "By name - metadata.keyword_version"
        [dif9-coll dif10-coll] {:name "metadata.keyword_version"}

        "By name - pattern"
        [dif9-coll dif10-coll] {:name "meta??ta*" :pattern true}

         "By value - string"
        [dif9-coll] {:name "metadata.extraction_date" :type "string" :value "2015-05-21 15:58:46"}

        ;; TODO - Figure out why exact match is failing
        ; "By value - float"
        ; [dif9-coll] {:name "metadata.keyword_version" :value 8.1 :type "float"}

        "By range - float"
        [dif9-coll dif10-coll] {:name "metadata.keyword_version" :min_value 8.0 :type "float"}))))

