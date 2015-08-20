(ns cmr.common.test.cache.single-thread-lookup-cache
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as c]
            [cmr.common.cache.single-thread-lookup-cache :as slc]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.common.lifecycle :as l]
            [clojail.core :as clojail]))

(deftest single-thread-lookup-cache-functions-as-cache-test
  (let [cache (l/start (slc/create-single-thread-lookup-cache) nil)]
    (try
      (cache-spec/assert-cache cache)
      (finally
        (l/stop cache nil)))))

(defn fail-lookup-fn
  "A lookup function that will throw an exception"
  []
  (throw (Exception. "failure")))

;; If an exception occurs while looking up something we want to get that exception back to the caller
;; it should prevent the single thread from dying.
;; See CMR-1392
(deftest exception-in-lookup-test
  (let [cache (l/start (slc/create-single-thread-lookup-cache) nil)]
    (try
      (testing "lookup value as normal"
        (is (= "normal value" (c/get-value cache :normal (constantly "normal value"))))

        ;; It's cached now
        (is (= "normal value" (c/get-value cache :normal))))

      (testing "lookup function that throws exception is passed to the user"

        (is (thrown-with-msg?
              Exception #"failure"
              ;; Timeout is used here in the case that this doesn't work we don't want it
              ;; causing tests to hang.
              (clojail/thunk-timeout #(c/get-value cache :fail fail-lookup-fn) 1000)))

        ;; test nothing was cached
        (is (nil? (c/get-value cache :fail))))

      (testing "after failure other values are still cached"
        (is (= "normal value" (c/get-value cache :normal))))

      (testing "after failure single thread is still running"
        (is (= "normal value2" (c/get-value cache :normal2 (constantly "normal value2")))))

      (finally
        (l/stop cache nil)))))

(deftest lookup-function-returns-nil-test
  (let [cache (l/start (slc/create-single-thread-lookup-cache) nil)]
    (try
      (testing "nil lookup value is still returned"
        (is (nil? (c/get-value cache :a-key (constantly nil)))))

      (testing "After returning nil value cache still functions as normal"
        (is (= "normal value" (c/get-value cache :normal (constantly "normal value"))))
        ;; It's cached now
        (is (= "normal value" (c/get-value cache :normal))))
      (finally
        (l/stop cache nil)))))

(deftest concurrent-request-test
  (let [cache (l/start (slc/create-single-thread-lookup-cache) nil)]
    (try
      (let [counter (atom 0)
            slow-lookup-fn (fn []
                             (Thread/sleep 200)
                             (swap! counter inc))]
        (testing "concurrent request for the same key will only invoke lookup once"
          (let [futures (for [n (range 10)]
                          (future (c/get-value cache :foo slow-lookup-fn)))]
            ;; The lookup function should only be called once and every request ends up with the same value
            (is (= #{1} (set (map deref futures))))
            ;; the lookup function was only called once.
            (is (= 1 (deref counter)))))

        (testing "subsequent requests for the same key will get the cached value"
          (is (= 1 (c/get-value cache :foo fail-lookup-fn)))
          (is (= 1 (deref counter))))

        (testing "a different key will cause a lookup"
          (is (= 2 (c/get-value cache :bar slow-lookup-fn)))
          (is (= 2 (deref counter)))))
      (finally
        (l/stop cache nil)))))