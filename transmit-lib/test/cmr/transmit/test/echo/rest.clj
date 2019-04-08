(ns cmr.transmit.test.echo.rest
  "Tests for cmr.transmit.echo.rest namespace"
  (:require [clojure.test :refer :all]
            [cmr.transmit.echo.rest :as rest]))

(deftest test-error-masked
  (let [error-message "Unexpected error message: Token 123 does not exist."
        status 500]
    (is (thrown-with-msg?
        java.lang.Exception
        ;; Fixme - (format "%d %s" status message) was getting the error:
        ;; java.lang.ClassCastException: java.lang.String cannot be cast to java.util.regex.Pattern
        #"Unexpected status 500 from response. body: Token does not exist"
        (rest/unexpected-status-error! status error-message)))))
