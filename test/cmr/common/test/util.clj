(ns cmr.common.test.util
  (:require [clojure.test :refer :all]
            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :as gen-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [cmr.common.util :as util]))

(deftest test-sequence->fn
  (testing "vector of values"
    (let [f (util/sequence->fn [1 2 3])]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "list of values"
    (let [f (util/sequence->fn '(1 2 3))]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= nil (f)))))
  (testing "empty vector"
    (let [f (util/sequence->fn [])]
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "empty list"
    (let [f (util/sequence->fn '())]
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "infinite sequence of values"
    (let [f (util/sequence->fn (iterate inc 1))]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= 4 (f)))
      (is (= 5 (f))))))

(deftest build-validator-test
  (let [error-type :not-found
        errors ["error 1" "error 2"]
        validation (fn [a b]
                     (cond
                       (> a b) errors
                       (< a b) []
                       :else nil))
        validator (util/build-validator error-type validation)]

    (is (nil? (validator 1 1)) "No error should be thrown for valid returning nil")
    (is (nil? (validator 0 1)) "No error should be thrown for valid returning empty vector")

    (try
      (validator 1 0)
      (is false "An exception should have been thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type error-type
                :errors errors}
               (ex-data e)))))))

(deftest remove-nil-keys-test
  (is (= {:a true :c "value" :d false}
         (util/remove-nil-keys
           {:a true :b nil :c "value" :d false}))))

(defspec map-n-spec 100
  (for-all [n gen/s-pos-int
            step gen/s-pos-int
            items (gen/vector gen/int)]
    ;; Verifies map-n is equivalent to partition
    (= (util/map-n vector n step items)
       (partition n step items))))

(defspec double->string-test 1000
  (for-all [d (gen/fmap double gen/ratio)]
    (let [^String double-str (util/double->string d)
          parsed (Double. double-str)]
      ;; Check it should contain an exponent and it doesn't lose precision.
      (and (not (re-find #"[eE]" double-str))
           (= parsed d)))))

(deftest binary-search-test
  (testing "along number line for integer"
    (let [range-size 100
          find-value 23
          matches-fn (fn [^long v minv maxv ^long depth]
                       (if (> depth range-size)
                         (throw (Exception. (format "Depth [%d] exceeded max [%d]" depth range-size)))
                         (cond
                           (< v find-value) :less-than
                           (> v find-value) :greater-than
                           :else v)))
          middle-fn #(int (/ (+ ^long %1 ^long %2) 2))]
      (is (= find-value (util/binary-search 0 range-size middle-fn matches-fn))))))
