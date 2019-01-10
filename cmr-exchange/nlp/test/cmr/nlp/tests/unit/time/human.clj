(ns cmr.nlp.tests.unit.time.human
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.nlp.tests.data :as test-data]
    [cmr.nlp.time.human :as human-time])
  (:import
    (java.util Date)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def parser (human-time/create))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Tests   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest dates
  (is (= [[Date]]
         (map #(map type %)
              (human-time/dates parser test-data/spatio-temporal-tokens-1))))
  (is (= [[Date]]
         (map #(map type %)
              (human-time/dates parser test-data/spatio-temporal-tokens-2)))))

(deftest date
  (is (= Date
         (type (human-time/date parser test-data/spatio-temporal-tokens-1))))
  (is (= Date
         (type (human-time/date parser test-data/spatio-temporal-tokens-2)))))
