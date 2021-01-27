(ns cmr.ingest.api.subscriptions-test
  (:require
   [clojure.test :refer :all]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.subscriptions :as subscriptions]))

(deftest ingest-subscription-test
  (testing "creating a subscription"
    (let [subscription {}]
      (testing "with native-id provided uses the native-id"
        (with-redefs-fn {#'subscriptions/perform-subscription-ingest (constantly nil)
                         #'subscriptions/common-ingest-checks (constantly nil)
                         #'api-core/body->concept! (fn [_concept-type _provider-id native-id _body _content-type _headers]
                                                     (is (= "given-native-id" native-id)))
                         #'subscriptions/check-subscription-ingest-permission (constantly nil)}
          #(subscriptions/ingest-subscription "test-provider" "given-native-id" subscription)))

      (testing "with native-id not provided generates a native-id"
        (with-redefs-fn {#'subscriptions/perform-subscription-ingest (constantly nil)
                         #'subscriptions/common-ingest-checks (constantly nil)
                         #'api-core/body->concept! (fn [_concept-type _provider-id native-id _body _content-type _headers]
                                                     (printf "generated native-id [%s]%n" native-id)
                                                     (is (string? native-id))
                                                     (is (not (nil? native-id))))
                         #'subscriptions/check-subscription-ingest-permission (constantly nil)}
          #(subscriptions/ingest-subscription "test-provider" nil subscription))))))
