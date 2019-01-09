(ns cmr.opendap.tests.unit.const
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.const :as const]))

(deftest user-agent
  (is (= "CMR Service-Bridge Service/1.0 (+https://github.com/cmr-exchange/cmr-service-bridge)"
         const/user-agent)))
