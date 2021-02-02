(ns cmr.ingest.api.subscriptions-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.subscriptions :as subscriptions]
   [cmr.transmit.metadata-db :as mdb]
   [ring.mock.request :as mock]))

(deftest create-subscription-test
  (testing "with native-id not provided generates a native-id"
    (let [request (-> (mock/request :post
                                    "/PROV1/subscriptions"
                                    {:content-type "application/vnd.nasa.cmr.umm+json;version=1.0"})
                      (mock/json-body {:Name "no native id"
                                       :SubscriberId "post-user"
                                       :EmailAddress "someone@speedy.com"
                                       :CollectionConceptId "C1200000018-PROV1"
                                       :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"}))]
      (with-redefs-fn {#'subscriptions/common-ingest-checks (constantly nil)
                       #'mdb/find-concepts (constantly [])
                       #'subscriptions/check-subscription-ingest-permission (constantly nil)
                       #'subscriptions/perform-subscription-ingest
                       (fn [_context concept _headers]
                         (is (string/starts-with? (:native-id concept) "no_native_id")))}
        #(subscriptions/create-subscription "PROV1" request)))))

(deftest create-subscription-with-native-id-test
  (let [request (-> (mock/request :post
                                  "/PROV1/subscriptions/given-native-id"
                                  {:content-type "application/vnd.nasa.cmr.umm+json;version=1.0"})
                    (mock/json-body {:Name "with native id"
                                     :SubscriberId "post-user"
                                     :EmailAddress "someone@speedy.com"
                                     :CollectionConceptId "C1200000019-PROV1"
                                     :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"}))]
    (testing "with native-id provided uses the native-id"
      (with-redefs-fn {#'subscriptions/common-ingest-checks (constantly nil)
                       #'mdb/find-concepts (constantly [])
                       #'subscriptions/perform-subscription-ingest (constantly nil)
                       #'subscriptions/check-subscription-ingest-permission
                       (fn [_context concept _headers]
                         (is (= "given-native-id" (:native-id concept))))}
        #(subscriptions/create-subscription-with-native-id "test-provider" "given-native-id" request)))

    (testing "create with a conflicting native-id is rejected"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"already exists"
           (with-redefs-fn {#'mdb/find-concepts (constantly [{:native-id "collides"}])}
             #(subscriptions/create-subscription-with-native-id "test-provider" "collides" request)))))))

(deftest create-or-update-subscription-test
  (let [request (-> (mock/request :put
                                  "/PROV1/subscriptions/given-native-id"
                                  {:content-type "application/vnd.nasa.cmr.umm+json;version=1.0"})
                    (mock/json-body {:Name "subscription to update"
                                     :SubscriberId "post-user"
                                     :EmailAddress "someone@speedy.com"
                                     :CollectionConceptId "C1200000019-PROV1"
                                     :Query "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"}))]
    (testing "with existing native-id sends it to the database"
      (with-redefs-fn {#'subscriptions/common-ingest-checks (constantly nil)
                       #'mdb/find-concepts (constantly [{:native-id "existing-id"}])
                       #'subscriptions/check-subscription-ingest-permission (constantly nil)
                       #'subscriptions/perform-subscription-ingest
                       (fn [_context concept _headers]
                         (is (not (nil? concept))))}

        #(subscriptions/create-or-update-subscription-with-native-id "test-provider" "existing-id" request)))

    (testing "with native-id not found throws an exception"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"No such subscription"
           (with-redefs-fn {#'mdb/find-concepts (constantly [])                            }
             #(subscriptions/create-or-update-subscription-with-native-id "test-provider" "existing-id" request)))))))

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

(deftest get-unique-native-id-test
  (let [concept {:metadata
                 "{\"Name\":\"collision_test\",\"SubscriberId\":\"post-user\",\"EmailAddress\":\"someEmail@gmail.com\",\"CollectionConceptId\":\"C1200000018-PROV1\",\"Query\":\"polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78\"}"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.0"
                 :native-id nil
                 :concept-type :subscription
                 :provider-id "PROV1"}
        retry-attempts 2
        retry-count (atom 0)]

    (testing "will retry if a collision is detected"
      (with-redefs-fn {#'mdb/find-concepts (fn [context _concept _concept-type]
                                             (if (> retry-attempts @retry-count)
                                               [{:native-id (format "colliding-concept-%d"
                                                                    (swap! retry-count inc))}]
                                               []))}
        #(do (is (string/starts-with? (subscriptions/get-unique-native-id nil concept) "collision_test"))
             (is (= retry-attempts @retry-count)))))))
