(ns cmr.common.test.util
  (:require [clojure.test :refer :all]
            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :as gen-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [cmr.common.util :as util :refer [defn-timed]]))

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

(deftest are2-test
  (testing "Normal use"
    (util/are2
      [x y] (= x y)
      "The most basic case with 1"
      2 (+ 1 1)
      "A more complicated test"
      4 (* 2 2)))
  (testing "without comments"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"The number of args doesn't match are2's argv or testing doc string may be missing"
          (eval '(cmr.common.util/are2
                  [x y] (= x y)
                  2 (+ 1 1)
                  4 (* 2 2)))))))

(defn-timed test-timed-multi-arity
  "The doc string"
  ([f]
   (test-timed-multi-arity f f))
  ([fa fb]
   (test-timed-multi-arity fa fb fa))
  ([fa fb fc]
   (fa)
   (fb)
   (fc)))

(defn-timed test-timed-single-arity
  "the doc string"
  [f]
  (f)
  (f)
  (f))

(deftest defn-timed-test
  (testing "single arity"
    (let [counter (atom 0)
          counter-fn #(swap! counter inc)]
      (is (= 3 (test-timed-single-arity counter-fn))))
    (testing "multi arity"
    (let [counter (atom 0)
          counter-fn #(swap! counter inc)]
      (is (= 3 (test-timed-multi-arity counter-fn)))))))

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


(deftest rename-keys-with-test
  (testing "basic rename key tests"
    (let [params {:k [1 2]}
          param-aliases {:k :replaced-k}
          merge-fn concat
          expected {:replaced-k [1 2]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo 2 :bar 4}
          param-aliases {:bar :foo}
          merge-fn +
          expected {:foo 6}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo 5}
          param-aliases {:bar :foo}
          merge-fn +]
      (is (= params
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo [1 2] :bar [3 4]}
          param-aliases {:bar :foo}
          merge-fn concat
          expected {:foo [1 2 3 4]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo [1 2] :bar [3 4]}
          param-aliases {:bar :foo :x :foo}
          merge-fn concat
          expected {:foo [1 2 3 4]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn)))))
  (testing "multiples keys aliasing to same key tests"
    (let [params {:foo 2 :bar 4 :k1 8 :k3 16}
          param-aliases {:bar :foo :k1 :foo :k2 :foo}
          merge-fn +
          expected {:foo 14 :k3 16}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:foo [1 2] :bar [3 4] :k1 8 :k2 "d"}
          param-aliases {:bar :foo :k1 :foo :k2 :foo}
          merge-fn #(concat (if (sequential? %1) %1 [%1])
                            (if (sequential? %2) %2 [%2]))
          expected {:foo [1 2 "d" 8 3 4]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))
    (let [params {:concept-id ["G9000000009-PROV2"],
                  :echo-granule-id ["G1000000006-PROV2"]
                  :echo-collection-id "C1000000002-PROV2"}
          param-aliases {:echo-granule-id :concept-id :echo-collection-id :concept-id :dummy-key :replace-key}
          merge-fn #(concat (if (sequential? %1) %1 [%1])
                            (if (sequential? %2) %2 [%2]))
          expected {:concept-id ["G9000000009-PROV2" "C1000000002-PROV2" "G1000000006-PROV2"]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))))


(defspec map-n-spec 1000
  (for-all [n gen/s-pos-int
            step gen/s-pos-int
            items (gen/vector gen/int)]
    ;; Verifies map-n is equivalent to partition
    (= (util/map-n identity n step items)
       (map identity (partition n step items)))))

(defspec map-n-all-spec 1000
  (for-all [n gen/s-pos-int
            step gen/s-pos-int
            items (gen/vector gen/int)]
    ;; Verifies map-n-all is equivalent to partition-all
    (= (util/map-n-all identity n step items)
       (map identity (partition-all n step items)))))

(defspec pmap-n-all-spec 1000
  (for-all [n gen/s-pos-int
            items (gen/vector gen/int)]
    ;; Verifies pmap-n is equivalent to map-n (just runs in parallel)
    (= (util/map-n-all identity n items)
       (util/pmap-n-all identity n items))))

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

(deftest greater-than?-less-than?-test
  (testing "greater-than? and less-than?"
    (are [items]
         (and
           (apply util/greater-than? items)
           (apply util/less-than? (reverse items)))
         []
         [3]
         [3 2]
         [nil]
         [-1 nil]
         [3 2 1]
         [3.0 2.0 1.0 0.0]
         ["c" "b" "a"]
         [:d :c :b :a]
         [(t/date-time 2015 1 14 4 3 27) (t/date-time 1986 10 14 4 3 28)])))

