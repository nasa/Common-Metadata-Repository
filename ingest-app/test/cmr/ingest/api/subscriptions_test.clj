(ns cmr.ingest.api.subscriptions-test
  (:require
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.common.util :as util]
    [cmr.ingest.api.subscriptions :as subscriptions]))

(deftest generate-native-id-test
  (let [parsed {:Name "the_beginning"
                :SubscriberId "someSubId"
                :CollectionConceptId "C123-PROV1"
                :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"}
        native-id (#'subscriptions/generate-native-id parsed)]
    (is (string? native-id))

    (testing "name is used as the prefix"
      (is (string/starts-with? native-id "the_beginning")))))

(deftest validate-subscription-endpoint-test
  (testing "validate subscription endpoint str -- expected valid"
    (util/are3 [subscription-concept]
               (let [fun #'cmr.ingest.api.subscriptions/validate-subscription-endpoint]
                 (is (= nil (fun subscription-concept))))

               "given method is search -- endpoint ignored"
               {:EndPoint "blahblah", :Method "search"}

               "given method is search and endpoint not given -- endpoint ignored"
               {:EndPoint "blahblah", :Method "search"}

               "given method is ingest and sqs arn is valid"
               {:EndPoint "arn:aws:sqs:us-east-1:000000000:Test-Queue", :Method "ingest"}

               "given method is ingest and url is valid"
               {:EndPoint "https://testwebsite.com", :Method "ingest"}))

  (testing "validate subscription endpoint str -- expected invalid"
    (util/are3 [subscription-concept]
               (let [fun #'cmr.ingest.api.subscriptions/validate-subscription-endpoint]
                 (is (thrown? Exception (fun subscription-concept))))

               "given method is ingest and sqs arn is invalid"
               {:EndPoint "iaminvalidendpoint", :Method "ingest"}

               "given method is ingest and endpoint is empty is invalid"
               {:Endpoint "", :Method "ingest"})))
