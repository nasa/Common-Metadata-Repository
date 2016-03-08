(ns cmr.system-int-test.search.tagging.tag-acl-test
  "This tests the CMR Search API's tagging capabilities"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :refer [are2]]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest tag-acl-test
  (e/grant-group-tag (s/context) "create-group" :create)
  (e/grant-group-tag (s/context) "update-group" :update)
  (e/grant-group-tag (s/context) "delete-group" :delete)
  (e/grant-group-tag (s/context) "all-group")

  (let [guest-token (e/login-guest (s/context))
        reg-user-token (e/login (s/context) "user1" ["some-group-guid"])
        create-user (e/login (s/context) "create-user" ["create-group"])
        update-user (e/login (s/context) "update-user" ["update-group"])
        delete-user (e/login (s/context) "delete-user" ["delete-group"])
        all-user (e/login (s/context) "all-user" ["all-group"])
        tag-key (atom 0)
        uniq-tag #(tags/make-tag {:tag-key (str (swap! tag-key inc))})]

    (testing "Create permissions"
      (testing "Success"
        (is (= 200 (:status (tags/create-tag create-user (uniq-tag)))))
        (is (= 200 (:status (tags/create-tag all-user (uniq-tag))))))

      (testing "Failure cases"
        (are
          [token]
          (= {:status 401
              :errors ["You do not have permission to create a tag."]}
             (tags/create-tag token (uniq-tag)))

          guest-token
          reg-user-token
          update-user
          delete-user)))

    (testing "Update permissions"
      (let [tag (uniq-tag)
            {:keys [concept-id revision-id]} (tags/create-tag all-user tag)]
        (testing "Success"
          (is (= 200 (:status (tags/update-tag update-user concept-id tag))))
          (is (= 200 (:status (tags/update-tag all-user concept-id tag)))))

        (testing "Failure Cases"
          (are
            [token]
            (= {:status 401
                :errors ["You do not have permission to update a tag."]}
               (tags/update-tag token concept-id (uniq-tag)))

            guest-token
            reg-user-token
            create-user
            delete-user))))

    (testing "Delete permissions"
      (testing "Success"
        (are [token]
             (= 200 (->> (uniq-tag)
                         (tags/create-tag all-user)
                         :concept-id
                         (tags/delete-tag token)
                         :status))
             delete-user
             all-user))

      (testing "Failure Cases"
        (are
          [token]
          (= {:status 401
              :errors ["You do not have permission to delete a tag."]}
             (->> (uniq-tag)
                  (tags/create-tag all-user)
                  :concept-id
                  (tags/delete-tag token)))

          guest-token
          reg-user-token
          create-user
          update-user)))

    (testing "Associate with Collections permissions"
      (let [tag (uniq-tag)
            {:keys [concept-id revision-id]} (tags/create-tag all-user tag)]
        (testing "Success"
          (is (= 200 (:status (tags/associate-by-query update-user concept-id {:provider "foo"}))))
          (is (= 200 (:status (tags/associate-by-query all-user concept-id {:provider "foo"})))))

        (testing "Failure Cases"
          (are
            [token]
            (= {:status 401
                :errors ["You do not have permission to update a tag."]}
               (tags/associate-by-query token concept-id {:provider "foo"}))

            nil
            guest-token
            reg-user-token
            create-user
            delete-user))))

    (testing "Dissociate with Collections permissions"
      (let [tag (uniq-tag)
            {:keys [concept-id revision-id]} (tags/create-tag all-user tag)]
        (testing "Success"
          (is (= 200 (:status (tags/disassociate-by-query update-user concept-id {:provider "foo"}))))
          (is (= 200 (:status (tags/disassociate-by-query all-user concept-id {:provider "foo"})))))

        (testing "Failure Cases"
          (are
            [token]
            (= {:status 401
                :errors ["You do not have permission to update a tag."]}
               (tags/disassociate-by-query token concept-id {:provider "foo"}))

            nil
            guest-token
            reg-user-token
            create-user
            delete-user))))))
