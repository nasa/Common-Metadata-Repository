(ns cmr.common.test.api.errors
  "This tests capabilities of the API error utilities."
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.api.errors :as errors]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :refer [are3]]))

(deftest test-error-masked
  (let [error-message "Token 123 does not exist"]
    (is (= "Token does not exist" (errors/mask-token-error error-message)))))

(deftest test-errors->body

  (testing "test supported error message types"
    (are3
     [body-type error-message expected]

     (is (= expected (errors/errors->body-string body-type [error-message])))

     "json version, blank message"
     mt/json ""
     "{\"errors\":[\"\"]}"

     "json version"
     mt/json "error goes here"
     "{\"errors\":[\"error goes here\"]}"

     "xml version, blank message"
     mt/xml ""
     "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error></error></errors>"

     "xml version"
     mt/xml "error goes here"
     "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>error goes here</error></errors>")))
