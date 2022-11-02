(ns cmr.nlp.tests.unit.query
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.nlp.query :as query]
    [cmr.nlp.tests.data :as test-data]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Data and Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def temporal-param "temporal%5B%5D=")

(defn count-temporals
  [query-string]
  (->> "temporal%5B%5D="
       re-pattern
       (string/split query-string)
       (remove empty?)
       count))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Tests   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest temporal-range-one-value
  (let [result (query/->cmr-temporal {:query test-data/spatio-temporal-sentence-1})
        temporal (:temporal result)]
    (is (string/starts-with? temporal temporal-param))
    (is (string/includes? temporal "%2C")) ; comma
    (is (string/ends-with? temporal "Z"))
    (is (= 1
           (count-temporals temporal)))))

(deftest temporal-range-two-values
  (let [result (query/->cmr-temporal {:query test-data/relative-temporals-sentence-1})
        temporal (:temporal result)]
    (is (string/starts-with? temporal temporal-param))
    (is (string/includes? temporal "%2C")) ; comma
    (is (string/ends-with? temporal "Z"))
    (is (= 1
           (count-temporals temporal)))))

;; XXX Need to add test data NL sentences that cause 3 and 4 dates to be
;;     extracted ...

; (deftest temporal-range-multiple-values-odd
;   )

; (deftest temporal-range-multiple-values-even
;   )
