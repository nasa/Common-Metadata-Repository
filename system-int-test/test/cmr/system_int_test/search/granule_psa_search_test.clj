(ns cmr.system-int-test.search.granule-psa-search-test
  "Tests searching for granules by product specific attributes."
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.search.services.messages.attribute-messages :as am]
            [clj-http.client :as client]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest invalid-psa-searches
  (are [v error]
       (= {:status 400 :errors [error]}
          (search/find-refs :granule {"attribute[]" v}))
       "" (am/invalid-name-msg "")
       "int,alpha" (am/invalid-num-parts-msg)

       ",alpha,a" (am/invalid-type-msg "")
       "foo,alpha,a" (am/invalid-type-msg "foo")
       ",alpha,a,b" (am/invalid-type-msg "")

       "string,,a" (am/invalid-name-msg "")
       "string,,a,b" (am/invalid-name-msg "")
       "string,alpha," (am/invalid-value-msg :string nil)
       "string,alpha" (am/invalid-num-parts-msg)
       "string,alpha,," (am/one-of-min-max-msg)
       "string,alpha,b,a" (am/max-must-be-greater-than-min-msg "b" "a")
       "string,alpha,b,b" (am/max-must-be-greater-than-min-msg "b" "b")

       "float,alpha,a" (am/invalid-value-msg :float "a")
       "float,alpha,a,0" (am/invalid-value-msg :float "a")
       "float,alpha,0,b" (am/invalid-value-msg :float "b")

       "int,alpha,a" (am/invalid-value-msg :int "a")
       "int,alpha,a,0" (am/invalid-value-msg :int "a")
       "int,alpha,0,b" (am/invalid-value-msg :int "b")

       "datetime,alpha,a" (am/invalid-value-msg :datetime "a")
       "datetime,alpha,a,2000-01-01T12:23:45" (am/invalid-value-msg :datetime "a")
       "datetime,alpha,2000-01-01T12:23:45,b" (am/invalid-value-msg :datetime "b")

       "time,alpha,a" (am/invalid-value-msg :time "a")
       "time,alpha,a,12:23:45" (am/invalid-value-msg :time "a")
       "time,alpha,12:23:45,b" (am/invalid-value-msg :time "b")

       "date,alpha,a" (am/invalid-value-msg :date "a")
       "date,alpha,a,2000-01-01" (am/invalid-value-msg :date "a")
       "date,alpha,2000-01-01,b" (am/invalid-value-msg :date "b"))

  (is (= {:status 400 :errors [(am/attributes-must-be-sequence-msg)]}
         (search/find-refs :granule {"attribute" "string,alpha,a"}))))

;; These are for boolean, datetime_string, time_string, and date_string attribute types which are all indexed and searchable as strings.
(deftest indexed-as-string-psas-search-test
  (let [psa1 (dc/psa {:name "bool" :data-type :boolean})
        psa2 (dc/psa {:name "dts" :data-type :datetime-string})
        psa3 (dc/psa {:name "ts" :data-type :time-string})
        psa4 (dc/psa {:name "ds" :data-type :date-string})
        coll (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3 psa4]}))
        gran1 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "bool" [true])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "bool" [false])]}))

        gran3 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "dts" ["2012-01-01T01:02:03Z"])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "dts" ["2012-01-02T01:02:03Z"])]}))

        gran5 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "ts" ["01:02:03Z"])]}))
        gran6 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "ts" ["01:02:04Z"])]}))

        gran7 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "ds" ["2012-01-01"])]}))
        gran8 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "ds" ["2012-01-02"])]}))]
    (index/wait-until-indexed)

    (testing "granule psa search by string value"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))
           "string,bool,true" [gran1]
           "string,bool,false" [gran2]

           "string,dts,2012-01-01T01:02:03Z" [gran3]
           "string,ts,01:02:03Z" [gran5]
           "string,ds,2012-01-01" [gran7]))

    (testing "search granules by additionalAttributes with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] [{:name "bool" :type :string :value "true"}]
           [gran2] [{:name "bool" :type :string :value "false"}]
           [gran3] [{:name "dts" :type :string :value "2012-01-01T01:02:03Z"}]
           [gran5] [{:name "ts" :type :string :value "01:02:03Z"}]
           [gran7] [{:name "ds" :type :string :value "2012-01-01"}]))))

(deftest string-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :string})
        psa2 (dc/psa {:name "bravo" :data-type :string})
        psa3 (dc/psa {:name "charlie" :data-type :string :value "foo"})
        psa4 (dc/psa {:name "case" :data-type :string})

        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" ["ab" "bc"])
                                                                                 (dg/psa "bravo" ["cd" "bf"])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "bravo" ["ab"])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "bravo" ["aa" "bf"])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "charlie" ["az"])]}))

        coll3 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa4]}))
        gran5 (d/ingest "PROV1" (dg/granule coll3 {:product-specific-attributes [(dg/psa "case" ["UP"])]}))]
    (index/wait-until-indexed)
    (testing "search by value"
      (are [items v options]
           (let [params (assoc options "attribute[]" v)]
             (d/refs-match? items (search/find-refs :granule params)))
           [gran1] "string,alpha,ab" nil
           [gran1] "string,alpha,AB" nil
           [] "string,alpha,c" nil
           [gran1 gran3] "string,bravo,bf" nil
           [gran5] "string,case,UP" nil
           [gran5] "string,case,up" nil

           ;; tests by value inheritance
           [gran3 gran4] "string,charlie,FoO" nil
           [gran3 gran4] "string,charlie,foo" nil
           [gran3 gran4] "string,charlie,foo" {"options[attribute][exclude-collection]" false}
           [] "string,charlie,foo" {"options[attribute][exclude-collection]" true}))

    (testing "search by value catalog-rest style"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (search/csv->tuples v)))
           "string,alpha,ab" [gran1]
           "string,alpha,c" []
           "string,alpha,c" []
           "string,bravo,bf" [gran1 gran3]

           ;; tests by value inheritance
           "string,charlie,foo" [gran3 gran4]))

    (testing "search granules by additionalAttributes string value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] [{:type :string :name "alpha" :value "ab"}]
           [gran1] [{:type :string :name "alpha" :value "AB"}]
           [] [{:type :string :name "alpha" :value "c"}]
           [gran1 gran3] [{:type :string :name "bravo" :value "bf"}]
           [gran5] [{:type :string :name "case" :value "UP"}]
           [gran5] [{:type :string :name "case" :value "up"}]

           ;; tests by value inheritance
           [gran3 gran4] [{:type :string :name "charlie" :value "FoO"}]
           [gran3 gran4] [{:type :string :name "charlie" :value "Foo"}]

           ;; tests list
           [gran1] [{:type :string :name "alpha" :value ["ab" "bc"]}]
           [gran1 gran3] [{:type :string :name "bravo" :value ["aa" "bf"]}]

           ;; tests pattern
           [gran2 gran3] [{:type :string :name "bravo" :value "a%" :pattern true}]
           [gran2 gran3] [{:type :string :name "bravo" :value "a_" :pattern true}]))

    (testing "search by range"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))

           ;; inside range
           "string,alpha,aa,ac" [gran1]
           ;; beginning edge of range
           "string,alpha,ab,ac" [gran1]
           ;; ending edge of range
           "string,alpha,aa,ab" [gran1]

           ;; only min range provided
           "string,bravo,bc," [gran1 gran3]

           ;; only max range provided
           "string,bravo,,bc" [gran1 gran2 gran3 gran4]

           ;; Range value inheritance
           "string,charlie,foa,foz" [gran3 gran4]))

    (testing "search by range catalog-rest style"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (search/csv->tuples v) {:snake-kebab? false}))

           ;; inside range
           "string,alpha,aa,ac"  [gran1]
           ;; beginning edge of range
           "string,alpha,ab,ac"[gran1]
           ;; ending edge of range
           "string,alpha,aa,ab" [gran1]

           ;; only min range provided
           "string,bravo,bc," [gran1 gran3]

           ;; only max range provided
           "string,bravo,,bc" [gran1 gran2 gran3 gran4]

           ;; Range value inheritance
           "string,charlie,foa,foz" [gran3 gran4]))

    (testing "search granules by additionalAttributes string range with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] [{:type :range :name "alpha" :value ["aa" "ac"]}]
           [gran1] [{:type :range :name "alpha" :value ["ab" "ac"]}]
           [gran1] [{:type :range :name "alpha" :value ["aa" "ab"]}]
           [gran1 gran3] [{:type :range :name "bravo" :value ["bc" nil]}]
           [gran1 gran2 gran3 gran4] [{:type :range :name "bravo" :value [nil "bc"]}]
           [gran3 gran4] [{:type :range :name "charlie" :value ["foa" "foz"]}]))

    (testing "searching with multiple attribute conditions"
      (are [v items operation]
           (d/refs-match?
             items
             (search/find-refs
               :granule
               (merge
                 {"attribute[]" v}
                 (when operation
                   {"options[attribute][or]" (= operation :or)}))))

           ["string,alpha,ab" "string,bravo,,bc"] [gran1 gran2 gran3 gran4] :or
           ["string,alpha,ab" "string,bravo,,bc"] [] :and
           ["string,alpha,ab" "string,bravo,bc,"] [gran1] :and
           ; and is the default
           ["string,alpha,ab" "string,bravo,,bc"] [] nil ))
    (testing "searching with multiple attribute conditions catalog-rest style"
      ;; the query is in the format of
      ;; attribute[0][name]=alpha&attribute[0][type]=string&attribute[0][value]=ab&attribute[1][name]=bravo&attribute[1][type]=string&attribute[1][maxValue]=bc
      (are [v items operation]
           (let [query (->> (map-indexed search/csv->tuples v)
                            (mapcat identity))
                 query (if operation
                         (merge query ["options[attribute][or]" (= operation :or)])
                         query)]
             (d/refs-match?
               items
               (search/find-refs
                 :granule
                 query
                 {:snake-kebab? false})))

           ["string,alpha,ab" "string,bravo,,bc"] [gran1 gran2 gran3 gran4] :or
           ["string,alpha,ab" "string,bravo,,bc"] [] :and
           ["string,alpha,ab" "string,bravo,bc,"] [gran1] :and
           ; and is the default
           ["string,alpha,ab" "string,bravo,,bc"] [] nil ))

    (testing "search granules by additionalAttributes multiple string values with aql"
      (are [items additional-attribs options]
           (let [condition (assoc options :additionalAttributes additional-attribs)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1 gran2 gran3 gran4] [{:type :string :name "alpha" :value "ab"}
                                      {:type :range :name "bravo" :value [nil "bc"]}] {:or true}
           [] [{:type :string :name "alpha" :value "ab"}
               {:type :range :name "bravo" :value [nil "bc"]}] {:and true}
           [] [{:type :string :name "alpha" :value "ab"}
               {:type :range :name "bravo" :value [nil "bc"]}] {}
           [gran1] [{:type :string :name "alpha" :value "ab"}
                    {:type :range :name "bravo" :value ["bc" nil]}] {:and true}))))

(deftest float-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :float})
        psa2 (dc/psa {:name "bravo" :data-type :float})
        psa3 (dc/psa {:name "charlie" :data-type :float :value 45})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" [10.5 123])
                                                                                 (dg/psa "bravo" [-12])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "bravo" [10.5 123])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "alpha" [14])
                                                                                 (dg/psa "bravo" [13.7 123])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "charlie" [14])]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))
           "float,bravo,123" [gran2, gran3]
           "float,alpha,10" []
           "float,bravo,-12" [gran1]
           "float,charlie,45" [gran3 gran4]))

    (testing "search by value legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (search/csv->tuples v)))
           "float,bravo,123" [gran2, gran3]
           "float,alpha,10" []
           "float,bravo,-12" [gran1]
           "float,charlie,45" [gran3 gran4]))

    (testing "search granules by additionalAttributes float value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran2 gran3] [{:type :float :name "bravo" :value 123}]
           [] [{:type :float :name "alpha" :value 10}]
           [gran1] [{:type :float :name "bravo" :value -12}]
           [gran3 gran4] [{:type :float :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))

           ;; inside range
           "float,alpha,10.2,11" [gran1]
           ;; beginning edge of range
           "float,alpha,10.5,10.6" [gran1]
           ;; ending edge of range
           "float,alpha,10,10.5" [gran1]

           ;; only min range provided
           "float,bravo,120," [gran2 gran3]

           ;; only max range provided
           "float,bravo,,13.6" [gran1 gran2]
           "float,charlie,44,45.1" [gran3 gran4]))
    (testing "search by range legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (search/csv->tuples v) {:snake-kebab? false}))

           ;; inside range
           "float,alpha,10.2,11" [gran1]
           ;; beginning edge of range
           "float,alpha,10.5,10.6" [gran1]
           ;; ending edge of range
           "float,alpha,10,10.5" [gran1]

           ;; only min range provided
           "float,bravo,120," [gran2 gran3]

           ;; only max range provided
           "float,bravo,,13.6" [gran1 gran2]
           "float,charlie,44,45.1" [gran3 gran4]))

    (testing "search granules by additionalAttributes float range with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] [{:type :floatRange :name "alpha" :value [10.2 11]}]
           [gran1] [{:type :floatRange :name "alpha" :value [10.5 10.6]}]
           [gran1] [{:type :floatRange :name "alpha" :value [10 10.5]}]
           [gran2 gran3] [{:type :floatRange :name "bravo" :value [120 nil]}]
           [gran1 gran2] [{:type :floatRange :name "bravo" :value [nil 13.6]}]
           [gran3 gran4] [{:type :floatRange :name "charlie" :value [44 45.1]}]))))

(deftest int-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :int})
        psa2 (dc/psa {:name "bravo" :data-type :int})
        psa3 (dc/psa {:name "charlie" :data-type :int :value 45})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" [10 123])
                                                                                 (dg/psa "bravo" [-12])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "bravo" [10 123])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "alpha" [14])
                                                                                 (dg/psa "bravo" [13 123])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "charlie" [14])]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))
           "int,bravo,123" [gran2, gran3]
           "int,alpha,11" []
           "int,bravo,-12" [gran1]
           ;; inherited from collection
           "int,charlie,45" [gran3 gran4]))

    (testing "search by value legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (search/csv->tuples v)))
           "int,bravo,123" [gran2, gran3]
           "int,alpha,11" []
           "int,bravo,-12" [gran1]
           ;; inherited from collection
           "int,charlie,45" [gran3 gran4]))

    (testing "search granules by additionalAttributes int value with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran2 gran3] [{:type :int :name "bravo" :value 123}]
           [] [{:type :int :name "alpha" :value 11}]
           [gran1] [{:type :int :name "bravo" :value -12}]
           ;; inherited from collection
           [gran3 gran4] [{:type :int :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))

           ;; inside range
           "int,alpha,9,11" [gran1]
           ;; beginning edge of range
           "int,alpha,10,11" [gran1]
           ;; ending edge of range
           "int,alpha,9,10" [gran1]

           ;; only min range provided
           "int,bravo,120," [gran2 gran3]

           ;; only max range provided
           "int,bravo,,12" [gran1 gran2]

           ;; range inheritance
           "int,charlie,44,46" [gran3,gran4]))
    (testing "search by range legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (search/csv->tuples v) {:snake-kebab? false}))

           ;; inside range
           "int,alpha,9,11" [gran1]
           ;; beginning edge of range
           "int,alpha,10,11" [gran1]
           ;; ending edge of range
           "int,alpha,9,10" [gran1]

           ;; only min range provided
           "int,bravo,120," [gran2 gran3]

           ;; only max range provided
           "int,bravo,,12" [gran1 gran2]

           ;; range inheritance
           "int,charlie,44,46" [gran3,gran4]))

    (testing "search granules by additionalAttributes int range with aql"
      (are [items additional-attribs]
           (let [condition {:additionalAttributes additional-attribs}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] [{:type :intRange :name "alpha" :value [9 11]}]
           [gran1] [{:type :intRange :name "alpha" :value [10 11]}]
           [gran1] [{:type :intRange :name "alpha" :value [9 10]}]
           [gran2 gran3] [{:type :intRange :name "bravo" :value [120 nil]}]
           [gran1 gran2] [{:type :intRange :name "bravo" :value [nil 12]}]
           ;; range inheritance
           [gran3 gran4] [{:type :intRange :name "charlie" :value [44 46]}]))))

(deftest datetime-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :datetime})
        psa2 (dc/psa {:name "bravo" :data-type :datetime})
        psa3 (dc/psa {:name "charlie" :data-type :datetime :value (d/make-datetime 45 false)})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes
                                                   [(dg/psa "alpha"
                                                            [(d/make-datetime 10) (d/make-datetime 123)])
                                                    (dg/psa "bravo"
                                                            [(d/make-datetime 0)])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes
                                                   [(dg/psa "bravo"
                                                            [(d/make-datetime 10) (d/make-datetime 123)])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes
                                                   [(dg/psa "alpha"
                                                            [(d/make-datetime 14)])
                                                    (dg/psa "bravo"
                                                            [(d/make-datetime 13) (d/make-datetime 123)])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes
                                                   [(dg/psa "charlie"
                                                            [(d/make-datetime 14)])]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (d/refs-match?
             items (search/find-refs :granule {"attribute[]" (str v (d/make-datetime n))}))
           "datetime,bravo," 123 [gran2, gran3]
           "datetime,alpha," 11 []
           "datetime,bravo," 0 [gran1]
           "datetime,charlie," 45 [gran3 gran4]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (d/refs-match? items
                          (search/find-refs :granule (search/csv->tuples (str v (d/make-datetime n)))))
           "datetime,bravo," 123 [gran2, gran3]
           "datetime,alpha," 11 []
           "datetime,bravo," 0 [gran1]
           "datetime,charlie," 45 [gran3 gran4]))

    (testing "search granules by additionalAttributes datetime value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] d/make-datetime) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran2 gran3] [{:type :date :name "bravo" :value 123}]
           [] [{:type :date :name "alpha" :value 11}]
           [gran1] [{:type :date :name "bravo" :value 0}]
           [gran3 gran4] [{:type :date :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (d/make-datetime min-n)
                 max-v (d/make-datetime max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :granule {"attribute[]" full-value})))

           ;; inside range
           "datetime,alpha," 9 11 [gran1]
           ;; beginning edge of range
           "datetime,alpha," 10 11 [gran1]
           ;; ending edge of range
           "datetime,alpha," 9 10 [gran1]

           ;; only min range provided
           "datetime,bravo," 120 nil [gran2 gran3]

           ;; only max range provided
           "datetime,bravo," nil 12 [gran1 gran2]

           "datetime,charlie," 44 45 [gran3 gran4]))

    (testing "search by range legacy parameters"
      (are [v min-n max-n items]
           (let [min-v (d/make-datetime min-n)
                 max-v (d/make-datetime max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :granule (search/csv->tuples full-value) {:snake-kebab? false})))

           ;; inside range
           "datetime,alpha," 9 11 [gran1]
           ;; beginning edge of range
           "datetime,alpha," 10 11 [gran1]
           ;; ending edge of range
           "datetime,alpha," 9 10 [gran1]

           ;; only min range provided
           "datetime,bravo," 120 nil [gran2 gran3]

           ;; only max range provided
           "datetime,bravo," nil 12 [gran1 gran2]

           "datetime,charlie," 44 45 [gran3 gran4]))

    (testing "search granules by additionalAttributes date range with aql"
      (are [items additional-attribs]
           (let [value-fn (fn [v] (map d/make-datetime v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] [{:type :dateRange :name "alpha" :value [9 11]}]
           [gran1] [{:type :dateRange :name "alpha" :value [10 11]}]
           [gran1] [{:type :dateRange :name "alpha" :value [9 10]}]
           [gran2 gran3] [{:type :dateRange :name "bravo" :value [120 nil]}]
           [gran1 gran2] [{:type :dateRange :name "bravo" :value [nil 12]}]
           ;; range inheritance
           [gran3 gran4] [{:type :dateRange :name "charlie" :value [44 45]}]))))

(deftest time-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :time})
        psa2 (dc/psa {:name "bravo" :data-type :time})
        psa3 (dc/psa {:name "charlie" :data-type :time :value (d/make-time 45 false)})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes
                                                   [(dg/psa "alpha"
                                                            [(d/make-time 10) (d/make-time 23)])
                                                    (dg/psa "bravo"
                                                            [(d/make-time 0)])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes
                                                   [(dg/psa "bravo"
                                                            [(d/make-time 10) (d/make-time 23)])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes
                                                   [(dg/psa "alpha"
                                                            [(d/make-time 14)])
                                                    (dg/psa "bravo"
                                                            [(d/make-time 13) (d/make-time 23)])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes
                                                   [(dg/psa "charlie"
                                                            [(d/make-time 14)])]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (d/refs-match?
             items (search/find-refs :granule {"attribute[]" (str v (d/make-time n))}))
           "time,bravo," 23 [gran2, gran3]
           "time,alpha," 11 []
           "time,bravo," 0 [gran1]
           "time,charlie," 45 [gran3 gran4]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (d/refs-match? items
                          (search/find-refs :granule (search/csv->tuples (str v (d/make-time n)))))
           "time,bravo," 23 [gran2, gran3]
           "time,alpha," 11 []
           "time,bravo," 0 [gran1]
           "time,charlie," 45 [gran3 gran4]))

    (testing "search granules by additionalAttributes time value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] d/make-time) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran2 gran3] [{:type :time :name "bravo" :value 23}]
           [] [{:type :time :name "alpha" :value 11}]
           [gran1] [{:type :time :name "bravo" :value 0}]
           [gran3 gran4] [{:type :time :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (d/make-time min-n)
                 max-v (d/make-time max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :granule {"attribute[]" full-value})))

           ;; inside range
           "time,alpha," 9 11 [gran1]
           ;; beginning edge of range
           "time,alpha," 10 11 [gran1]
           ;; ending edge of range
           "time,alpha," 9 10 [gran1]

           ;; only min range provided
           "time,bravo," 20 nil [gran2 gran3]

           ;; only max range provided
           "time,bravo," nil 12 [gran1 gran2]

           "time,charlie," 44 45 [gran3 gran4]))

    (testing "search by range legacy parameters"
      (are [v min-n max-n items]
           (let [min-v (d/make-time min-n)
                 max-v (d/make-time max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :granule (search/csv->tuples full-value) {:snake-kebab? false})))

           ;; inside range
           "time,alpha," 9 11 [gran1]
           ;; beginning edge of range
           "time,alpha," 10 11 [gran1]
           ;; ending edge of range
           "time,alpha," 9 10 [gran1]

           ;; only min range provided
           "time,bravo," 20 nil [gran2 gran3]

           ;; only max range provided
           "time,bravo," nil 12 [gran1 gran2]

           "time,charlie," 44 45 [gran3 gran4]))

    (testing "search granules by additionalAttributes time range with aql"
      (are [items additional-attribs]
           (let [value-fn (fn [v] (map d/make-time v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] [{:type :timeRange :name "alpha" :value [9 11]}]
           [gran1] [{:type :timeRange :name "alpha" :value [10 11]}]
           [gran1] [{:type :timeRange :name "alpha" :value [9 10]}]
           [gran2 gran3] [{:type :timeRange :name "bravo" :value [20 nil]}]
           [gran1 gran2] [{:type :timeRange :name "bravo" :value [nil 12]}]
           ;; range inheritance
           [gran3 gran4] [{:type :timeRange :name "charlie" :value [44 45]}]))))

(deftest date-psas-search-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :date})
        psa2 (dc/psa {:name "bravo" :data-type :date})
        psa3 (dc/psa {:name "charlie" :data-type :date :value (d/make-date 45 false)})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes
                                                   [(dg/psa "alpha"
                                                            [(d/make-date 10) (d/make-date 23)])
                                                    (dg/psa "bravo"
                                                            [(d/make-date 0)])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes
                                                   [(dg/psa "bravo"
                                                            [(d/make-date 10) (d/make-date 23)])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes
                                                   [(dg/psa "alpha"
                                                            [(d/make-date 14)])
                                                    (dg/psa "bravo"
                                                            [(d/make-date 13) (d/make-date 23)])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes
                                                   [(dg/psa "charlie"
                                                            [(d/make-date 14)])]}))]
    (index/wait-until-indexed)

    (testing "search by value"
      (are [v n items]
           (d/refs-match?
             items (search/find-refs :granule {"attribute[]" (str v (d/make-date n))}))
           "date,bravo," 23 [gran2, gran3]
           "date,alpha," 11 []
           "date,bravo," 0 [gran1]
           "date,charlie," 45 [gran3 gran4]))

    (testing "search by value legacy parameters"
      (are [v n items]
           (d/refs-match? items
                          (search/find-refs :granule (search/csv->tuples (str v (d/make-date n)))))
           "date,bravo," 23 [gran2, gran3]
           "date,alpha," 11 []
           "date,bravo," 0 [gran1]
           "date,charlie," 45 [gran3 gran4]))

    (testing "search granules by additionalAttributes date value with aql"
      (are [items additional-attribs]
           (let [value (map #(update-in % [:value] d/make-date) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran2 gran3] [{:type :date :name "bravo" :value 23}]
           [] [{:type :date :name "alpha" :value 11}]
           [gran1] [{:type :date :name "bravo" :value 0}]
           [gran3 gran4] [{:type :date :name "charlie" :value 45}]))

    (testing "search by range"
      (are [v min-n max-n items]
           (let [min-v (d/make-date min-n)
                 max-v (d/make-date max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :granule {"attribute[]" full-value})))

           ;; inside range
           "date,alpha," 9 11 [gran1]
           ;; beginning edge of range
           "date,alpha," 10 11 [gran1]
           ;; ending edge of range
           "date,alpha," 9 10 [gran1]

           ;; only min range provided
           "date,bravo," 20 nil [gran2 gran3]

           ;; only max range provided
           "date,bravo," nil 12 [gran1 gran2]

           "date,charlie," 44 45 [gran3 gran4]))

    (testing "search by range legacy parameters"
      (are [v min-n max-n items]
           (let [min-v (d/make-date min-n)
                 max-v (d/make-date max-n)
                 full-value (str v min-v "," max-v)]
             (d/refs-match? items (search/find-refs :granule (search/csv->tuples full-value) {:snake-kebab? false})))

           ;; inside range
           "date,alpha," 9 11 [gran1]
           ;; beginning edge of range
           "date,alpha," 10 11 [gran1]
           ;; ending edge of range
           "date,alpha," 9 10 [gran1]

           ;; only min range provided
           "date,bravo," 20 nil [gran2 gran3]

           ;; only max range provided
           "date,bravo," nil 12 [gran1 gran2]

           "date,charlie," 44 45 [gran3 gran4]))

    (testing "search granules by additionalAttributes date range with aql"
      (are [items additional-attribs]
           (let [value-fn (fn [v] (map d/make-date v))
                 value (map #(update-in % [:value] value-fn) additional-attribs)
                 condition {:additionalAttributes value}]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] [{:type :dateRange :name "alpha" :value [9 11]}]
           [gran1] [{:type :dateRange :name "alpha" :value [10 11]}]
           [gran1] [{:type :dateRange :name "alpha" :value [9 10]}]
           [gran2 gran3] [{:type :dateRange :name "bravo" :value [20 nil]}]
           [gran1 gran2] [{:type :dateRange :name "bravo" :value [nil 12]}]
           ;; range inheritance
           [gran3 gran4] [{:type :dateRange :name "charlie" :value [44 45]}]))))

(deftest additional-attribute-search-by-name-test
  (let [psa1 (dc/psa {:name "alpha" :data-type :string})
        psa2 (dc/psa {:name "beta" :data-type :string})
        psa3 (dc/psa {:name "gamma" :data-type :time})
        psa4 (dc/psa {:name "delta" :data-type :string})
        psa5 (dc/psa {:name "alpha" :data-type :time})
        psa6 (dc/psa {:name "sigma" :data-type :string})
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3]}))
        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa4 psa5 psa6]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" ["atlantic"])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "beta" ["wind"])]}))
        gran3 (d/ingest "PROV1" (dg/granule coll1
                                            {:product-specific-attributes [(dg/psa "alpha" ["pacific"])
                                                                           (dg/psa "gamma" ["01:02:03Z"])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2
                                            {:product-specific-attributes [(dg/psa "delta" ["rain"])]}))

        gran5 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "alpha" ["01:02:03Z"])]}))
        gran6 (d/ingest "PROV1" (dg/granule coll2
                                            {:product-specific-attributes [(dg/psa "delta" ["cloud"])
                                                                           (dg/psa "sigma" ["ocean"])]}))]
    (index/wait-until-indexed)

    (testing "granule psa search by names"
      (testing "single name"
        (are [v items options]
             (let [params (assoc options "attribute[]" v)]
               (d/refs-match? items (search/find-refs :granule params)))
             "no_match" [] nil
             "alpha" [gran1 gran2 gran3 gran4 gran5 gran6] nil
             "alpha" [gran1 gran2 gran3 gran4 gran5 gran6] {"options[attribute][exclude-collection]" false}
             "alpha" [gran1 gran3 gran5] {"options[attribute][exclude-collection]" true}
             "beta" [gran1 gran2 gran3] nil
             "beta" [gran1 gran2 gran3] {"options[attribute][exclude-collection]" false}
             "beta" [gran2] {"options[attribute][exclude-collection]" true}
             "gamma" [gran1 gran2 gran3] nil
             "gamma" [gran1 gran2 gran3] {"options[attribute][exclude-collection]" false}
             "gamma" [gran3] {"options[attribute][exclude-collection]" true}
             "delta" [gran4 gran5 gran6] nil
             "delta" [gran4 gran5 gran6] {"options[attribute][exclude-collection]" false}
             "delta" [gran4 gran6] {"options[attribute][exclude-collection]" true}))
      (testing "multiple names"
        (are [v items options]
             (let [params (assoc options "attribute[]" v)]
               (d/refs-match? items (search/find-refs :granule params)))
             ["alpha" "sigma"] [gran4 gran5 gran6] nil
             ["alpha" "sigma"] [gran4 gran5 gran6] {"options[attribute][or]" false}
             ["alpha" "sigma"] [gran1 gran2 gran3 gran4 gran5 gran6] {"options[attribute][or]" true}
             ["alpha" "sigma"] [gran1 gran2 gran3 gran4 gran5 gran6] {"options[attribute][or]" true
                                                                      "options[attribute][exclude-collection]" false}
             ["alpha" "sigma"] [gran1 gran3 gran5 gran6] {"options[attribute][or]" true
                                                          "options[attribute][exclude-collection]" true}
             ["delta" "sigma"] [gran4 gran5 gran6] nil
             ["delta" "sigma"] [gran4 gran5 gran6] {"options[attribute][or]" false}
             ["delta" "sigma"] [gran4 gran5 gran6] {"options[attribute][or]" false
                                                    "options[attribute][exclude-collection]" false}
             ["delta" "sigma"] [gran6] {"options[attribute][or]" false
                                        "options[attribute][exclude-collection]" true})))))
