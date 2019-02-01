(ns cmr.system-int-test.search.collection-psa-search-test
  "Tests searching for granules by product specific attributes."
  (:require
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.search.services.messages.attribute-messages :as attribute-messages]
   [cmr.system-int-test.data2.collection :as collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; These are for boolean, datetime_string, time_string, and date_string attribute types which are
;; all indexed and searchable as strings.
(deftest indexed-as-string-psas-search-test
  (let [psa1 (collection/psa {:name "bool"
                              :data-type :boolean
                              :value true
                              :group "additionalattribute"})
        psa2 (collection/psa {:name "dts"
                              :data-type :datetime-string
                              :value "2012-01-01T01:02:03Z"
                              :group "additionalattribute"})
        psa3 (collection/psa {:name "ts"
                              :data-type :time-string
                              :value "01:02:03Z"
                              :group "additionalattribute"})
        psa4 (collection/psa {:name "ds"
                              :data-type :date-string
                              :value "2012-01-01"
                              :group "additionalattribute"})
        coll1 (data-core/ingest
               "PROV1"
               (collection/collection-dif {:product-specific-attributes [psa1 psa2 psa3]})
               {:format :dif})
        coll2 (data-core/ingest
               "PROV1"
               (collection/collection-dif {:product-specific-attributes [psa2 psa3]})
               {:format :dif})
        coll3 (data-core/ingest
               "PROV1"
               (collection/collection-dif {:product-specific-attributes [psa3 psa4]})
               {:format :dif})]
    (index/wait-until-indexed)
    (are [v items]
         (data-core/refs-match? items (search/find-refs :collection {"attribute[]" v}))
         "string,bool,true" [coll1]
         "string,bool,false" []
         "string,dts,2012-01-01T01:02:03Z" [coll1 coll2]
         "string,ts,01:02:03Z" [coll1 coll2 coll3]
         "string,ds,2012-01-01" [coll3])))

(deftest string-psas-search-test
  (let [psa1 (collection/psa {:name "alpha"
                              :group "G1.additionalattribute"
                              :data-type :string
                              :value "ab"})
        psa2 (collection/psa {:name "bravo"
                              :group "G1.additionalattribute"
                              :data-type :string
                              :value "bf"})
        psa3 (collection/psa {:name "charlie"
                              :group "G2.additionalattribute"
                              :data-type :string
                              :value "foo"})
        psa4 (collection/psa {:name "case"
                              :group "G3.additionalattribute"
                              :data-type :string
                              :value "up"})
        coll1 (data-core/ingest
               "PROV1"
               (collection/collection-dif {:product-specific-attributes [psa1 psa2]})
               {:format :dif})
        coll2 (data-core/ingest
               "PROV1"
               (collection/collection-dif {:product-specific-attributes [psa2 psa3]})
               {:format :dif})
        coll3 (data-core/ingest
               "PROV1"
               (collection/collection-dif {:product-specific-attributes [psa4]})
               {:format :dif})]
    (index/wait-until-indexed)
    (testing "search by value"
      (are [v items]
           (data-core/refs-match? items (search/find-refs :collection {"attribute[]" v}))
           "string,alpha,ab" [coll1]
           "string,alpha,AB" [coll1]
           "string,alpha,c" []
           "string,bravo,bf" [coll1 coll2]
           "string,case,UP" [coll3]
           "string,case,up" [coll3]))

    (testing "search by value catalog-rest style"
      (are [v items]
           (data-core/refs-match? items (search/find-refs :collection (search/csv->tuples v)))
           "string,alpha,ab" [coll1]
           "string,alpha,c" []
           "string,alpha,c" []
           "string,bravo,bf" [coll1 coll2]))

    (testing "search collections by additionalAttributes string value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

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
             (data-core/refs-match? items
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
           (data-core/refs-match?
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
           ["string,alpha,ab" "string,bravo,,bc"] [] nil))

    (testing "searching with multiple attribute conditions catalog-rest style"
      ;; the query is in the format of
      ;; attribute[0][name]=alpha&attribute[0][type]=string&attribute[0][value]=ab&attribute[1][name]=bravo&attribute[1][type]=string&attribute[1][maxValue]=bc
      (are [v items operation]
           (let [query (->> (map-indexed search/csv->tuples v)
                            (mapcat identity))
                 query (if operation
                         (merge query ["options[attribute][or]" (= operation :or)])
                         query)]
             (data-core/refs-match?
              items
              (search/find-refs
               :collection
               query
               {:snake-kebab? false})))

           ["string,alpha,ab" "string,bravo,,bc"] [coll1] :or
           ["string,alpha,ab" "string,bravo,,bc"] [] :and
           ["string,alpha,ab" "string,bravo,bc,"] [coll1] :and
           ; and is the default
           ["string,alpha,ab" "string,bravo,,bc"] [] nil))

    (testing "search collections by additionalAttributes multiple string values with aql"
      (are [items additional-attribs options]
           (let [condition (merge {:additionalAttributes additional-attribs} options)]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

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
           (data-core/refs-match? items (search/find-refs :collection {"attribute[]" v}))

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
           (data-core/refs-match?
            items
            (search/find-refs
             :collection
             (search/csv->tuples v)
             {:snake-kebab? false}))

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
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :range :name "alpha" :value ["aa" "ac"]}]
           [coll1] [{:type :range :name "'alpha'" :value ["'aa'" "'ac'"]}]
           [coll1] [{:type :range :name "alpha" :value ["ab" "ac"]}]
           [coll1] [{:type :range :name "alpha" :value ["aa" "ab"]}]
           [coll1 coll2] [{:type :range :name "bravo" :value ["bc" nil]}]
           [coll1 coll2] [{:type :range :name "bravo" :value [nil "bg"]}]))

    (testing "search collections by string attribute using JSON Query Language"
      (are [items search]
           (data-core/refs-match? items (search/find-refs-with-json-query :collection {} search))

           ;; Ranges
           [coll1] {:additional_attribute_range
                    {:type "string" :name "alpha" :min_value "aa" :max_value "ac"}}
           [coll1] {:additional_attribute_range
                    {:type "string" :name "alpha" :min_value "ab" :max_value "ac"}}
           [coll1] {:additional_attribute_range
                    {:type "string" :name "alpha" :min_value "aa" :max_value "ab"}}
           [coll1 coll2] {:additional_attribute_range
                          {:type "string" :name "bravo" :min_value "bc"}}
           [coll1 coll2] {:additional_attribute_range
                          {:type "string" :name "bravo" :max_value "bg"}}

           ;; Exact value
           [coll1] {:additional_attribute_value {:type "string" :name "alpha" :value "ab"}}

           ;; Test exclude boundary
           [coll1] {:additional_attribute_range {:type "string" :name "alpha" :min_value "aa"
                                                 :max_value "ab" :exclude_boundary false}}
           [] {:additional_attribute_range {:type "string" :name "alpha" :min_value "aa"
                                            :max_value "ab" :exclude_boundary true}}

           ;; Test searching by group
           [coll3] {:additional_attribute_name {:group "G3.additionalattribute"}}
           [coll3] {:additional_attribute_name {:group "*3.additionalattribute" :pattern true}}
           [coll1 coll2 coll3] {:additional_attribute_name {:group "G?.additionalattribute" :pattern true}}))))

(deftest float-psas-search-test
  (let [psa1 (collection/psa {:name "alpha"
                              :data-type :float
                              :value 10
                              :group "additionalattribute"})
        psa2 (collection/psa {:name "bravo"
                              :data-type :float
                              :value -12
                              :group "additionalattribute"})
        psa3 (collection/psa {:name "charlie"
                              :data-type :float
                              :value 45
                              :group "additionalattribute"})
        coll1 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa1 psa2]}))
        coll2 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa2 psa3]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v items]
           (data-core/refs-match? items (search/find-refs :collection {"attribute[]" v}))
           "float,alpha,10" [coll1]
           "float,alpha,11" []
           "float,bravo,-12" [coll1 coll2]
           "float,charlie,45" [coll2]))

    (testing "search by value legacy parameters"
      (are [v items]
           (data-core/refs-match? items (search/find-refs :collection (search/csv->tuples v)))
           "float,alpha,10" [coll1]
           "float,alpha,11" []
           "float,bravo,-12" [coll1 coll2]
           "float,charlie,45" [coll2]))

    (testing "search collections by additionalAttributes float value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :float :name "alpha" :value 10}]
           [] [{:type :float :name "alpha" :value 11}]
           [coll1 coll2] [{:type :float :name "bravo" :value -12}]
           [coll2] [{:type :float :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v items]
           (data-core/refs-match? items (search/find-refs :collection {"attribute[]" v}))

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
           (data-core/refs-match?
            items
            (search/find-refs
             :collection
             (search/csv->tuples v)
             {:snake-kebab? false}))

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
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :floatRange :name "alpha" :value [9.2 11]}]
           [coll1] [{:type :floatRange :name "alpha" :value [10.0 10.6]}]
           [coll1] [{:type :floatRange :name "alpha" :value [9.2 10.0]}]
           [coll1 coll2] [{:type :floatRange :name "bravo" :value [-120 nil]}]
           [coll1 coll2] [{:type :floatRange :name "bravo" :value [nil 13.6]}]
           [coll2] [{:type :floatRange :name "charlie" :value [44 45.1]}]))

    (testing "search collections by float attribute using JSON Query Language"
      (are [items search]
           (data-core/refs-match? items (search/find-refs-with-json-query :collection {} search))

           ;; Ranges
           [coll1] {:additional_attribute_range
                    {:type "float" :name "alpha" :min_value 9.2 :max_value 11}}
           [coll1] {:additional_attribute_range
                    {:type "float" :name "alpha" :min_value 10.0 :max_value 10.6}}
           [coll1] {:additional_attribute_range
                    {:type "float" :name "alpha" :min_value 9.2 :max_value 10.0}}
           [coll1 coll2] {:additional_attribute_range
                          {:type "float" :name "bravo" :min_value -120}}
           [coll1 coll2] {:additional_attribute_range
                          {:type "float" :name "bravo" :max_value 13.6}}
           [coll2] {:additional_attribute_range
                    {:type "float" :name "charlie" :min_value 44 :max_value 45.1}}

           ;; Exact value
           [coll1 coll2] {:additional_attribute_value {:type "float" :name "bravo" :value -12}}

           ;; Test exclude boundary
           [coll1 coll2] {:additional_attribute_range {:type "float" :name "bravo" :min_value -12
                                                       :max_value -5 :exclude_boundary false}}
           [] {:additional_attribute_range {:type "float" :name "bravo" :min_value -12
                                            :max_value -5 :exclude_boundary true}}))))

(deftest int-psas-search-test
  (let [psa1 (collection/psa {:name "alpha"
                              :data-type :int
                              :value 10
                              :group "additionalattribute"})
        psa2 (collection/psa {:name "bravo"
                              :data-type :int
                              :value -12
                              :group "additionalattribute"})
        psa3 (collection/psa {:name "charlie"
                              :data-type :int
                              :value 45
                              :group "additionalattribute"})
        coll1 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa1 psa2]}))
        coll2 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa2 psa3]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v items]
           (data-core/refs-match? items (search/find-refs :collection {"attribute[]" v}))
           "int,alpha,10" [coll1]
           "int,alpha,11" []
           "int,bravo,-12" [coll1 coll2]))

    (testing "search by value legacy parameters"
      (are [v items]
           (data-core/refs-match? items (search/find-refs :collection (search/csv->tuples v)))
           "int,alpha,10" [coll1]
           "int,alpha,11" []
           "int,bravo,-12" [coll1 coll2]))

    (testing "search collections by additionalAttributes int value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :int :name "alpha" :value 10}]
           [] [{:type :int :name "alpha" :value 11}]
           [coll1 coll2] [{:type :int :name "bravo" :value -12}]))

    (testing "search by range"
      (are [v items]
           (data-core/refs-match? items (search/find-refs :collection {"attribute[]" v}))

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
           (data-core/refs-match?
            items
            (search/find-refs
             :collection
             (search/csv->tuples v)
             {:snake-kebab? false}))

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
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :intRange :name "alpha" :value [9 11]}]
           [coll1] [{:type :intRange :name "alpha" :value [10 11]}]
           [coll1] [{:type :intRange :name "alpha" :value [9 10]}]
           [coll1 coll2] [{:type :intRange :name "bravo" :value [-120 nil]}]
           [coll1 coll2] [{:type :intRange :name "bravo" :value [nil 12]}]
           [coll2] [{:type :intRange :name "charlie" :value [44 46]}]))

    (testing "search collections by int attribute using JSON Query Language"
      (are [items search]
           (data-core/refs-match? items (search/find-refs-with-json-query :collection {} search))

           ;; Ranges
           [coll1] {:additional_attribute_range
                    {:type "int" :name "alpha" :min_value 9 :max_value 11}}
           [coll1] {:additional_attribute_range
                    {:type "int" :name "alpha" :min_value 10 :max_value 11}}
           [coll1] {:additional_attribute_range
                    {:type "int" :name "alpha" :min_value 9 :max_value 10}}
           [coll1 coll2] {:additional_attribute_range
                          {:type "int" :name "bravo" :min_value -120}}
           [coll1 coll2] {:additional_attribute_range
                          {:type "int" :name "bravo" :max_value 12}}
           [coll2] {:additional_attribute_range
                    {:type "int" :name "charlie" :min_value 44 :max_value 46}}

           ;; Exact value
           [coll1 coll2] {:additional_attribute_value {:type "int" :name "bravo" :value -12}}

           ;; Test exclude boundary
           [coll1 coll2] {:additional_attribute_range {:type "int" :name "bravo" :min_value -12
                                                       :max_value -5 :exclude_boundary false}}
           [] {:additional_attribute_range {:type "int" :name "bravo" :min_value -12
                                            :max_value -5 :exclude_boundary true}}))))

(deftest datetime-psas-search-test
  (let [psa1 (collection/psa {:name "alpha"
                              :data-type :datetime
                              :value (data-core/make-datetime 10 false)
                              :group "additionalattribute"})
        psa2 (collection/psa {:name "bravo"
                              :data-type :datetime
                              :value (data-core/make-datetime 14 false)
                              :group "additionalattribute"})
        psa3 (collection/psa {:name "charlie"
                              :data-type :datetime
                              :value (data-core/make-datetime 45 false)
                              :group "additionalattribute"})
        coll1 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa1 psa2]}))
        coll2 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa2 psa3]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (data-core/refs-match?
            items
            (search/find-refs
             :collection
             {"attribute[]" (str v (data-core/make-datetime n))}))
           "datetime,bravo," 14 [coll1, coll2]
           "datetime,alpha," 11 []
           "datetime,alpha," 10 [coll1]
           "datetime,charlie," 45 [coll2]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (data-core/refs-match?
            items
            (search/find-refs
             :collection
             (search/csv->tuples (str v (data-core/make-datetime n)))))
           "datetime,bravo," 14 [coll1, coll2]
           "datetime,alpha," 11 []
           "datetime,alpha," 10 [coll1]
           "datetime,charlie," 45 [coll2]))

    (testing "search collections by additionalAttributes datetime value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] data-core/make-datetime) additional-attribs)
                 condition {:additionalAttributes value}]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1, coll2] [{:type :date :name "bravo" :value 14}]
           [] [{:type :date :name "alpha" :value 11}]
           [coll1] [{:type :date :name "alpha" :value 10}]
           [coll2] [{:type :date :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (data-core/make-datetime min-n)
                 max-v (data-core/make-datetime max-n)
                 full-value (str v min-v "," max-v)]
             (data-core/refs-match? items (search/find-refs :collection {"attribute[]" full-value})))

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
           (let [min-v (data-core/make-datetime min-n)
                 max-v (data-core/make-datetime max-n)
                 full-value (str v min-v "," max-v)]
             (data-core/refs-match?
              items
              (search/find-refs
               :collection
               (search/csv->tuples full-value)
               {:snake-kebab? false})))

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
           (let [value-fn (fn [v] (map data-core/make-datetime v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :dateRange :name "alpha" :value [9 11]}]
           [coll1] [{:type :dateRange :name "alpha" :value [10 11]}]
           [coll1] [{:type :dateRange :name "alpha" :value [9 10]}]
           [coll1 coll2] [{:type :dateRange :name "bravo" :value [10 nil]}]
           [coll1 coll2] [{:type :dateRange :name "bravo" :value [nil 17]}]
           [coll2] [{:type :dateRange :name "charlie" :value [44 45]}]))

    (testing "search collections by datetime attribute using JSON Query Language"
      ;; Exact value
      (data-core/refs-match? [coll1 coll2]
                             (search/find-refs-with-json-query
                              :collection {}
                              {:additional_attribute_value
                               {:type "datetime"
                                :name "bravo"
                                :value (data-core/make-datetime 14)}}))
      (are [items search]
           (let [date-search (util/remove-nil-keys
                               (assoc search
                                      :min_value (data-core/make-datetime (:min_value search))
                                      :max_value (data-core/make-datetime (:max_value search))))
                 condition {:additional_attribute_range date-search}]
             (data-core/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "datetime" :name "alpha" :min_value 9 :max_value 11}
           [coll1] {:type "datetime" :name "alpha" :min_value 10 :max_value 11}
           [coll1] {:type "datetime" :name "alpha" :min_value 9 :max_value 10}
           [coll1 coll2] {:type "datetime" :name "bravo" :min_value 10}
           [coll1 coll2] {:type "datetime" :name "bravo" :max_value 17}
           [coll2] {:type "datetime" :name "charlie" :min_value 44 :max_value 45}

           ;; Test exclude boundary
           [coll1 coll2] {:type "datetime" :name "bravo" :min_value 0 :max_value 14
                          :exclude_boundary false}
           [coll1 coll2] {:type "datetime" :name "bravo" :min_value 0 :max_value 15
                          :exclude_boundary true}
           [] {:type "datetime" :name "bravo" :min_value 0 :max_value 14 :exclude_boundary true}))))

(deftest time-psas-search-test
  (let [psa1 (collection/psa {:name "alpha"
                              :data-type :time
                              :value (data-core/make-time 10 false)
                              :group "additionalattribute"})
        psa2 (collection/psa {:name "bravo"
                              :data-type :time
                              :value (data-core/make-time 23 false)
                              :group "additionalattribute"})
        psa3 (collection/psa {:name "charlie"
                              :data-type :time
                              :value (data-core/make-time 45 false)
                              :group "additionalattribute"})
        coll1 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa1 psa2]}))
        coll2 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa2 psa3]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (data-core/refs-match?
            items
            (search/find-refs
             :collection
             {"attribute[]" (str v (data-core/make-time n))}))
           "time,alpha," 10 [coll1]
           "time,alpha," 11 []
           "time,bravo," 23 [coll1 coll2]
           "time,charlie," 45 [coll2]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (data-core/refs-match?
            items
            (search/find-refs
             :collection
             (search/csv->tuples (str v (data-core/make-time n)))))
           "time,alpha," 10 [coll1]
           "time,alpha," 11 []
           "time,bravo," 23 [coll1 coll2]
           "time,charlie," 45 [coll2]))

    (testing "search collections by additionalAttributes time value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] data-core/make-time) additional-attribs)
                 condition {:additionalAttributes value}]
             (data-core/refs-match?
              items
              (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :time :name "alpha" :value 10}]
           [] [{:type :time :name "alpha" :value 11}]
           [coll1 coll2] [{:type :time :name "bravo" :value 23}]
           [coll2] [{:type :time :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (data-core/make-time min-n)
                 max-v (data-core/make-time max-n)
                 full-value (str v min-v "," max-v)]
             (data-core/refs-match?
              items
              (search/find-refs :collection {"attribute[]" full-value})))

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
           (let [min-v (data-core/make-time min-n)
                 max-v (data-core/make-time max-n)
                 full-value (str v min-v "," max-v)]
             (data-core/refs-match?
              items
              (search/find-refs
               :collection
               (search/csv->tuples full-value)
               {:snake-kebab? false})))

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
           (let [value-fn (fn [v] (map data-core/make-time v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :timeRange :name "alpha" :value [9 11]}]
           [coll1] [{:type :timeRange :name "alpha" :value [10 11]}]
           [coll1] [{:type :timeRange :name "alpha" :value [9 10]}]
           [coll1 coll2] [{:type :timeRange :name "bravo" :value [20 nil]}]
           [coll1 coll2] [{:type :timeRange :name "bravo" :value [nil 24]}]
           [coll2] [{:type :timeRange :name "charlie" :value [44 45]}]))

    (testing "search collections by time attribute using JSON Query Language"
      (data-core/refs-match? [coll1 coll2]
                             (search/find-refs-with-json-query
                              :collection {}
                              {:additional_attribute_value
                               {:type "time"
                                :name "bravo"
                                :value (data-core/make-time 23)}}))
      (are [items search]
           (let [date-search (util/remove-nil-keys
                               (assoc search
                                      :min_value (data-core/make-time (:min_value search))
                                      :max_value (data-core/make-time (:max_value search))))
                 condition {:additional_attribute_range date-search}]
             (data-core/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "time" :name "alpha" :min_value 9 :max_value 11}
           [coll1] {:type "time" :name "alpha" :min_value 10 :max_value 11}
           [coll1] {:type "time" :name "alpha" :min_value 9 :max_value 10}
           [coll1 coll2] {:type "time" :name "bravo" :min_value 20}
           [coll1 coll2] {:type "time" :name "bravo" :max_value 24}
           [coll2] {:type "time" :name "charlie" :min_value 44 :max_value 45}

           ;; Test exclude boundary
           [coll1 coll2] {:type "time" :name "bravo" :min_value 0 :max_value 23
                          :exclude_boundary false}
           [coll1 coll2] {:type "time" :name "bravo" :min_value 0 :max_value 24
                          :exclude_boundary true}
           [] {:type "time" :name "bravo" :min_value 0 :max_value 23 :exclude_boundary true}))))

(deftest date-psas-search-test
  (let [psa1 (collection/psa {:name "alpha"
                              :data-type :date
                              :value (data-core/make-date 10 false)
                              :group "additionalattribute"})
        psa2 (collection/psa {:name "bravo"
                              :data-type :date
                              :value (data-core/make-date 23 false)
                              :group "additionalattribute"})
        psa3 (collection/psa {:name "charlie"
                              :data-type :date
                              :value (data-core/make-date 45 false)
                              :group "additionalattribute"})
        coll1 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa1 psa2]}))
        coll2 (data-core/ingest
               "PROV1"
               (collection/collection
                {:product-specific-attributes [psa2 psa3]}))]

    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (data-core/refs-match?
            items
            (search/find-refs :collection {"attribute[]" (str v (data-core/make-date n))}))
           "date,bravo," 23 [coll1 coll2]
           "date,alpha," 11 []
           "date,alpha," 10 [coll1]
           "date,charlie," 45 [coll2]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (data-core/refs-match?
            items
            (search/find-refs :collection (search/csv->tuples (str v (data-core/make-date n)))))
           "date,bravo," 23 [coll1 coll2]
           "date,alpha," 11 []
           "date,alpha," 10 [coll1]
           "date,charlie," 45 [coll2]))

    (testing "search collections by additionalAttributes date value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] data-core/make-date) additional-attribs)
                 condition {:additionalAttributes value}]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1 coll2] [{:type :date :name "bravo" :value 23}]
           [coll1] [{:type :date :name "alpha" :value 10}]
           [] [{:type :date :name "alpha" :value 11}]
           [coll2] [{:type :date :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (data-core/make-date min-n)
                 max-v (data-core/make-date max-n)
                 full-value (str v min-v "," max-v)]
             (data-core/refs-match? items (search/find-refs :collection {"attribute[]" full-value})))

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
           (let [min-v (data-core/make-date min-n)
                 max-v (data-core/make-date max-n)
                 full-value (str v min-v "," max-v)]
             (data-core/refs-match?
              items
              (search/find-refs
               :collection
               (search/csv->tuples full-value) {:snake-kebab? false})))

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
           (let [value-fn (fn [v] (map data-core/make-date v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (data-core/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:type :dateRange :name "alpha" :value [9 11]}]
           [coll1] [{:type :dateRange :name "alpha" :value [10 11]}]
           [coll1] [{:type :dateRange :name "alpha" :value [9 10]}]
           [coll1 coll2] [{:type :dateRange :name "bravo" :value [20 nil]}]
           [coll1 coll2] [{:type :dateRange :name "bravo" :value [nil 24]}]
           [coll2] [{:type :dateRange :name "charlie" :value [44 45]}]))

    (testing "search collections by date attribute using JSON Query Language"
      (data-core/refs-match? [coll1 coll2]
                     (search/find-refs-with-json-query
                       :collection {}
                       {:additional_attribute_value
                        {:type "date" :name "bravo" :value (data-core/make-date 23)}}))
      (are [items search]
           (let [date-search (util/remove-nil-keys
                               (assoc search
                                      :min_value (data-core/make-date (:min_value search))
                                      :max_value (data-core/make-date (:max_value search))))
                 condition {:additional_attribute_range date-search}]
             (data-core/refs-match? items (search/find-refs-with-json-query :collection {} condition)))

           ;; Ranges
           [coll1] {:type "date" :name "alpha" :min_value 9 :max_value 11}
           [coll1] {:type "date" :name "alpha" :min_value 10 :max_value 11}
           [coll1] {:type "date" :name "alpha" :min_value 9 :max_value 10}
           [coll1 coll2] {:type "date" :name "bravo" :min_value 20}
           [coll1 coll2] {:type "date" :name "bravo" :max_value 24}
           [coll2] {:type "date" :name "charlie" :min_value 44 :max_value 45}

           ;; Test exclude boundary
           [coll1 coll2] {:type "date" :name "bravo" :min_value 0 :max_value 23
                          :exclude_boundary false}
           [coll1 coll2] {:type "date" :name "bravo" :min_value 0 :max_value 24
                          :exclude_boundary true}
           [] {:type "date" :name "bravo" :min_value 0 :max_value 23 :exclude_boundary true}))))

(deftest range-validation-test
  (testing "empty parameter range"
    (are [attrib-value]
         (= {:errors [(attribute-messages/one-of-min-max-msg)]
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
         (= {:errors [(attribute-messages/max-must-be-greater-than-min-msg min-n max-n)]
             :status 400}
            (search/get-search-failure-xml-data
              (search/find-refs :collection
                                {"attribute[]" (format "%s,%s,%s" v (str min-n) (str max-n))})))

         "string,alpha" "y" "m"
         "int,alpha" 10 6
         "float,alpha" 10.0 6.0))

  (testing "empty aql range"
    (are [attrib-value]
         (= {:errors [(attribute-messages/one-of-min-max-msg)]
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
         (= {:errors [(apply attribute-messages/max-must-be-greater-than-min-msg (:value attrib-value))]
             :status 400}
            (search/get-search-failure-xml-data
              (search/find-refs-with-aql
                :collection [{:additionalAttributes [attrib-value]}])))

         {:type :range :name "alpha" :value ["y" "m"]}
         {:type :intRange :name "alpha" :value [10 6]}
         {:type :floatRange :name "alpha" :value [10.0 6.0]})))

(deftest search-by-name-and-group-test
  (let [psa1 (collection/psa {:name "foo" :group "g1.additionalattribute" :data-type :boolean :value true})
        psa2 (collection/psa {:name "two"
                              :group "g2.additionalattribute"
                              :data-type :datetime-string
                              :value "2012-01-01T01:02:03Z"})
        coll1 (data-core/ingest
               "PROV1"
               (collection/collection-dif {:product-specific-attributes [psa1]})
               {:format :dif})
        coll2 (data-core/ingest
               "PROV1"
               (collection/collection-dif {:product-specific-attributes [psa2]})
               {:format :dif})]
    (index/wait-until-indexed)
    (are [matches a-name group pattern?]
         (data-core/refs-match? matches
                                (search/find-refs-with-json-query
                                 :collection
                                 {}
                                 {:additional_attribute_name
                                  (util/remove-nil-keys
                                    {:name a-name
                                     :group group
                                     :pattern pattern?})}))
         [coll1] "foo" nil false
         [coll1] "f?o" nil true
         [] "f?o" nil false
         [coll1] nil "g1.additionalattribute" false
         [coll1 coll2] nil "g*" true
         [] nil "g*" false
         [] "t*o" "*" false
         [coll2] "t*o" "*" true)))

(deftest json-query-validate-types-test
  (are [type value]
       (data-core/refs-match? [] (search/find-refs-with-json-query
                                  :collection
                                  {}
                                  {:additional_attribute_value
                                   {:name "a"
                                    :type type 
                                    :value value}}))
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
    (= {:status 400 :errors errors}
       (search/find-refs-with-json-query :collection {} search))

    "Invalid data type"
    {:additional_attribute_value {:type "bad" :value "c" :name "n"}}
    ["#/condition/additional_attribute_value/type: bad is not a valid enum value"]

    "Invalid int"
    {:additional_attribute_value {:type "int" :name "B" :value "1"}}
    ["[\"1\"] is an invalid value for type [int]"]

    "Invalid float"
    {:additional_attribute_value {:type "float" :name "B" :value "1.42"}}
    ["[\"1.42\"] is an invalid value for type [float]"]

    "Invalid datetime"
    {:additional_attribute_value {:type "datetime" :name "B" :value "2012-01-11 10:00:00"}}
    ["[\"2012-01-11 10:00:00\"] is an invalid value for type [datetime]"]

    "Invalid date"
    {:additional_attribute_value {:type "date" :name "B" :value "10:00:00Z"}}
    ["[\"10:00:00Z\"] is an invalid value for type [date]"]

    "Invalid time"
    {:additional_attribute_value {:type "time" :name "B" :value "2012-01-11"}}
    ["[\"2012-01-11\"] is an invalid value for type [time]"]

    "Invalid use of exclude-boundary"
    {:additional_attribute_value {:type "string" :name "a" :value "b" :exclude_boundary true}}
    ["#/condition/additional_attribute_value: extraneous key [exclude_boundary] is not permitted"]

    "Invalid use of pattern"
    {:additional_attribute_value {:type "string" :group "g" :name "a*" :value "b" :pattern true}}
    ["#/condition/additional_attribute_value: extraneous key [pattern] is not permitted"]

    "Invalid range max < min"
    {:additional_attribute_range {:type "int" :name "foo" :min_value 25 :max_value 14}}
    ["The maximum value [14] must be greater than the minimum value [25]"]

    "Cannot include both range and an exact value - value search"
    {:additional_attribute_value {:type "int" :name "foo" :min_value 25 :value 37}}
    ["#/condition/additional_attribute_value: extraneous key [min_value] is not permitted"]

    "Cannot include both range and an exact value - range search"
    {:additional_attribute_range {:type "int" :name "foo" :min_value 25 :value 37}}
    ["#/condition/additional_attribute_range: extraneous key [value] is not permitted"]

    "Name is required when doing an exact value search"
    {:additional_attribute_value {:type "int" :value 25 :group "abc"}}
    ["#/condition/additional_attribute_value: required key [name] not found"]

    "Name is required when doing a range search"
    {:additional_attribute_range {:type "int" :min_value 25 :max_value 35 :group "abc"}}
    ["#/condition/additional_attribute_range: required key [name] not found"]

    "Type is required when doing an exact value search"
    {:additional_attribute_value {:value 25 :name "a" :group "abc"}}
    ["#/condition/additional_attribute_value: required key [type] not found"]

    "Type is required when doing a range search"
    {:additional_attribute_range {:min_value 25 :max_value 35 :name "c" :group "abc"}}
    ["#/condition/additional_attribute_range: required key [type] not found"]

    "One of group or name is required"
    {:additional_attribute_name {:pattern true}}
    ["One of 'group' or 'name' must be provided."]

    "Multiple errors can be returned"
    {:additional_attribute_range {:type "float" :name "B" :min_value "1.42" :max_value "foo"}}
    ["[\"foo\"] is an invalid value for type [float]"
     "[\"1.42\"] is an invalid value for type [float]"]))

(deftest dif-extended-metadata-test
  (let [dif9-coll (data-core/ingest-concept-with-metadata-file
                    "example-data/dif/C1214305813-AU_AADC.xml"
                    {:provider-id "PROV1"
                     :concept-type :collection
                     :format-key :dif
                     :native-id "dif9-coll"})
        dif10-coll (data-core/ingest-concept-with-metadata-file
                     "example-data/dif10/sample_collection.xml"
                     {:provider-id "PROV1"
                      :concept-type :collection
                      :format-key :dif10
                      :native-id "dif10-coll"})]
    (index/wait-until-indexed)

    (testing "search for extended metadata fields"
      (util/are2
        [items search]
        (data-core/refs-match? items (search/find-refs-with-json-query :collection {} search))

        "By group"
        [dif9-coll dif10-coll] {:additional_attribute_name {:group "gov.nasa.gsfc.gcmd.additionalattribute"}}

        "By group - pattern"
        [dif9-coll dif10-coll] {:additional_attribute_name {:group "*additionalattribute" :pattern true}}

        "By name - metadata.extraction_date"
        [dif9-coll dif10-coll] {:additional_attribute_name {:name "metadata.extraction_date"}}

        "By name - metadata.keyword_version"
        [dif9-coll dif10-coll] {:additional_attribute_name {:name "metadata.keyword_version"}}

        "By name - pattern"
        [dif9-coll dif10-coll] {:additional_attribute_name {:name "meta??ta*" :pattern true}}

        "By value - string"
        [dif9-coll] {:additional_attribute_value
                     {:name "metadata.extraction_date" :type "string" :value "2015-11-29 18:23:23"}}

        ;; CMR-2413 - This is failing due to 8.100000381469727 being sent to elasticsearch.
        ; "By value - float"
        ; [dif9-coll] {:additional_attribute_name {:name "metadata.keyword_version" :value 8.1 :type "float"}}

        "By range - float"
        [dif9-coll dif10-coll] {:additional_attribute_range
                                {:name "metadata.keyword_version" :min_value 8.0 :max_value 8.5
                                 :type "float"}}))))
