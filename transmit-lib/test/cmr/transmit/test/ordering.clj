(ns cmr.transmit.test.ordering
  "Contains unit tests for verifying ordering notifications."
  (:require [clojure.test :refer [deftest is testing]]
            [cmr.common.util :refer [are3]]
            [cmr.transmit.ordering :as ordering]))

(deftest just-token-test
  (testing "Test token prep function to ensure graphql gets the correct form"
    (are3
     [expected provided]
     (do (let [context {:token provided}]
           (is (= expected (#'cmr.transmit.ordering/context->just-token context)))))
     "No bearer" "1234" "1234"
     "Upper case bearer" "1234" "Bearer:1234"
     "Lower case bearer" "1234" "bearer:1234"
     "Spaces around bearer" "1234" " bearer: 1234"
     "nothing" "" ""
     "bearer but no token" "" " Bearer: ")))
