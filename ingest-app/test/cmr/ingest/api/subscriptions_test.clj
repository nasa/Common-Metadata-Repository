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
               {:EndPoint "", :Method "search"}

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
               {:EndPoint "", :Method "ingest"})))

(deftest check-subscription-for-collection-not-already-exist-test
  (let [fun #'cmr.ingest.api.subscriptions/check-endpoint-queue-for-collection-not-already-exist
        context nil]
    (util/are3 [subscription-concept]
               (is (= nil (fun context subscription-concept)))

               "subscription concept not ingest type -- does nothing"
               {:EndPoint "", :Method "search"}

               "subscription concept not sqs arn nor local queue arn -- does nothing"
               {:EndPoint "http://www.something.com", :Method "ingest"})

    (let [subscription-concept {:EndPoint "arn:aws:sqs:blahblah" :Method "ingest" :CollectionConceptId "C123-PROV1"}
          returned-cache-content {:Mode {:Delete ["sqs1" "sqs2"], :New ["url1"], :Update ["url1"]}}
          returned-cache-content-with-duplicates {:Mode {:Delete ["sqs1" "sqs2"], :New ["url1" "arn:aws:sqs:blahblah"], :Update ["url1"]}}]

      ;; method for getting cache-content returns error -- this method should bubble up that error
      (with-redefs [cmr.transmit.metadata-db2/get-subscription-cache-content (fn [context collection-concept-id] (throw (Exception. "Exception was thrown from cache-content func")))]
        (is (thrown? Exception (fun context subscription-concept))))

      ;; returns nil cache-content -- does nothing
      (with-redefs [cmr.transmit.metadata-db2/get-subscription-cache-content (fn [context collection-concept-id] nil)]
        (is (nil? (fun context subscription-concept))))

      ;; duplication collection to sqs queue not found -- does nothing
      (with-redefs [cmr.transmit.metadata-db2/get-subscription-cache-content (fn [context collection-concept-id] returned-cache-content)]
        (is (nil? (fun context subscription-concept))))

      ;; duplicate collection to sqs queue found -- throws error
      (with-redefs [cmr.transmit.metadata-db2/get-subscription-cache-content (fn [context collection-concept-id] returned-cache-content-with-duplicates)]
        (is (thrown? Exception (fun context subscription-concept))))
      )))
