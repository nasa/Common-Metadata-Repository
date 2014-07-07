(ns cmr.system-int-test.search.granule-psa-search-test
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
            [clj-http.client :as client]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(comment
  (ingest/create-provider "PROV1")
  )

(defn- csv->tuples
  "Convert a comma-separated-value string into a set of tuples to be use with find-refs."
  [csv]
  (let [[type name min-value max-value] (s/split csv #"," -1)
        tuples [["attribute[][name]" name]
                ["attribute[][type]" type]]]
    (cond
      (and (not (empty? max-value)) (not (empty? min-value)))
      (into tuples [["attribute[][minValue]" min-value]
                    ["attribute[][maxValue]" max-value]])
      (not (empty? max-value))
      (conj tuples ["attribute[][maxValue]" max-value])

      max-value ;; max-value is empty but not nil
      (conj tuples ["attribute[][minValue]" min-value])

      :else ; min-value is really value
      (conj tuples ["attribute[][value]" min-value]))))

(deftest invalid-psa-searches
  (are [v error]
       (= {:status 422 :errors [error]}
          (search/find-refs :granule {"attribute[]" v}))
       ",alpha,a" (am/invalid-type-msg "")
       "foo,alpha,a" (am/invalid-type-msg "foo")
       ",alpha,a,b" (am/invalid-type-msg "")

       "string,,a" (am/invalid-name-msg "")
       "string,,a,b" (am/invalid-name-msg "")
       "string,alpha," (am/invalid-value-msg :string "")
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

  (is (= {:status 422 :errors [(am/attributes-must-be-sequence-msg)]}
         (search/find-refs :granule {"attribute" "string,alpha,a"}))))

;; These are for boolean, datetime_string, time_string, and date_string attribute types which are all indexed and searchable as strings.
(deftest indexed-as-string-psas-search-test
  (let [psa1 (dc/psa "bool" :boolean)
        psa2 (dc/psa "dts" :datetime-string)
        psa3 (dc/psa "ts" :time-string)
        psa4 (dc/psa "ds" :date-string)
        coll (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3 psa4]}))
        gran1 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "bool" [true])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "bool" [false])]}))

        gran3 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "dts" ["2012-01-01T01:02:03Z"])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "dts" ["2012-01-02T01:02:03Z"])]}))

        gran5 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "ts" ["01:02:03Z"])]}))
        gran6 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "ts" ["01:02:04Z"])]}))

        gran7 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "ds" ["2012-01-01"])]}))
        gran8 (d/ingest "PROV1" (dg/granule coll {:product-specific-attributes [(dg/psa "ds" ["2012-01-02"])]}))]
    (index/refresh-elastic-index)
    (are [v items]
         (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))
         "string,bool,true" [gran1]
         "string,bool,false" [gran2]

         "string,dts,2012-01-01T01:02:03Z" [gran3]
         "string,ts,01:02:03Z" [gran5]
         "string,ds,2012-01-01" [gran7])))

(deftest string-psas-search-test
  (let [psa1 (dc/psa "alpha" :string)
        psa2 (dc/psa "bravo" :string)
        psa3 (dc/psa "charlie" :string "foo")
        psa4 (dc/psa "case" :string)

        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" ["ab" "bc"])
                                                                                 (dg/psa "bravo" ["cd" "bf"])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "bravo" ["ab"])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "bravo" ["aa" "bf"])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "charlie" ["az"])]}))

        coll3 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa4]}))
        gran5 (d/ingest "PROV1" (dg/granule coll3 {:product-specific-attributes [(dg/psa "case" ["UP"])]}))]
    (index/refresh-elastic-index)
    (testing "search by value"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))
           "string,alpha,ab" [gran1]
           "string,alpha,AB" [gran1]
           "string,alpha,c" []
           "string,bravo,bf" [gran1 gran3]
           "string,case,UP" [gran5]
           "string,case,up" [gran5]

           ;; tests by value inheritance
           "string,charlie,FoO" [gran3 gran4]
           "string,charlie,foo" [gran3 gran4]))

    (testing "search by value catalog-rest style"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (csv->tuples v)))
           "string,alpha,ab" [gran1]
           "string,alpha,c" []
           "string,alpha,c" []
           "string,bravo,bf" [gran1 gran3]

           ;; tests by value inheritance
           "string,charlie,foo" [gran3 gran4]))

    (client/generate-query-string [["attribute[][name]" "alpha"]
                                   ["attribute[][type]" "string"]
                                   ["attribute[][value]" "aa"]
                                   ["attribute[][maxValue]" "ab"]])

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

           ["string,alpha,ab" "string,bravo,,bc"] [gran1 gran2 gran3] :or
           ["string,alpha,ab" "string,bravo,,bc"] [] :and
           ["string,alpha,ab" "string,bravo,bc,"] [gran1] :and
           ; and is the default
           ["string,alpha,ab" "string,bravo,,bc"] [] nil ))
    (testing "searching with multiple attribute conditions catalog-rest style"
      (are [v items operation]
           (let [query (mapcat csv->tuples v)
                 query (if operation
                         (merge query ["options[attribute][or]" (= operation :or)])
                         query)]
             (d/refs-match?
               items
               (search/find-refs
                 :granule
                 query
                 {:snake-kebab? false})))

           ["string,alpha,ab" "string,bravo,,bc"] [gran1 gran2 gran3] :or
           ["string,alpha,ab" "string,bravo,,bc"] [] :and
           ["string,alpha,ab" "string,bravo,bc,"] [gran1] :and
           ; and is the default
           ["string,alpha,ab" "string,bravo,,bc"] [] nil ))

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
           "string,bravo,,bc" [gran2 gran3]

           ;; Range value inheritance
           "string,charlie,foa,foz" [gran3 gran4]))

    (testing "search by range catalog-rest style"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (csv->tuples v) {:snake-kebab? false}))

           ;; inside range
           "string,alpha,aa,ac"  [gran1]
           ;; beginning edge of range
           "string,alpha,ab,ac"[gran1]
           ;; ending edge of range
           "string,alpha,aa,ab" [gran1]

           ;; only min range provided
           "string,bravo,bc," [gran1 gran3]

           ;; only max range provided
           "string,bravo,,bc" [gran2 gran3]

           ;; Range value inheritance
           "string,charlie,foa,foz" [gran3 gran4]))))

(deftest float-psas-search-test
  (let [psa1 (dc/psa "alpha" :float)
        psa2 (dc/psa "bravo" :float)
        psa3 (dc/psa "charlie" :float 45)
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" [10.5 123])
                                                                                 (dg/psa "bravo" [-12])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "bravo" [10.5 123])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "alpha" [14])
                                                                                 (dg/psa "bravo" [13.7 123])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "charlie" [14])]}))]
    (index/refresh-elastic-index)

    (testing "search by value"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))
           "float,bravo,123" [gran2, gran3]
           "float,alpha,10" []
           "float,bravo,-12" [gran1]
           "float,charlie,45" [gran3 gran4]))

    (testing "search by value legacy parameters"
      (are [v items]
           (d/refs-match? items (search/find-refs :granule (csv->tuples v)))
           "float,bravo,123" [gran2, gran3]
           "float,alpha,10" []
           "float,bravo,-12" [gran1]
           "float,charlie,45" [gran3 gran4]))

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
           (d/refs-match? items (search/find-refs :granule (csv->tuples v) {:snake-kebab? false}))

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
           "float,charlie,44,45.1" [gran3 gran4]))))

(deftest int-psas-search-test
  (let [psa1 (dc/psa "alpha" :int)
        psa2 (dc/psa "bravo" :int)
        psa3 (dc/psa "charlie" :int 45)
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" [10 123])
                                                                                 (dg/psa "bravo" [-12])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "bravo" [10 123])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "alpha" [14])
                                                                                 (dg/psa "bravo" [13 123])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "charlie" [14])]}))]
    (index/refresh-elastic-index)

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
           (d/refs-match? items (search/find-refs :granule (csv->tuples v)))
           "int,bravo,123" [gran2, gran3]
           "int,alpha,11" []
           "int,bravo,-12" [gran1]
           ;; inherited from collection
           "int,charlie,45" [gran3 gran4]))

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
           (d/refs-match? items (search/find-refs :granule (csv->tuples v) {:snake-kebab? false}))

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
           "int,charlie,44,46" [gran3,gran4]))))

(deftest datetime-psas-search-test
  (let [psa1 (dc/psa "alpha" :datetime)
        psa2 (dc/psa "bravo" :datetime)
        psa3 (dc/psa "charlie" :datetime (d/make-datetime 45 false))
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
    (index/refresh-elastic-index)

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
                          (search/find-refs :granule (csv->tuples (str v (d/make-datetime n)))))
           "datetime,bravo," 123 [gran2, gran3]
           "datetime,alpha," 11 []
           "datetime,bravo," 0 [gran1]
           "datetime,charlie," 45 [gran3 gran4]))

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
             (d/refs-match? items (search/find-refs :granule (csv->tuples full-value) {:snake-kebab? false})))

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

           "datetime,charlie," 44 45 [gran3 gran4]))))

(deftest time-psas-search-test
  (let [psa1 (dc/psa "alpha" :time)
        psa2 (dc/psa "bravo" :time)
        psa3 (dc/psa "charlie" :time (d/make-time 45 false))
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
    (index/refresh-elastic-index)

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
                          (search/find-refs :granule (csv->tuples (str v (d/make-time n)))))
           "time,bravo," 23 [gran2, gran3]
           "time,alpha," 11 []
           "time,bravo," 0 [gran1]
           "time,charlie," 45 [gran3 gran4]))

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
             (d/refs-match? items (search/find-refs :granule (csv->tuples full-value) {:snake-kebab? false})))

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

           "time,charlie," 44 45 [gran3 gran4]))))

(deftest date-psas-search-test
  (let [psa1 (dc/psa "alpha" :date)
        psa2 (dc/psa "bravo" :date)
        psa3 (dc/psa "charlie" :date (d/make-date 45 false))
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
    (index/refresh-elastic-index)

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
                          (search/find-refs :granule (csv->tuples (str v (d/make-date n)))))
           "date,bravo," 23 [gran2, gran3]
           "date,alpha," 11 []
           "date,bravo," 0 [gran1]
           "date,charlie," 45 [gran3 gran4]))

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
             (d/refs-match? items (search/find-refs :granule (csv->tuples full-value) {:snake-kebab? false})))

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

           "date,charlie," 44 45 [gran3 gran4]))))





