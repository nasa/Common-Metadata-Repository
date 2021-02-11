(ns cmr.ingest.api.subscriptions-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.subscriptions :as subscriptions]))

(deftest generate-native-id-test
  (let [concept {:metadata
                 "{\"Name\":\"the beginning\",\"SubscriberId\":\"post-user\",\"EmailAddress\":\"someEmail@gmail.com\",\"CollectionConceptId\":\"C1200000018-PROV1\",\"Query\":\"polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78\"}"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.0"
                 :native-id nil
                 :concept-type :subscription
                 :provider-id "PROV1"}
        native-id (subscriptions/generate-native-id concept)]
    (is (string? native-id))

    (testing "name is used as the prefix"
      (is (string/starts-with? native-id "the_beginning")))))
