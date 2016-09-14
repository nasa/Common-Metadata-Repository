(ns cmr.access-control.int-test.access-control-group-acl-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.int-test.fixtures :as fixtures]
   [cmr.access-control.test.util :as u]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :once (fixtures/int-test-fixtures))
(use-fixtures :each (fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"} ["user1" "user2" "user3" "user4" "user5"]))

(comment
 ((fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"} ["user1" "user2" "user3" "user4" "user5"])
  (constantly true)))

(defn create-group-with-members
  "Creates a group with the given members. Returns the concept-id"
  ([name users]
   (create-group-with-members name nil users))
  ([name provider-id users]
   (let [group (if provider-id
                 {:name name
                  :provider_id provider-id}
                 {:name name})]
     (:concept_id
      (u/create-group-with-members
       transmit-config/mock-echo-system-token
       (u/make-group group)
       users)))))

(deftest create-system-group-test
  (let [group-id (create-group-with-members "group-create-group" ["user1"])]
    (e/grant-system-group-permissions-to-group (u/conn-context) group-id :create))

  ;; Wait until groups are indexed.
  (u/wait-until-indexed)
  ;; ACLS would have already been cached in Access Control Service
  (access-control/clear-cache (u/conn-context))

  (testing "with permission"
    (let [group (u/make-group)
          token (e/login (u/conn-context) "user1" ["group-create-group"])
          {:keys [status concept_id revision_id]} (u/create-group token group)]
      (is (= 200 status))
      (is (some? concept_id))
      (is (= 1 revision_id))))

  (testing "without permission"
    (let [group (u/make-group)
          token (e/login (u/conn-context) "user2")
          response (u/create-group token group)]
      (is (= {:status 401
              :errors ["You do not have permission to create system-level access control group [Administrators]."]}
             response)))))

(deftest create-provider-group-test

  ;; Put user1 in new group prov1-group-create-group and give permission to create prov1 groups
  (let [group-id (create-group-with-members "prov1-group-create-group" "PROV1" ["user1"])]
    (e/grant-provider-group-permissions-to-group (u/conn-context) group-id "prov1guid" :create))

  ;; Wait until groups are indexed.
  (u/wait-until-indexed)
  ;; ACLS would have already been cached in Access Control Service
  (access-control/clear-cache (u/conn-context))

  (testing "with permission"
    (let [group (u/make-group {:provider_id "PROV1"})
          token (e/login (u/conn-context) "user1" ["prov1-group-create-group"])
          {:keys [status concept_id revision_id]} (u/create-group token group)]
      (is (= 200 status))
      (is (re-matches #"AG\d+-PROV1" concept_id) "Incorrect concept id for a provider group")
      (is (= 1 revision_id))))

  (testing "without permission"
    (let [token (e/login (u/conn-context) "user2")
          group (u/make-group {:provider_id "PROV1"})
          response (u/create-group token group)]
      (is (= {:status 401
              :errors ["You do not have permission to create access control group [Administrators] in provider [PROV1]."]}
             response)))))

(deftest get-group-acl-test
  (let [group-id (create-group-with-members "prov1-group-readers" "PROV1" ["user1" "user3"])]
    (e/grant-provider-group-permissions-to-group (u/conn-context) group-id "prov1guid" :read :create))

  (let [group-id (create-group-with-members "prov2-group-creator" "PROV2" ["user3"])]
    (e/grant-provider-group-permissions-to-group (u/conn-context) group-id "prov2guid" :create))

  (let [group-id (create-group-with-members "sys-group-readers" ["user1"])]
    (e/grant-system-group-permissions-to-group (u/conn-context) group-id :read :create))


  ;; Wait until groups are indexed.
  (u/wait-until-indexed)
  ;; ACLS would have already been cached in Access Control Service
  (access-control/clear-cache (u/conn-context))

  (let [token (e/login (u/conn-context) "user1" ["sys-group-readers" "prov1-group-readers"])
        no-group-token (e/login (u/conn-context) "user2")
        prov1-only-token (e/login (u/conn-context) "user3" ["prov1-group-readers"])
        system-group (u/make-group)
        system-group-concept-id (:concept_id (u/create-group token system-group))
        prov-group (u/make-group {:provider_id "PROV1"})
        prov-group-concept-id (:concept_id (u/create-group token prov-group))
        prov2-group (u/make-group {:provider_id "PROV2"})
        prov2-creator-token (e/login (u/conn-context) "user3" ["prov2-group-creator"])
        prov2-group-concept-id (:concept_id (u/create-group prov2-creator-token prov2-group))]

    (testing "reading system groups"
      (testing "with permission"
        (is (= (assoc system-group :status 200 :num_members 0)
               (u/get-group token system-group-concept-id))))

      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to read system-level access control group [Administrators]."]}
               (u/get-group no-group-token system-group-concept-id)))))

    (testing "reading provider groups"
      (testing "with permission"
        (is (= (assoc prov-group :num_members 0 :status 200)
               (u/get-group token prov-group-concept-id))))

      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to read access control group [Administrators] in provider [PROV1]."]}
               (u/get-group no-group-token prov-group-concept-id)))

        (is (= {:status 401
                :errors ["You do not have permission to read access control group [Administrators] in provider [PROV2]."]}
               (u/get-group prov1-only-token prov2-group-concept-id)))))))

(deftest group-search-acl-test
  (e/grant-system-group-permissions-to-group (u/conn-context) "sys-group" :create :read)
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov1-group" "prov1guid" :create :read)
  (let [sys-token (e/login (u/conn-context) "sys-user" ["sys-group"])
        sys-group (u/make-group)
        sys-group-concept-id (:concept_id (u/create-group sys-token sys-group))
        prov1-token (e/login (u/conn-context) "prov1-user" ["prov1-group"])
        prov1-group (u/make-group {:provider_id "PROV1"})
        prov1-group-concept-id (:concept_id (u/create-group prov1-token prov1-group))]
    (u/wait-until-indexed)
    (is (= [sys-group-concept-id]
           (map :concept_id (:items (u/search-for-groups sys-token {:name "Administrators"})))))
    (is (= [prov1-group-concept-id]
           (map :concept_id (:items (u/search-for-groups prov1-token {:name "Administrators"})))))
    (is (= 0 (:hits (u/search-for-groups (e/login (u/conn-context) "non-permitted-user") {:name "Administrators"}))))))

(deftest delete-group-acl-test

  ;; Note: the following comment is temporarily inaccurate due to CMR-2585
  ;; Members of "sys-group-delete" can create system-level groups and delete the (to-be-created) group with
  ;; guid "system-group-guid". Members of "prov*-" groups can do the same with their respective
  ;; "prov*-group-guid" groups.

  (e/grant-system-group-permissions-to-group (u/conn-context) "sys-group-delete" :create)
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov1-group-delete" "prov1guid" :create)
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov2-group-creator" "prov2guid" :create)

  ;; Note: temporarily disabled for CMR-2585
  ;; (e/grant-group-instance-permissions-to-group (u/conn-context) "sys-group-delete" "system-group-guid" :delete)
  ;; (e/grant-group-instance-permissions-to-group (u/conn-context) "prov1-group-delete" "prov1-group-guid" :delete)
  ;; (e/grant-group-instance-permissions-to-group (u/conn-context) "prov2-group-creator" "prov2-group-guid" :delete)

  (let [sys-token (e/login (u/conn-context) "user1" ["sys-group-delete"])
        prov-token (e/login (u/conn-context) "user2" ["prov1-group-delete"])
        prov2-token (e/login (u/conn-context) "user3" ["prov2-group-creator"])

        sys-group (u/make-group {:legacy_guid "system-group-guid"})
        sys-group-id (:concept_id (u/create-group sys-token sys-group))

        prov-group (u/make-group {:provider_id "PROV1" :legacy_guid "prov1-group-guid"})
        prov-group-id (:concept_id (u/create-group prov-token prov-group))

        prov2-group-id (:concept_id (u/create-group prov2-token
                                                    (u/make-group {:provider_id "PROV2"})))]

    (testing "deleting system groups"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to delete system-level access control group [Administrators]."]}
               (u/delete-group prov-token sys-group-id))))

      (testing "with permission"
        (is (= {:status 200 :concept_id sys-group-id :revision_id 2}
               (u/delete-group sys-token sys-group-id)))
        (u/assert-group-deleted sys-group "user1" sys-group-id 2)))

    (testing "deleting provider groups"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to delete access control group [Administrators] in provider [PROV1]."]}
               (u/delete-group prov2-token prov-group-id))))

      (testing "with permission"
        (is (= {:status 200 :concept_id prov-group-id :revision_id 2}
               (u/delete-group prov-token prov-group-id)))
        (u/assert-group-deleted prov-group "user2" prov-group-id 2)))))

(deftest update-group-acl-test
  ;; members of "sys-group" can create system-level groups and delete the group with the guid "sys-group-guid"
  ;; Note: :update permission is here temporarily as part of CMR-2585
  (e/grant-system-group-permissions-to-group (u/conn-context) "sys-group" :create :update)

  ;; Note: temporarily disabled for CMR-2585
  ;; (e/grant-group-instance-permissions-to-group (u/conn-context) "sys-group" "sys-group-guid" :update)
  ;; (e/grant-group-instance-permissions-to-group (u/conn-context) "prov1-group" "prov1-group-guid" :update)

  ;; members of "prov1-group" can create (and temporarily update for CMR-2585) groups for PROV1
  ;; but can only update the group with guid "prov1-group-guid"
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov1-group" "prov1guid" :create)
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov2-group" "prov2guid" :create)

  (let [sys-token (e/login (u/conn-context) "user1" ["sys-group"])
        prov-token (e/login (u/conn-context) "user2" ["prov1-group"])
        prov2-token (e/login (u/conn-context) "user3" ["prov2-group"])

        sys-group (u/make-group {:legacy_guid "sys-group-guid"})
        sys-group-id (:concept_id (u/create-group sys-token sys-group))

        prov-group (u/make-group {:provider_id "PROV1" :legacy_guid "prov1-group-guid"})
        prov-group-id (:concept_id (u/create-group prov-token prov-group))

        prov2-group (u/make-group {:provider_id "PROV2"})
        prov2-group-id (:concept_id (u/create-group prov2-token
                                                    prov2-group))]

    (testing "updating system groups"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to update system-level access control group [Administrators]."]}
               (u/update-group prov-token sys-group-id (assoc sys-group :description "Updated name")))))

      (testing "with permission"
        (is (= {:status 200 :concept_id sys-group-id :revision_id 2}
               (u/update-group sys-token sys-group-id (assoc sys-group :description "Updated name"))))))

    (testing "updating provider groups"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to update access control group [Administrators] in provider [PROV1]."]}
               (u/update-group prov2-token prov-group-id (assoc prov-group :description "Updated name")))))

      (testing "with permission"
        (is (= {:status 200 :concept_id prov-group-id :revision_id 2}
               (u/update-group prov-token prov-group-id (assoc prov-group :description "Updated name"))))))))

(deftest group-members-acl-test

  ;; members of "sys-group" can create and update system-level groups
  (e/grant-system-group-permissions-to-group (u/conn-context) "sys-group" :create :read)

  ;; members of "prov1-group" can create and update groups for PROV1
  (e/grant-provider-group-permissions-to-group (u/conn-context) "prov1-group" "prov1guid" :create :read)

  ;; Note: these are temporarily disabled for CMR-2585
  ;; (e/grant-group-instance-permissions-to-group (u/conn-context) "sys-group" "sys-group-guid" :update)
  ;; (e/grant-group-instance-permissions-to-group (u/conn-context) "prov1-group" "prov1-group-guid" :update)

  (let [sys-token (e/login (u/conn-context) "sys-user" ["sys-group"])
        sys-group (u/make-group {:legacy_guid "sys-group-guid"})
        sys-group-concept-id (:concept_id (u/create-group sys-token sys-group))
        prov1-token (e/login (u/conn-context) "prov1-user" ["prov1-group"])
        prov1-group (u/make-group {:legacy_guid "prov1-group-guid" :provider_id "PROV1"})
        prov1-group-concept-id (:concept_id (u/create-group prov1-token prov1-group))]

    (testing "read group members"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to read system-level access control group [Administrators]."]}
               (u/get-members prov1-token sys-group-concept-id)))
        (is (= {:status 401
                :errors ["You do not have permission to read access control group [Administrators] in provider [PROV1]."]}
               (u/get-members (e/login (u/conn-context) "some-random-user") prov1-group-concept-id))))
      (testing "with permission"
        (is (= {:status 200 :body []}
               (u/get-members sys-token sys-group-concept-id)))
        (is (= {:status 200 :body []}
               (u/get-members prov1-token prov1-group-concept-id)))))

    (testing "update group members"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to update system-level access control group [Administrators]."]}
               (u/add-members prov1-token sys-group-concept-id ["user1" "user2" "user1"])))
        ;; Note: temporarily disabled for CMR-2585
        (comment
          (is (= {:status 401
                  :errors ["You do not have permission to update access control group [Administrators] in provider [PROV1]."]}
                 (u/add-members sys-token prov1-group-concept-id ["user1" "user2" "user1"])))))
      (testing "with permission"
        (u/add-members sys-token sys-group-concept-id ["user1" "user2"])
        (is (= {:status 200 :body ["user1" "user2"]}
               (u/get-members sys-token sys-group-concept-id)))
        (u/add-members prov1-token prov1-group-concept-id ["user1" "user2"])
        (is (= {:status 200 :body ["user1" "user2"]}
               (u/get-members prov1-token prov1-group-concept-id)))))

    (testing "remove group members"
      (testing "without permission"
        (is (= {:status 401
                :errors ["You do not have permission to update system-level access control group [Administrators]."]}
               (u/remove-members prov1-token sys-group-concept-id ["user1" "user2" "user1"])))
        ;; Note: temporarily disabled for CMR-2585
        (comment
          (is (= {:status 401
                  :errors ["You do not have permission to update access control group [Administrators] in provider [PROV1]."]}
                 (u/remove-members sys-token prov1-group-concept-id ["user1" "user2" "user1"])))))
      (testing "with permission"
        (u/remove-members sys-token sys-group-concept-id ["user1" "user2"])
        (is (= {:status 200 :body []}
               (u/get-members sys-token sys-group-concept-id)))
        (u/remove-members prov1-token prov1-group-concept-id ["user1" "user2"])
        (is (= {:status 200 :body []}
               (u/get-members prov1-token prov1-group-concept-id)))))))
