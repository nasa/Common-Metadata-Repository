(ns cmr.access-control.int-test.access-control-group-acl-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.access-control.int-test.access-control-test-util :as u]))

(use-fixtures :once (u/int-test-fixtures))
(use-fixtures :each (u/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"}))

(deftest create-system-group-test

  (e/grant-system-group-permissions-to-group (u/conn-context) "group-create-group" :create)

  (testing "with permission"
    (let [group (u/make-group)
          token (e/login (u/conn-context) "user1" ["group-create-group"])
          {:keys [status concept-id revision-id]} (u/create-group token group)]
      (is (= 200 status))
      (is (some? concept-id))
      (is (= 1 revision-id))))

  (testing "without permission"
    (let [group (u/make-group)
          token (e/login (u/conn-context) "user2")
          response (u/create-group token group)]
      (is (= {:status 401
              :errors ["You do not have permission to create system-level access control groups."]}
             response)))))

(deftest create-provider-group-test

  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov1-group-create-group" "prov1guid" :create)

  (testing "with permission"
    (let [group (u/make-group {:provider-id "PROV1"})
          token (e/login (u/conn-context) "user1" ["prov1-group-create-group"])
          {:keys [status concept-id revision-id]} (u/create-group token group)]
      (is (= 200 status))
      (is (re-matches #"AG\d+-PROV1" concept-id) "Incorrect concept id for a provider group")
      (is (= 1 revision-id))))

  (testing "without permission"
    (let [token (e/login (u/conn-context) "user2")
          group (u/make-group {:provider-id "PROV1"})
          response (u/create-group token group)]
      (is (= {:status 401
              :errors ["You do not have permission to create access control groups for provider [PROV1]."]}
             response)))))

(deftest get-group-acl-test

  (e/grant-system-group-permissions-to-group (u/conn-context) "sys-group-readers" :read :create)
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov1-group-readers" "prov1guid" :read :create)

  (let [token (e/login (u/conn-context) "user1" ["sys-group-readers" "prov1-group-readers"])
        no-group-token (e/login (u/conn-context) "user2")
        system-group (u/make-group)
        system-group-concept-id (:concept-id (u/create-group token system-group))
        prov-group (u/make-group {:provider-id "PROV1"})
        prov-group-concept-id (:concept-id (u/create-group token prov-group))]

    (testing "reading system groups"
      (testing "with permission"
        (is (= (assoc system-group :status 200 :num-members 0)
               (u/get-group token system-group-concept-id))))

      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to read system-level access control groups."]}
               (u/get-group no-group-token system-group-concept-id)))))

    (testing "reading provider groups"
      (testing "with permission"
        (is (= (assoc prov-group :num-members 0 :status 200)
               (u/get-group token prov-group-concept-id))))

      (testing "without permission"
        (testing "without permission"
          (is (= {:status 401
                  :errors ["You do not have permission to read access control groups for provider [PROV1]."]}
                 (u/get-group no-group-token prov-group-concept-id))))))))
