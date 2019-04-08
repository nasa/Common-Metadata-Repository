(ns cmr.common.test.api.errors
  "This tests capabilities of the API error utilities."
  (:require
   [clojure.test :refer :all]
   [cmr.common.api.errors :as errors]))

(deftest test-error-masked
  (let [error-message "Token 123 does not exist"]
    (is (= "Token does not exist" (errors/mask-token-error error-message)))))
