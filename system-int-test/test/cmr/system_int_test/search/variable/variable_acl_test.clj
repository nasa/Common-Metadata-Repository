(ns cmr.system-int-test.search.variable.variable-acl-test
  "This tests the CMR Search API's variable association ACL permissions"
  (:require
   [clojure.test :refer :all]
   [clojure.string :as string]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.variable-util :as variable-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest variable-acl-test
  (let [guest-token (echo-util/login-guest (s/context))
        reg-user-group (echo-util/get-or-create-group (s/context) "some-group-guid")
        update-group (echo-util/get-or-create-group (s/context) "update-group")
        all-group (echo-util/get-or-create-group (s/context) "all-group")
        reg-user-token (echo-util/login (s/context) "user1" [reg-user-group])
        update-user (echo-util/login (s/context) "update-user" [update-group])
        all-user (echo-util/login (s/context) "all-user" [all-group])
        index (atom 0)
        uniq-variable (fn []
                        (swap! index inc)
                        (variable-util/make-variable @index {}))

        _ (echo-util/grant-group-provider-admin (s/context) update-group "provguid1")
        _ (echo-util/grant-group-provider-admin (s/context) all-group "provguid1")
        c1 (:concept-id
            (d/ingest-umm-spec-collection "PROV1"
                                          (data-umm-c/collection
                                           {:EntryTitle "C1" :ShortName "S1"})))]

    (testing "Create permissions, actually checks on Update permission"
      (testing "Success"
        (is (= 201 (:status (variable-util/create-variable update-user (uniq-variable)))))
        (is (= 201 (:status (variable-util/create-variable all-user (uniq-variable))))))

      (testing "Failure cases"
        (are
          [token]
          (= {:status 401
              :errors ["You do not have permission to update a variable."]}
             (variable-util/create-variable token (uniq-variable)))

          nil
          guest-token
          reg-user-token)))

    (testing "Associate with Collections permissions"
      (let [variable (uniq-variable)
            variable-name (string/lower-case (:Name variable))
            {:keys [concept-id revision-id]} (variable-util/create-variable all-user variable)]
        (testing "Success"
          (is (= 200 (:status (variable-util/associate-by-concept-ids
                               update-user variable-name [{:concept-id c1}]))))
          (is (= 200 (:status (variable-util/associate-by-concept-ids
                               all-user variable-name [{:concept-id c1}])))))

        (testing "Failure Cases"
          (are
            [token]
            (= {:status 401
                :errors ["You do not have permission to update a variable."]}
               (variable-util/associate-by-concept-ids
                token variable-name [{:concept-id c1}]))

            nil
            guest-token
            reg-user-token))))

    (testing "Dissociate with Collections permissions"
      (let [variable (uniq-variable)
            variable-name (string/lower-case (:Name variable))
            {:keys [concept-id revision-id]} (variable-util/create-variable all-user variable)]
        (testing "Success"
          (is (= 200 (:status (variable-util/dissociate-by-concept-ids
                               update-user variable-name [{:concept-id c1}]))))
          (is (= 200 (:status (variable-util/dissociate-by-concept-ids
                               all-user variable-name [{:concept-id c1}])))))

        (testing "Failure Cases"
          (are
            [token]
            (= {:status 401
                :errors ["You do not have permission to update a variable."]}
               (variable-util/dissociate-by-concept-ids
                token variable-name [{:concept-id c1}]))

            nil
            guest-token
            reg-user-token))))))
