(ns cmr.nlp.tests.unit.core
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.nlp.core :as nlp]
    [cmr.nlp.tests.data :as test-data]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest find-locations
  (is (= ["Lake Superior"]
         (nlp/find-locations test-data/spatio-temporal-tokens-1))))

(deftest find-dates
  (is (= ["last week"]
         (nlp/find-dates test-data/spatio-temporal-tokens-1))))
