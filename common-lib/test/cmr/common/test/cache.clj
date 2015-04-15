(ns cmr.common.test.cache
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as c]))

(def counter (atom 0))

(defn increment-counter
  "Increments the counter atom and returns it"
  []
  (swap! counter inc))

(deftest cache-test
  (testing "cache hit, miss and reset"
    (let [cache-atom (c/create-in-memory-cache)]
      (reset! counter 0)
      (is (= 1 (c/get-value cache-atom "key" increment-counter)))
      ;; look up again will not call the increment-counter function
      (is (= 1 (c/get-value cache-atom "key" increment-counter)))
      (c/reset cache-atom)
      (is (= 2 (c/get-value cache-atom "key" increment-counter)))
      (is (= 2 (c/get-value cache-atom "key" increment-counter))))))

(deftest update-cache-test
  (testing "update cache"
    (let [cache-atom (c/create-in-memory-cache)]
      (reset! counter 0)
      (c/get-value cache-atom "key" increment-counter)
      (is (= 1 (c/get-value cache-atom "key" increment-counter)))
      (c/set-value cache-atom "key" (increment-counter))
      (is (= 2 (c/get-value cache-atom "key" increment-counter))))))

