(ns cmr.ingest.test.services.additional-attribute-validation
  (:require [clojure.test :refer :all]
            [cmr.umm.collection :as c]
            [cmr.common.util :as util]
            [cmr.ingest.services.additional-attribute-validation :as v]))

(deftest aa-range-reduced-test
  (util/are2
    [prev-params params reduced?]
    (let [aa (c/map->ProductSpecificAttribute
               {:parsed-parameter-range-begin (first params)
                :parsed-parameter-range-end (last params)})
          prev-aa (c/map->ProductSpecificAttribute
                    {:parsed-parameter-range-begin (first prev-params)
                     :parsed-parameter-range-end (last prev-params)})]
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
          aa (c/map->ProductSpecificAttribute
               {:name name
                :data-type type
                :parameter-range-begin begin
                :parameter-range-end end})]
      (= expected (#'v/out-of-range-searches aa)))

    ["alpha" :int 1 5] ["int,alpha,,1" "int,alpha,5,"]
    ["alpha" :int 1 nil] ["int,alpha,,1"]
    ["alpha" :int nil 5] ["int,alpha,5,"]
    ["alpha" :float nil 1.23] ["float,alpha,1.23,"]))