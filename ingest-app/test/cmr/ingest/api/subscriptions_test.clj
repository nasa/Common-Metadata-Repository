(ns cmr.ingest.api.subscriptions-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
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
