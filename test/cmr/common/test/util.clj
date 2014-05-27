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
    (let [params {:concept-id ["G9000000009-CMR_PROV2"],
                  :echo-granule-id ["G1000000006-CMR_PROV2"]
                  :echo-collection-id "C1000000002-CMR_PROV2"}
          param-aliases {:echo-granule-id :concept-id :echo-collection-id :concept-id :dummy-key :replace-key}
          merge-fn #(concat (if (sequential? %1) %1 [%1])
                            (if (sequential? %2) %2 [%2]))
          expected {:concept-id ["G9000000009-CMR_PROV2" "C1000000002-CMR_PROV2" "G1000000006-CMR_PROV2"]}]
      (is (= expected
             (util/rename-keys-with params param-aliases merge-fn))))))


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