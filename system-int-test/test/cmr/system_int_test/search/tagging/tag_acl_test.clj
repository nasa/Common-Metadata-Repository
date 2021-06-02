(ns cmr.system-int-test.search.tagging.tag-acl-test
  "This tests the CMR Search API's tagging capabilities"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are2]]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.system-int-test.utils.tag-util :as tags]
    [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest tag-acl-test
  (let [guest-token (e/login-guest (s/context))
        system-token transmit-config/mock-echo-system-token
        reg-user-group (e/get-or-create-group (s/context) "some-group-guid")
        create-group (e/get-or-create-group (s/context) "create-group")
        update-group (e/get-or-create-group (s/context) "update-group")
        delete-group (e/get-or-create-group (s/context) "delete-group")
        all-group (e/get-or-create-group (s/context) "all-group")
        reg-user-token (e/login (s/context) "user1" [reg-user-group])
        create-user (e/login (s/context) "create-user" [create-group])
        update-user (e/login (s/context) "update-user" [update-group])
        delete-user (e/login (s/context) "delete-user" [delete-group])
        all-user (e/login (s/context) "all-user" [all-group])
        tag-key (atom 0)
        uniq-tag #(tags/make-tag {:tag-key (str (swap! tag-key inc))})]

    (e/grant-group-tag (s/context) create-group :create)
    (e/grant-group-tag (s/context) update-group :update)
    (e/grant-group-tag (s/context) delete-group :delete)
    (e/grant-group-tag (s/context) all-group)

    (testing "Create permissions"
      (testing "Success"
        (are
          [token]
          (= 201 (:status (tags/create-tag token (uniq-tag))))

          system-token
          create-user
          all-user))

      (testing "Failure cases"
        (are
          [token]
          (= {:status 401
              :errors ["You do not have permission to create a tag."]}
             (tags/create-tag token (uniq-tag)))

          nil
          guest-token
          reg-user-token
          update-user
          delete-user)))

    (testing "Update permissions"
      (let [tag (uniq-tag)
            {:keys [concept-id revision-id]} (tags/create-tag all-user tag)]
        (testing "Success"
          (are
            [token]
            (= 200 (:status (tags/update-tag token tag)))

            system-token
            update-user
            all-user))

        (testing "Failure Cases"
          (are
            [token]
            (= {:status 401
                :errors ["You do not have permission to update a tag."]}
               (tags/update-tag token (uniq-tag)))

            nil
            guest-token
            reg-user-token
            create-user
            delete-user))))

    (testing "Delete permissions"
      (testing "Success"
        (are
          [token]
          (let [tag (uniq-tag)
                _ (tags/create-tag all-user tag)
                {:keys [status]} (tags/delete-tag token (:tag-key tag))]
            (= 200 status))

          system-token
          delete-user
          all-user))

      (testing "Failure Cases"
        (are
          [token]
          (let [tag (uniq-tag)
                _ (tags/create-tag all-user tag)]
            (= {:status 401
                :errors ["You do not have permission to delete a tag."]}
               (tags/delete-tag token (:tag-key tag))))

          nil
          guest-token
          reg-user-token
          create-user
          update-user)))

    (testing "Associate with Collections permissions"
      (let [tag (uniq-tag)
            {:keys [concept-id revision-id]} (tags/create-tag all-user tag)]
        (testing "Success"
          (are
            [token]
            (= 200 (:status (tags/associate-by-query token (:tag-key tag) {:provider "foo"})))

            system-token
            update-user
            all-user))

        (testing "Failure Cases"
          (are
            [token]
            (= {:status 401
                :errors ["You do not have permission to update a tag."]}
               (tags/associate-by-query token (:tag-key tag) {:provider "foo"}))

            nil
            guest-token
            reg-user-token
            create-user
            delete-user))))

    (testing "Dissociate with Collections permissions"
      (let [tag (uniq-tag)
            {:keys [concept-id revision-id]} (tags/create-tag all-user tag)]
        (testing "Success"
          (are
            [token]
            (= 200 (:status (tags/dissociate-by-query token (:tag-key tag) {:provider "foo"})))

            system-token
            update-user
            all-user))

        (testing "Failure Cases"
          (are
            [token]
            (= {:status 401
                :errors ["You do not have permission to update a tag."]}
               (tags/dissociate-by-query token (:tag-key tag) {:provider "foo"}))

            nil
            guest-token
            reg-user-token
            create-user
            delete-user))))))
