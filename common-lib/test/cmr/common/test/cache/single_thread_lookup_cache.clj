(ns cmr.common.test.cache.single-thread-lookup-cache
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as c]
            [cmr.common.cache.single-thread-lookup-cache :as slc]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.common.lifecycle :as l]))

(deftest single-thread-lookup-cache-functions-as-cache-test
  (let [cache (l/start (slc/create-single-thread-lookup-cache) nil)]
    (try
      (cache-spec/assert-cache cache)
      (finally
        (l/stop cache nil)))))

(defn fail-lookup-fn
  "A lookup function that will throw an exception"
  []
  (throw (Exception. "fail")))

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
            ;; The lookup function should only be called once and every request ended up with the same value
            (is (= #{1} (set (map deref futures))))
            ;; the lookup function was only called once.
            (is (= 1 (deref counter)))))

        (testing "subsequent requests for the same key will get the cached value"
          (is (= 1 (c/get-value cache :foo fail-lookup-fn))))

        (testing "a different key will cause a lookup"
          (is (= 2 (c/get-value cache :bar slow-lookup-fn)))))
      (finally
        (l/stop cache nil)))))