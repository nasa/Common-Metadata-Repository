(ns cmr.transmit.test.echo.rest
  "Tests for cmr.transmit.echo.rest namespace"
  (:require [clojure.test :refer :all]
            [cmr.transmit.echo.rest :as rest]))

(deftest test-error-masked
  (let [error-message "Unexpected error message: Token 123 does not exist."
        status 500
        replacement-message "Token does not exist"]
    (is (thrown-with-msg?
        java.lang.Exception
        (re-pattern (format "Unexpected status %d from response. body: %s" status replacement-message))
        (rest/unexpected-status-error! status error-message)))))
