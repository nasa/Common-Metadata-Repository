(ns cmr.ous.tests.unit.const
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.ous.const :as const]))

(deftest user-agent
  (is (= "CMR Service-Bridge/1.0 (+https://github.com/cmr-exchange/cmr-ous-plugin)"
         const/user-agent)))
