(ns cmr.common.test.test-check-ext-test
  (:require [clojure.test :refer :all]
            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :as ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]))


(def min-max-gen
  "Generator creating min and max integer tuple that will be separated by at least 3"
  (gen/bind gen/int
            (fn [i]
              (gen/tuple (gen/return i)
                         (gen/fmap #(+ % i 2) gen/s-pos-int)))))

(def min-max-double-gen
  "Generates tuples with a min value, max value, and a double generated between them."
  (gen/bind min-max-gen
            (fn [[minv maxv]]
              (gen/tuple (gen/return minv) (gen/return maxv) (ext/choose-double minv maxv)))))


;; Tests our double generator to make sure that it creates valid double values.
(defspec choose-double-test 1000
  (for-all [min-max-d min-max-double-gen]
    (let [[minv maxv d] min-max-d]
      (and (= d (double d))
           (>= d minv)
           (<= d maxv)))))

