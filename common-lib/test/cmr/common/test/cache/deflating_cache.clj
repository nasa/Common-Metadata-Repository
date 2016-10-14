(ns cmr.common.test.cache.deflating-cache
  "Unit tests for the deflating cache."
  (:require [clojure.test :refer :all]
            [clojure.edn :as edn]
            [cmr.common.cache.deflating-cache :as dc]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache.cache-spec :as cache-spec]))

(deftest deflating-cache-functions-as-cache-test
  (cache-spec/assert-cache (dc/create-deflating-cache
                            ;; Delegate cache
                            (mem-cache/create-in-memory-cache)
                            ;; deflate function
                            edn/read-string
                            ;; inflate function
                            pr-str)))



