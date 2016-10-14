(ns cmr.common.test.cache.deflating-cache
  "Unit tests for the deflating cache."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [cmr.common.cache :as cache]
   [cmr.common.cache.cache-spec :as cache-spec]
   [cmr.common.cache.deflating-cache :as dc]
   [cmr.common.cache.in-memory-cache :as mem-cache]))

(deftest deflating-cache-functions-as-cache-test
  (cache-spec/assert-cache (dc/create-deflating-cache
                            ;; Delegate cache
                            (mem-cache/create-in-memory-cache)
                            ;; deflate function
                            edn/read-string
                            ;; inflate function
                            pr-str)))

(deftest deflating-cache-test
  (let [cache (dc/create-deflating-cache
                ;; Delegate cache
                (mem-cache/create-in-memory-cache)
                ;; deflate function
                csk/->snake_case_string
                ;; inflate function
                csk/->kebab-case-string)]

    (testing "Inflate function is called when retrieving a non-nil value from the cache"
      (cache/set-value cache :snake-test "theSnake")
      (is (= "the_snake" (cache/get-value cache :snake-test))))

    (testing "When the value retrieved from the cache is nil, the inflate function is not called"
      ;; Would throw an exception if inflate is called
      (is (= nil (cache/get-value cache :no-such-key))))

    (testing (str "The inflate function is called when the value retrieved from the cache is nil"
                  "and a lookup function is provided.")
      (is (= "the_snake" (cache/get-value cache :snake-test (constantly "theSnake")))))

    (testing "When setting a nil value, the deflate function is not called and get-value returns nil"
      ;; Would throw an exception if deflate is called
      (cache/set-value cache :snake-test nil)
      (is (= nil (cache/get-value cache :snake-test))))))
