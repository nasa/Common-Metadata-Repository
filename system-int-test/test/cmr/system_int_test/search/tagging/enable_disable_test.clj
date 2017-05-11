(ns cmr.system-int-test.search.tagging.enable-disable-test
  "Search tag / tag association peristence enable/disable endpoint test"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are2] :as util]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.system-int-test.utils.tag-util :as tags]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.system-int-test.system :as s]
    [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       tags/grant-all-tag-fixture]))

(deftest tag-crud-test
  (let [tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        tag2-key "tag2"
        tag2 (tags/make-tag {:tag-key tag2-key})
        token (e/login (s/context) "user1")
        tag3-key "tag3"
        tag3 (tags/make-tag {:tag-key tag3-key})]

    (testing "Successful creation before disable"
      (let [{:keys [status concept-id revision-id]} (tags/create-tag token tag)]
        (is (= 201 status)))
      ;; create a second tag so we can use it to test updating/deleting after disable
      (let [{:keys [status concept-id revision-id]} (tags/create-tag token tag2)]
        (is (= 201 status))))

    (testing "Update tag before disable"
        (let [updated-tag (-> tag
                              (update-in [:description] #(str % " updated"))
                              (assoc :originator-id "user1"))
              token2 (e/login (s/context) "user2")
              response (tags/update-tag token2 tag-key updated-tag)]
          (is (= 200 (:status response)))))

    (testing "Delete succeeds before disable"
      (is (= 200 (:status (tags/delete-tag token tag-key)))))

    ;; disable tag / tag association persistence
    (search/disable-writes {:headers {transmit-config/token-header (transmit-config/echo-system-token)}})

    (testing "Failed creation after disable"
      (let [tag-key "tag2"
            tag (tags/make-tag {:tag-key tag-key})
            token (e/login (s/context) "user1")
            {:keys [status concept-id revision-id]} (tags/create-tag token tag)]
        (is (= 503 status))))

    (testing "Update tag fails after disable"
        (let [updated-tag (-> tag2
                              (update-in [:description] #(str % " updated"))
                              (assoc :originator-id "user1"))
              token2 (e/login (s/context) "user2")
              response (tags/update-tag token2 tag2-key updated-tag)]
          (is (= 503 (:status response)))))

    (testing "Delete fails after disable"
      (is (= 503 (:status (tags/delete-tag token tag2-key)))))

    ;; re-enable tag / tag association persistence
    (search/enable-writes {:headers {transmit-config/token-header (transmit-config/echo-system-token)}})

    (testing "Successful creation after re-enable"
      (let [{:keys [status concept-id revision-id]} (tags/create-tag token tag3)]
        (is (= 201 status))))

    (testing "Update tag after re-enable"
        (let [updated-tag (-> tag3
                              (update-in [:description] #(str % " updated"))
                              (assoc :originator-id "user1"))
              token2 (e/login (s/context) "user2")
              response (tags/update-tag token2 tag3-key updated-tag)]
          (is (= 200 (:status response)))))

    (testing "Delete succeeds before disable"
      (is (= 200 (:status (tags/delete-tag token tag3-key)))))))

(deftest tag-association-test
  (let [tag-collection (ingest/ingest-concept (dc/collection-concept {:native-id "native1"
                                                                      :entry-title "coll1"
                                                                      :entry-id "coll1"
                                                                      :version-id "V1"
                                                                      :short-name "short1"}))
        tag1-key "tag1"
        tag1 (tags/make-tag {:tag-key tag1-key})
        tag2-key "tag2"
        tag2 (tags/make-tag {:tag-key tag2-key})
        token (e/login (s/context) "user1")
        _ (tags/create-tag token tag1)
        _ (tags/create-tag token tag2)]
    (testing "associate by query works before disable"
      (let [response (tags/associate-by-query token tag1-key {:provider "PROV1"})]
        (is (= 200 (:status response)))))

    (testing "associate tag to collection works before disable"
      (let [response (tags/associate-by-concept-ids
                      token tag1-key [{:concept-id (:concept-id tag-collection)}])]
        (is (= 200 (:status response)))))

    (testing "dissociate by query works before disable"
      (let [response (tags/dissociate-by-query token tag1-key {:provider "PROV1"})]
        (is (= 200 (:status response)))))

    (testing "dissociate tag with collection works before disable"
      (let [response (tags/dissociate-by-concept-ids
                               token
                               tag1-key
                               [{:concept-id (:concept-id tag-collection)}])]
        (is (= 200 (:status response)))))

    ;; disable tag / tag association persistence
    (search/disable-writes {:headers {transmit-config/token-header (transmit-config/echo-system-token)}})

    (testing "associate by query fails after disable"
      (let [response (tags/associate-by-query token tag2-key {:provider "PROV1"})]
        (is (= 503 (:status response)))))

    (testing "dissociate by query fails after disable"
      (let [response (tags/dissociate-by-query token tag1-key {:provider "PROV1"})]
        (is (= 503 (:status response)))))

    (testing "dissociate tag with collection fails after disable"
      (let [response (tags/dissociate-by-concept-ids
                               token
                               tag1-key
                               [{:concept-id (:concept-id tag-collection)}])]
        (is (= 503 (:status response)))))

    ;; re-enable tag / tag association persistence
    (search/enable-writes {:headers {transmit-config/token-header (transmit-config/echo-system-token)}})

    (testing "associate by query works after re-enable"
      (let [response (tags/associate-by-query token tag2-key {:provider "PROV1"})]
        (is (= 200 (:status response)))))

    (testing "dissociate by query works after re-enable"
      (let [response (tags/dissociate-by-query token tag2-key {:provider "PROV1"})]
        (is (= 200 (:status response)))))

    (testing "dissociate tag with collection works after re-enable"
      (let [response (tags/dissociate-by-concept-ids
                               token
                               tag2-key
                               [{:concept-id (:concept-id tag-collection)}])]
        (is (= 200 (:status response)))))))

