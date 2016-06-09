(ns cmr.search.test.services.parameter-validation
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters.parameter-validation :as pv]
            [cmr.common-app.services.search.parameter-validation :as cpv]
            [cmr.search.services.messages.attribute-messages :as attrib-msg]
            [cmr.search.services.messages.orbit-number-messages :as on-msg]
            [cmr.common.services.messages :as com-msg]
            [cmr.search.services.messages.common-messages :as msg]
            [cmr.common-app.services.search.messages :as cmsg]))

(def valid-params
  "Example valid parameters"
  {:entry-title "foo"
   :page-size 10
   :options {:entry-title {:ignore-case "true"}}})

(deftest individual-parameter-validation-test
  (testing "unrecognized parameters"
    (is (= [] (cpv/unrecognized-params-validation :collection valid-params)))
    (is (= #{"Parameter [foo] was not recognized."
             "Parameter [bar] was not recognized."}
           (set (cpv/unrecognized-params-validation :collection
                                                    {:entry-title "fdad"
                                                     :echo-compatible "true"
                                                     :foo 1
                                                     :bar 2})))))
  (is (= ["Parameter [page_size] was not recognized."]
         (pv/unrecognized-tile-params-validation {:page-size 1
                                                  :point "50, 50"})))
  (testing "invalid options param names"
    (is (= [] (cpv/unrecognized-params-in-options-validation :collection valid-params)))
    (is (= ["Parameter [foo] with option was not recognized."]
           (cpv/unrecognized-params-in-options-validation :collection
                                                          {:entry-title "fdad"
                                                           :options {:foo {:ignore-case "true"}}}))))
  (testing "invalid options param args"
    (is (= [(cmsg/invalid-opt-for-param :entry-title :foo)]
           (cpv/parameter-options-validation :collection {:entry-title "fdad"
                                                          :options {:entry-title {:foo "true"}}}))))

  (testing "for a parameter requiring a single value validating a value vector returns an error"
    (is (= ["Parameter [keyword] must have a single value."]
           (cpv/single-value-validation :collection {:keyword ["foo"]}))))
  (testing "for multiple parameters requiring single values validating value vectors returns multiple errors"
    (is (= ["Parameter [page_size] must have a single value."
            "Parameter [keyword] must have a single value."]
           (cpv/single-value-validation :collection {:keyword ["foo"] :page-size [10] :platform ["bar"]}))))
  (testing "for a parameter allowing multiple values validating a value vector returns no error"
    (is (= []
           (cpv/single-value-validation :collection {:platform ["bar"]}))))
  (testing "for a parameter requiring a single value validating a single value returns no error"
    (is (= []
           (cpv/single-value-validation :collection {:keyword "foo"}))))
  (testing "for a parameter requiring a single value validating no value returns no error"
    (is (= []
           (cpv/single-value-validation :collection {}))))
  (testing "for a parameter requiring vector of values validating a value map returns an error"
    (is (= ["Parameter [concept_id] must have a single value or multiple values."]
           (cpv/multiple-value-validation :collection {:concept-id {0 "C1-PROV1"}}))))
  (testing "for multiple parameters requiring vector of values validating value maps returns multiple errors"
    (is (= ["Parameter [concept_id] must have a single value or multiple values."
            "Parameter [platform] must have a single value or multiple values."]
           (cpv/multiple-value-validation :collection {:concept-id {0 "C1-PROV1"}
                                                       :platform {0 "bar"}
                                                       :page-size 10}))))

  ;; Page Size
  (testing "Search with large page size"
    (is (= []
           (cpv/page-size-validation :collection (assoc valid-params :page-size 100)))))
  (testing "Negative page size"
    (is (= ["page_size must be a number between 0 and 2000"]
           (cpv/page-size-validation :collection (assoc valid-params :page-size -1)))))
  (testing "Page size too large."
    (is (= ["page_size must be a number between 0 and 2000"]
           (cpv/page-size-validation :collection (assoc valid-params :page-size 2001)))))
  (testing "Non-numeric page size"
    (is (= ["page_size must be a number between 0 and 2000"]
           (cpv/page-size-validation :collection (assoc valid-params :page-size "ABC")))))

  ;; Page Num
  (testing "Valid page_num"
    (is (= []
           (cpv/page-num-validation :collection (assoc valid-params :page-num 5)))))
  (testing "Page num less than one"
    (is (= ["page_num must be a number greater than or equal to 1"]
           (cpv/page-num-validation :collection (assoc valid-params :page-num 0)))))
  (testing "Non-numeric page num"
    (is (= ["page_num must be a number greater than or equal to 1"]
           (cpv/page-num-validation :collection (assoc valid-params :page-num "ABC")))))

  ;; Sort Key
  (testing "sort key"
    (are [sort-key type errors]
      (= errors
         (cpv/sort-key-validation type {:sort-key sort-key}))

      nil :collection []
      "entry-title" :collection []
      ["entry-title" "start-date"] :collection []
      ["+entry-title" "-start-date"] :collection []
      "foo" :collection [(cmsg/invalid-sort-key "foo" :collection)]
      ["foo" "-bar" "+chew"] :collection [(cmsg/invalid-sort-key "foo" :collection)
                                          (cmsg/invalid-sort-key "bar" :collection)
                                          (cmsg/invalid-sort-key "chew" :collection)]
      ["foo" "-bar" "+chew"] :granule [(cmsg/invalid-sort-key "foo" :granule)
                                       (cmsg/invalid-sort-key "bar" :granule)
                                       (cmsg/invalid-sort-key "chew" :granule)]))

  ;; Orbit Number
  (testing "Valid exact orbit_number"
    (is (= []
           (pv/orbit-number-validation :granule (assoc valid-params :orbit-number "10")))))
  (testing "Valid orbit_number range"
    (is (= []
           (pv/orbit-number-validation :granule (assoc valid-params :orbit-number "1,2")))))
  (testing "Non-numeric single orbit-number"
    (is (= [(on-msg/invalid-orbit-number-msg) (com-msg/invalid-msg java.lang.Double "A")]
           (pv/orbit-number-validation :granlue (assoc valid-params :orbit-number "A")))))
  (testing "Non-numeric start-orbit-number"
    (is (= [(on-msg/invalid-orbit-number-msg) (com-msg/invalid-msg java.lang.Double "A")]
           (pv/orbit-number-validation :granule (assoc valid-params :orbit-number "A,10")))))
  (testing "Non-numeric stop-orbit-number"
    (is (= [(on-msg/invalid-orbit-number-msg) (com-msg/invalid-msg java.lang.Double "A")]
           (pv/orbit-number-validation :granule (assoc valid-params :orbit-number "10,A")))))

  ;; Equator Crossing Longitude
  (testing "Valid equator-crossing-longitude range"
    (is (= []
           (pv/equator-crossing-longitude-validation :granule (assoc valid-params :equator-crossing-longitude "10,120")))))
  (testing "Non-numeric equator-crossing-longitude"
    (is (= [(com-msg/invalid-msg java.lang.Double "A")]
           (pv/equator-crossing-longitude-validation :granule (assoc valid-params :equator-crossing-longitude "A,10")))))
  (testing "Non-numeric equator-crossing-longitude"
    (is (= [(com-msg/invalid-msg java.lang.Double "A")]
           (pv/equator-crossing-longitude-validation :granule (assoc valid-params :equator-crossing-longitude "10,A")))))

  ;; Point, Line, Polygon and Bounding-Box
  (testing "a spatial parameter can be a multi-valued parameter"
    (is (empty?
         (pv/bounding-box-validation :granule {:bounding-box ["-180,-90,180,90","-20,-20,20,20"]}))))
  (testing "a geometry parameter which is invalid returns a parsing error"
    (is (= ["[10.0,-.3] is not a valid URL encoded point"]
           (pv/point-validation :granule {:point "10.0,-.3"}))))

  ;; Boolean parameter validations
  (testing "valid boolean parameters do not return an error"
    (is (= []
           (pv/boolean-value-validation :collection {:hierarchical-facets "TRUE"
                                                     :downloadable "uNSet"}))))
  (testing "boolean parameters with an invalid value return an error"
    (is (= ["Parameter hierarchical_facets must take value of true, false, or unset, but was [not-right]"]
           (pv/boolean-value-validation :collection {:hierarchical-facets "not-right"})))))

(deftest temporal-format-validation :collection-start-date-test
  (testing "valid-start-date"
    (is (empty? (pv/temporal-format-validation :collection {:temporal ["2014-04-05T00:00:00Z"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal ["2014-04-05T00:00:00"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal ["2014-04-05T00:00:00.123Z"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal ["2014-04-05T00:00:00.123"]}))))
  (testing "invalid-start-date"
    (are [start-date]
         (let [error (pv/temporal-format-validation :collection {:temporal [start-date]})]
           (is (= 1 (count error)))
           (re-find (re-pattern "temporal start datetime is invalid:") (first error)))
         "2014-13-05T00:00:00Z"
         "2014-04-00T00:00:00Z"
         "2014-04-05T24:00:00Z"
         "2014-04-05T00:60:00Z"
         "2014-04-05T00:00:60Z")))

(deftest temporal-format-validation :collection-end-date-test
  (testing "valid-end-date"
    (is (empty? (pv/temporal-format-validation :collection {:temporal [",2014-04-05T00:00:00Z"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal [",2014-04-05T00:00:00"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal [",2014-04-05T00:00:00.123Z"]})))
    (is (empty? (pv/temporal-format-validation :collection {:temporal [",2014-04-05T00:00:00.123"]}))))
  (testing "invalid-end-date"
    (are [end-date]
         (let [error (pv/temporal-format-validation :collection {:temporal [end-date]})]
           (is (= 1 (count error)))
           (re-find (re-pattern "temporal end datetime is invalid:") (first error)))
         ",2014-13-05T00:00:00Z"
         ",2014-04-00T00:00:00Z"
         ",2014-04-05T24:00:00Z"
         ",2014-04-05T00:60:00Z"
         ",2014-04-05T00:00:60Z")))

(deftest validate-temporal-start-day-test
  (testing "valid-start-day"
    (are [start-day] (empty? (pv/temporal-format-validation
                               :collection
                               {:temporal [(str "2014-04-05T18:45:51Z,," start-day)]}))
         "1"
         "366"
         "10"))
  (testing "invalid-start-day"
    (are [start-day err-msg] (= [err-msg]
                                (pv/temporal-format-validation
                                  :collection
                                  {:temporal [(str "2014-04-05T18:45:51Z,," start-day)]}))
         "x" "temporal_start_day [x] must be an integer between 1 and 366"
         "0" "temporal_start_day [0] must be an integer between 1 and 366"
         "367" "temporal_start_day [367] must be an integer between 1 and 366")))

(deftest validate-temporal-end-day-test
  (testing "valid-end-day"
    (are [end-day] (empty? (pv/temporal-format-validation
                             :collection
                             {:temporal [(str "2014-04-05T18:45:51Z,," end-day)]}))
         "1"
         "366"
         "10"))
  (testing "invalid-end-day"
    (are [end-day err-msg] (= [err-msg]
                              (pv/temporal-format-validation
                                :collection
                                {:temporal [(str "2013-04-05T18:45:51Z,2014-04-05T18:45:51Z,," end-day)]}))
         "x" "temporal_end_day [x] must be an integer between 1 and 366"
         "0" "temporal_end_day [0] must be an integer between 1 and 366"
         "367" "temporal_end_day [367] must be an integer between 1 and 366")))

(deftest validate-attributes-is-a-sequence
  (is (= [(attrib-msg/attributes-must-be-sequence-msg)]
         (pv/attribute-validation :granule {:attribute "foo"}))))

(deftest validate-science-keywords-is-a-map
  (is (= []
         (pv/science-keywords-validation :collection {:science-keywords {:0 {:category "Cat1"}}})))
  (are [value] (= [(msg/science-keyword-invalid-format-msg)]
                  (pv/science-keywords-validation :collection {:science-keywords value}))
       "foo"
       ["foo"]
       {:or "true"}))

(deftest validate-science-keywords-search-terms
  (are [term] (= []
                 (pv/science-keywords-validation :collection {:science-keywords {:0 {term "value"}}}))
       :category
       :topic
       :term
       :variable-level-1
       :variable-level-2
       :variable-level-3
       :detailed-variable)
  (is (= ["parameter [categories] is not a valid science keyword search term."]
         (pv/science-keywords-validation :collection {:science-keywords {:0 {:categories "Cat1"}}})))
  (is (= ["parameter [categories] is not a valid science keyword search term."
          "parameter [topics] is not a valid science keyword search term."]
         (pv/science-keywords-validation :collection {:science-keywords {:0 {:categories "Cat1"
                                                                             :topics "Topic1"}}}))))

(deftest validate-parameters-test
  (testing "parameters are returned when valid"
    (is (= valid-params (pv/validate-parameters :collection valid-params)))
    (is (= valid-params (pv/validate-parameters :granule valid-params))))
  (testing "parameters are validated according to concept-type"
    (is (= {:granule-ur "Dummy"} (pv/validate-parameters :granule {:granule-ur "Dummy"})))
    (is (thrown? clojure.lang.ExceptionInfo (pv/validate-parameters :collection {:granule-ur "Dummy"}))))
  (testing "validation errors (rather than type errors) thrown a vector is supplied for page-num"
    (try
      (pv/validate-parameters :collection {:page-num [10]})
      (is false "An error should have been thrown.")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type :bad-request
                :errors #{"Parameter [page_num] must have a single value."}}
               (update-in (ex-data e) [:errors] set))))))
  (testing "errors thrown when parameters are invalid."
    (try
      (pv/validate-parameters :collection {:entry-title "fdad"
                                           :foo 1
                                           :bar 2})
      (is false "An error should have been thrown.")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type :bad-request
                :errors #{"Parameter [foo] was not recognized."
                          "Parameter [bar] was not recognized."}}
               (update-in (ex-data e) [:errors] set)))))))

(deftest exclude-validation-test
  (testing "concept-id is a valid key to exclude"
    (is (= nil (pv/exclude-validation :granule {:exclude {:concept-id "G1-PROV1"}})))
    (is (= nil (pv/exclude-validation :granule {:exclude {:concept-id "G1-CPROV1"}}))))
  (testing "after parameter replacement, anything other than concept-id is not a valid key to exclude"
    (is (= ["Parameter(s) [echo-collection-id] can not be used with exclude."]
           (pv/exclude-validation :granule {:exclude {:echo-collection-id "G1-PROV1"}})))
    (is (= ["Parameter(s) [echo-granule-id] can not be used with exclude."]
           (pv/exclude-validation :granule {:exclude {:echo-granule-id "G1-PROV1"}})))
    (is (= ["Parameter(s) [dummy] can not be used with exclude."]
           (pv/exclude-validation :granule {:exclude {:dummy "G1-PROV1"}}))))
  (testing "collection-concept-id is an invalid value to exclude"
    (is (= ["Exclude collection is not supported, {:concept-id \"C1-PROV1\"}"]
           (pv/exclude-validation :granule {:exclude {:concept-id "C1-PROV1"}})))))

(deftest assoc-keys->param-name-fn-test
  (is (= "foo_bar" (cpv/assoc-keys->param-name [:foo-bar])))
  (is (= "foo_bar[bar_baz][baz_quux]" (cpv/assoc-keys->param-name [:foo-bar :bar-baz :baz-quux]))))

(deftest validate-map-fn-test
  (testing "params contain a map at the specified path"
    (is (= [{:parent {:child {:gchild 0}}} []]
           (cpv/validate-map [:parent :child] {:parent {:child {:gchild 0}}}))))
  (testing "params do not contain an entry for the specified path"
    (is (= [{:parent {:other-child 0}} []]
           (cpv/validate-map [:parent :child] {:parent {:other-child 0}}))))
  (testing "params have something other than a map at the specified path"
    (is (= [{:parent {:other-child 0}} ["Parameter [parent[child]] must include a nested key, parent[child][...]=value."]]
           (cpv/validate-map [:parent :child] {:parent {:child 0 :other-child 0}})))))

(deftest apply-type-validations-fn-test
  (let [type-validation-fns [(partial cpv/validate-map [:foo])
                             (partial cpv/validate-map [:bar])
                             (partial cpv/validate-map [:baz :quux])]
        valid-params {:foo {:foochild 1} :bar {:barchild 1} :baz {:quux {:quuxchild 1} :other-quux 1} :other 1}
        invalid-params {:foo "foovalue" :bar "barvalue" :baz {:quux "quuxvalue" :other-quux 1} :other 1}]
    (testing "passed valid params"
      (is (= [valid-params []]
             (cpv/apply-type-validations valid-params type-validation-fns))))
    (testing "passed invalid params"
      (is (= [{:baz {:other-quux 1} :other 1} ["Parameter [baz[quux]] must include a nested key, baz[quux][...]=value."
                                               "Parameter [bar] must include a nested key, bar[...]=value."
                                               "Parameter [foo] must include a nested key, foo[...]=value."]]
             (cpv/apply-type-validations invalid-params type-validation-fns))))))

(deftest validate-all-map-values-fn-test
  (testing "passed valid map values"
    (let [params {:root {:0 {:k0 :v0} :1 {:k1 :v1}}}]
      (is (= [params []]
             (cpv/validate-all-map-values cpv/validate-map [:root] params)))))
  (testing "passed an invalid map value"
    (let [params {:root {:0 {:k0 :v0} :1 :v1 :2 {:k2 :v2}}}]
      (is (= [{:root {:0 {:k0 :v0} :2 {:k2 :v2}}} ["Parameter [root[1]] must include a nested key, root[1][...]=value."]]
             (cpv/validate-all-map-values cpv/validate-map [:root] params))))))
