(ns cmr.common.cache.spec-util
  "Defines utility functions used from within multiple cache specs."
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as c]))

(defn put-values-in-caches
  "Puts the key value pairs in the val map into the caches"
  [caches val-map]
  (doseq [cache caches
          [k v] val-map]
    (c/set-value cache k v)))

(defn assert-values-in-caches
  "Asserts that all of the values in the value map are in the caches."
  [caches val-map]
  (doseq [cache caches
          [k v] val-map]
    (is (= v (c/get-value cache k)))))

(defn assert-cache-keys
  "Asserts that the expected keys are in the cache."
  ([expected-keys cache]
   (assert-cache-keys expected-keys cache false))
  ([expected-keys cache extra-allowed?]
   (if extra-allowed?
     (let [actual-keys (set (c/get-keys cache))]
       (is (every? actual-keys expected-keys)))
     (is (= (set expected-keys) (set (c/get-keys cache)))))))

