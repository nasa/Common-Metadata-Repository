(ns cmr.ingest.test.validation.additional-attribute-validation
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [cmr.common.util :as util]
    [cmr.ingest.validation.additional-attribute-validation :as v]
    [cmr.umm-spec.additional-attribute :as aa]))

(deftest aa-range-reduced-test
  (util/are2
    [prev-params params reduced?]
    (let [aa {::aa/parsed-parameter-range-begin (first params)
              ::aa/parsed-parameter-range-end (last params)}
          prev-aa {::aa/parsed-parameter-range-begin (first prev-params)
                   ::aa/parsed-parameter-range-end (last prev-params)}]
      (= reduced? (#'v/aa-range-reduced? aa prev-aa)))

    "reduced on both ends" [3 9] [4 8] true
    "reduced on min with max" [3 9] [4 10] true
    "reduced on min no max" [3 9] [4 nil] true
    "reduced on max with min" [3 9] [1 8] true
    "reduced on max no min"[3 9] [nil 8] true

    "same range" [3 9] [3 9] false
    "expanded on both ends" [3 9] [2 10] false
    "no max" [3 9] [3 nil] false
    "no min" [3 9] [nil 9] false
    "no range" [3 9] [nil nil] false))

(deftest out-of-range-searches-test
  (are
    [values expected-search]
    (let [[name type begin end] values
          msg (format "Collection additional attribute [%s] cannot be changed since there are existing granules outside of the new value range."
                      name)
          expected {:params {"attribute[]" expected-search
                             "options[attribute][or]" true}
                    :error-msg msg}
          aa {:Name name
              :DataType type
              :ParameterRangeBegin begin
              :ParameterRangeEnd end}]
      (= expected (#'v/out-of-range-searches aa)))

    ["alpha" "INT" 1 5] ["int,alpha,,1" "int,alpha,5,"]
    ["alpha" "INT" 1 nil] ["int,alpha,,1"]
    ["alpha" "INT" nil 5] ["int,alpha,5,"]
    ["alpha" "FLOAT" nil 1.23] ["float,alpha,1.23,"]))

(deftest case-insensitivity-test
  (let [name "InT NaMe"
        type "iNt"
        prev-aa {:Name (str/lower-case name)
                 :DataType (str/lower-case type)}
        aa {:Name name
            :DataType type}]
    (is (empty? (#'v/build-aa-deleted-searches [aa] [prev-aa])))
    ;; TODO: find answer to returned error "Collection additional attribute [InT NaMe] was of DataType [INT], cannot be changed to [I_NT]."
    #_(is (empty? (#'v/build-aa-type-range-searches [aa] [prev-aa])))))
