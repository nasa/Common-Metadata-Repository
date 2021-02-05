(ns cmr.ingest.api.subscriptions-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.subscriptions :as subscriptions]))

(defn- concept->query
  "extract just the Query out of a result from process-result->hash"
  [result]
  (let  [meta-raw (:metadata result)
         meta-json (json/parse-string meta-raw)]
    (get meta-json "Query")))

(defn- create-concept
  "Helper method to create a basic subscription concept"
  [query]
  {:metadata (str "{\"Name\":\"the beginning\","
                  "\"SubscriberId\":\"post-user\","
                  "\"EmailAddress\":\"someEmail@gmail.com\","
                  "\"CollectionConceptId\":\"C1200000018-PROV1\","
                  "\"Query\":\"" query "\"}")
   :format "application/vnd.nasa.cmr.umm+json;version=1.0"
   :native-id nil
   :concept-type :subscription
   :provider-id "PROV1"})

(deftest ingest-subscription-test
  (testing "creating a subscription"
    (let [subscription {}]
      (testing "with native-id provided uses the native-id"
        (with-redefs-fn {#'subscriptions/perform-subscription-ingest (constantly nil)
                         #'subscriptions/common-ingest-checks (constantly nil)
                         #'api-core/body->concept! (constantly {:native-id "tmp"
                                                                :metadata " {\"Name\": \"some name\"}"})
                         #'subscriptions/check-subscription-ingest-permission (fn [request-context concept provider-id]
                                                                                (is (= "given-native-id" (:native-id concept))))}
                        #(subscriptions/ingest-subscription "test-provider" "given-native-id" subscription)))

      (testing "with native-id not provided generates a native-id"
        (with-redefs-fn {#'subscriptions/perform-subscription-ingest (constantly nil)
                         #'subscriptions/common-ingest-checks (constantly nil)
                         #'api-core/body->concept! (constantly {:native-id "tmp"
                                                                :metadata " {\"Name\": \"some name\"}"})
                         #'subscriptions/check-subscription-ingest-permission
                         (fn [request-context concept provider-id]
                           (is (string/starts-with? (:native-id concept) "some_name")))}
                        #(subscriptions/ingest-subscription "test-provider" nil subscription))))))

(deftest generate-native-id-test
  (let [concept (create-concept "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78")
        native-id (subscriptions/generate-native-id concept)]
    (is (string? native-id))

    (testing "name is used as the prefix"
      (is (string/starts-with? native-id "the_beginning")))))

(deftest encoded-query-test
  "Queries can be URL encoded and ingest needs to be able to properly decode them"
  (testing
   "Test that a common parameter is not broken"
    (let [expected "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"
          concept-raw (create-concept expected)
          concept-clean (#'subscriptions/decode-query-in-concept concept-raw)
          query (concept->query concept-clean)]
      (is (= expected query))))

  (testing
    "Test that a complicated query will be decoded correctly."
    (let [expected "provider=PROV1&options[spatial][or]=true"]
      (testing
        "Test that a complicated unencoded query is used."
       (let [concept-raw (create-concept expected)
             concept-clean (#'subscriptions/decode-query-in-concept concept-raw)
             query (concept->query concept-clean)]
         (is (= expected query))))

      (testing
       "Test that a complicated encoded query is fixed"
        (let [provided "provider=PROV1&options%5bspatial%5d%5bor%5d=true"
              concept-raw (create-concept provided)
              concept-clean (#'subscriptions/decode-query-in-concept concept-raw)
              query (concept->query concept-clean)]
          (is (= expected query)))))))
