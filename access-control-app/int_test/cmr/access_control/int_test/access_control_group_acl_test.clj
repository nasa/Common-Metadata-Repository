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
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov2-group-creator" "prov2guid" :create)

  (let [token (e/login (u/conn-context) "user1" ["sys-group-readers" "prov1-group-readers"])
        no-group-token (e/login (u/conn-context) "user2")
        system-group (u/make-group)
        system-group-concept-id (:concept-id (u/create-group token system-group))
        prov-group (u/make-group {:provider-id "PROV1"})
        prov-group-concept-id (:concept-id (u/create-group token prov-group))
        prov2-group (u/make-group {:provider-id "PROV2"})
        prov2-creator-token (e/login (u/conn-context) "user3" ["prov2-group-creator"])
        prov2-group-concept-id (:concept-id (u/create-group prov2-creator-token prov2-group))]

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
        (is (= {:status 401
                :errors ["You do not have permission to read access control groups for provider [PROV1]."]}
               (u/get-group no-group-token prov-group-concept-id)))

        (is (= {:status 401
                :errors ["You do not have permission to read access control groups for provider [PROV2]."]}
               (u/get-group token prov2-group-concept-id)))))))

(deftest delete-group-acl-test

  (e/grant-system-group-permissions-to-group (u/conn-context) "sys-group-delete" :create)
  (e/grant-group-instance-permissions-to-group (u/conn-context) "sys-group-delete" "system-group-guid" :delete)
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov1-group-delete" "prov1guid" :create)
  (e/grant-group-instance-permissions-to-group (u/conn-context) "prov1-group-delete" "prov1-group-guid" :delete)
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov2-group-creator" "prov2guid" :create)
  (e/grant-group-instance-permissions-to-group (u/conn-context) "prov2-group-creator" "prov2-group-guid" :delete)

  (let [sys-token (e/login (u/conn-context) "user1" ["sys-group-delete"])
        prov-token (e/login (u/conn-context) "user2" ["prov1-group-delete"])

        sys-group (u/make-group {:legacy-guid "system-group-guid"})
        sys-group-id (:concept-id (u/create-group sys-token sys-group))

        prov-group (u/make-group {:provider-id "PROV1" :legacy-guid "prov1-group-guid"})
        prov-group-id (:concept-id (u/create-group prov-token prov-group))

        prov2-group-id (:concept-id (u/create-group (e/login (u/conn-context) "user3" ["prov2-group-creator"])
                                                    (u/make-group {:provider-id "PROV2"})))]

    (testing "deleting system groups"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to delete system-level access control groups."]}
               (u/delete-group prov-token sys-group-id))))

      (testing "with permission"
        (is (= {:status 200 :concept-id sys-group-id :revision-id 2}
               (u/delete-group sys-token sys-group-id)))
        (u/assert-group-deleted sys-group "user1" sys-group-id 2)))

    (testing "deleting provider groups"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to delete access control groups for provider [PROV1]."]}
               (u/delete-group sys-token prov-group-id)))
        (is (= {:status 401
                :errors ["You do not have permission to delete access control groups for provider [PROV2]."]}
               (u/delete-group prov-token prov2-group-id))))

      (testing "with permission"
        (is (= {:status 200 :concept-id prov-group-id :revision-id 2}
               (u/delete-group prov-token prov-group-id)))
        (u/assert-group-deleted prov-group "user2" prov-group-id 2)))))

(deftest update-group-acl-test

  ;; members of "sys-group" can create system-level groups and delete the group with the guid "sys-group-guid"
  (e/grant-system-group-permissions-to-group (u/conn-context) "sys-group" :create)
  (e/grant-group-instance-permissions-to-group (u/conn-context) "sys-group" "sys-group-guid" :update)
  ;; members of "prov1-group" can create groups for PROV1 but can only update the group with guid "prov1-group-guid"
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov1-group" "prov1guid" :create)
  (e/grant-group-instance-permissions-to-group (u/conn-context) "prov1-group" "prov1-group-guid" :update)
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov2-group" "prov2guid" :create)

  (let [sys-token (e/login (u/conn-context) "user1" ["sys-group"])
        prov-token (e/login (u/conn-context) "user2" ["prov1-group"])

        sys-group (u/make-group {:legacy-guid "sys-group-guid"})
        sys-group-id (:concept-id (u/create-group sys-token sys-group))

        prov-group (u/make-group {:provider-id "PROV1" :legacy-guid "prov1-group-guid"})
        prov-group-id (:concept-id (u/create-group prov-token prov-group))

        prov2-group (u/make-group {:provider-id "PROV2"})
        prov2-group-id (:concept-id (u/create-group (e/login (u/conn-context) "user3" ["prov2-group"])
                                                    prov2-group))]

    (testing "updating system groups"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to update system-level access control groups."]}
               (u/update-group prov-token sys-group-id (assoc sys-group :description "Updated name")))))

      (testing "with permission"
        (is (= {:status 200 :concept-id sys-group-id :revision-id 2}
               (u/update-group sys-token sys-group-id (assoc sys-group :description "Updated name"))))
        (u/assert-group-saved sys-group "user1" sys-group-id 2)))

    (testing "updating provider groups"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to update access control groups for provider [PROV1]."]}
               (u/update-group sys-token prov-group-id (assoc prov-group :description "Updated name"))))
        (is (= {:status 401
                :errors ["You do not have permission to update access control groups for provider [PROV2]."]}
               (u/update-group prov-token prov2-group-id (assoc prov2-group :description "Updated name")))))

      (testing "with permission"
        (is (= {:status 200 :concept-id prov-group-id :revision-id 2}
               (u/update-group prov-token prov-group-id (assoc prov-group :description "Updated name"))))
        (u/assert-group-saved prov-group "user2" prov-group-id 2)))))
