(ns cmr.access-control.int-test.access-control-group-crud-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.access-control.int-test.fixtures :as fixtures]
   [cmr.access-control.test.util :as test-util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :each
              (fixtures/int-test-fixtures)
              (fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"} ["user1" "user2" "user3"])
              (fixtures/grant-all-group-fixture ["PROV1" "PROV2"])
              (fixtures/grant-all-acl-fixture))

;; CMR-2134, CMR-2133 test creating groups without various permissions

(def field-maxes
  "A map of fields to their max lengths"
  {:name 100
   :description 255
   :legacy_guid 50})

(defn string-of-length
  "Creates a string of the specified length"
  [n]
  (str/join (repeat n "x")))

(deftest create-group-validation-test
  (let [valid-user-token (echo-util/login (test-util/conn-context) "user1")
        valid-group (test-util/make-group)]

    (testing "Create group with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (test-util/create-group valid-user-token
                                     valid-group
                                     {:http-options {:content-type :xml}
                                      :allow-failure? true}))))

    (testing "Create group with invalid JSON"
      (let [{:keys [status errors]} (test-util/create-group valid-user-token
                                                            valid-group
                                                            {:http-options {:body "{{{"}
                                                             :allow-failure? true})]
        (is (= 400 status))
        (is (re-find #"Invalid JSON: A JSON Object can not directly nest another JSON Object"
                     (first errors)))))

    (testing "Missing field validations"
      (are [field]
        (= {:status 400
            :errors [(format "#: required key [%s] not found" (name field))]}
           (test-util/create-group valid-user-token (dissoc valid-group field) {:allow-failure? true}))

        :name :description))

    (testing "Minimum field length validations"
      (are [field]
        (= {:status 400
            :errors [(format "#/%s: expected minLength: 1, actual: 0" (name field))]}
           (test-util/create-group valid-user-token (assoc valid-group field "") {:allow-failure? true}))

        :name :description :provider_id :legacy_guid))

    (testing "Maximum field length validations"
      (doseq [[field max-length] field-maxes]
        (let [long-value (string-of-length (inc max-length))]
          (is (= {:status 400
                  :errors [(format "#/%s: expected maxLength: %d, actual: %d"
                                   (name field)
                                   max-length
                                   (inc max-length))]}
                 (test-util/create-group
                  valid-user-token
                  (assoc valid-group field long-value)
                  {:allow-failure? true}))))))))

(deftest create-system-group-test
  (testing "Successful creation"
    (let [group (test-util/make-group)
          token (echo-util/login (test-util/conn-context) "user1")
          {:keys [status concept_id revision_id]} (test-util/create-group token group)]
      (is (= 200 status))
      (is (re-matches #"AG\d+-CMR" concept_id) "Incorrect concept id for a system group")
      (is (= 1 revision_id))
      (test-util/assert-group-saved group "user1" concept_id revision_id)

      (testing "Creation with an already existing name"
        (testing "Is rejected for another system group"
          (is (= {:status 409
                  :errors [(format "A system group with name [%s] already exists with concept id [%s]."
                                   (:name group) concept_id)]}
                 (test-util/create-group token group {:allow-failure? true}))))

        (testing "Works for a different provider"
          (is (= 200 (:status (test-util/create-group token (assoc group :provider_id "PROV1")))))))

      (testing "Creation of previously deleted group"
        (is (= 200 (:status (test-util/delete-group token concept_id))))
        (let [new-group (assoc group :legacy_guid "the legacy guid" :description "new description")
              response (test-util/create-group token new-group)]
          (is (= {:status 200 :concept_id (:concept_id response) :revision_id 1}
                 response))
          (test-util/assert-group-saved new-group "user1" (:concept_id response) 1))))

    (testing "Create group with fields at maximum length"
      (let [group (into {} (for [[field max-length] field-maxes]
                             [field (string-of-length max-length)]))
            ;; FIXME understand why having this outside of a binding causes JVM corruption error
            ;; in Clojure 1.10.0
            _ (is (= 200 (:status (test-util/create-group
                                   (echo-util/login
                                    (test-util/conn-context)
                                    "user1")
                                   group))))])))

  (testing "Creation without optional fields is allowed"
    (let [group (dissoc (test-util/make-group {:name "name2"}) :legacy_guid)
          token (echo-util/login (test-util/conn-context) "user1")
          {:keys [status concept_id revision_id]} (test-util/create-group token group)]
      (is (= 200 status))
      (is concept_id)
      (is (= 1 revision_id)))))

(deftest create-provider-group-test
  (testing "Successful creation"
    (let [group (test-util/make-group {:name "TeSt GrOuP 1" :provider_id "PROV1"})
          token (echo-util/login (test-util/conn-context) "user1")
          {:keys [status concept_id revision_id]} (test-util/create-group token group)
          lowercase-group (assoc group :name (str/lower-case (:name group)))]
      (is (= 200 status))
      (is (re-matches #"AG\d+-PROV1" concept_id) "Incorrect concept id for a provider group")
      (is (= 1 revision_id))
      (test-util/assert-group-saved group "user1" concept_id revision_id)

      (testing "Creation with an already existing name"
        (testing "Is rejected for the same provider"
          (is (= {:status 409
                  :errors [(format
                            "A provider group with name [%s] already exists with concept id [%s] for provider [PROV1]."
                            (:name group) concept_id)]}
                 (test-util/create-group token group {:allow-failure? true}))))
        (testing "Is rejected for the same provider if case insensitive"
          (is (= {:status 409
                  :errors [(format
                            "A provider group with name [%s] already exists with concept id [%s] for provider [PROV1]."
                            (:name group) concept_id)]}
                 (test-util/create-group token lowercase-group {:allow-failure? true}))))

        (testing "Works for a different provider"
          (is (= 200 (:status (test-util/create-group token (assoc group :provider_id "PROV2")))))))))

  (testing "Creation for a non-existent provider"
    (is (= {:status 400
            :errors ["Provider with provider-id [NOT_EXIST] does not exist."]}
           (test-util/create-group
            (echo-util/login (test-util/conn-context) "user1")
            (test-util/make-group {:provider_id "NOT_EXIST"})
            {:allow-failure? true})))))

(deftest create-group-with-members-test
  (let [token (echo-util/login (test-util/conn-context) "user1")
        create-group-with-members (fn [members]
                                    (test-util/create-group
                                     token
                                     (test-util/make-group {:members members})
                                     {:allow-failure? true}))]
    (testing "Successful create with existing members"
      (let [{:keys [status concept_id]} (create-group-with-members ["user1" "user2"])]
        (is (= 200 status))
        (is (= {:status 200 :body ["user1" "user2"]} (test-util/get-members token concept_id)))))

    (testing "Attempt to create group with non-existent members"
      (let [{:keys [status errors]} (create-group-with-members ["user1" "user4"])]
        (is (= 400 status))
        (is (= ["The following users do not exist [user4]"] errors))))))

;; This test currently records a false positive, the single instance acl is not granting permissions because group
;; service uses echo-rest to determine permissions therefor adding a single instance acl to cmr has no effect.
;; The fixture system group acl in mock echo is what allows for any user to modify groups in this test. CMR-3295 is needed to have
;; mock-echo use cmr for acls instead of echo-rest to more accurately depict how operations works with cmr-acl-read-enabled.
(deftest create-group-with-managing-group-id-test
  (let [token-user1 (echo-util/login (test-util/conn-context) "user1")
        token-user2 (echo-util/login (test-util/conn-context) "user2")
        token-user3 (echo-util/login (test-util/conn-context) "user3")
        managing-group (test-util/ingest-group token-user1 {:name "managing group"} ["user1"])
        managing-group-id (:concept_id managing-group)
        create-group-with-managing-group
        (fn [group-name managing-group-id]
          (test-util/create-group
           (transmit-config/echo-system-token)
           (test-util/make-group {:name group-name})
           {:http-options {:query-params {:managing_group_id managing-group-id}}
            :allow-failure? true}))]
    (testing "Successful create with managing group id"
      ;; search for ACLs related to managing group and found none
      (let [{:keys [hits]} (access-control/search-for-acls
                            (test-util/conn-context)
                            {:permitted-group [managing-group-id]})]
        (is (= 0 hits)))

      ;; create a new group with the managing group
      (let [{:keys [status concept_id]} (create-group-with-managing-group
                                         "group1" managing-group-id)]
        ;; verify group creation is successful
        (is (= 200 status))
        ;; verify a new ACL is now created on the managing group
        (is (= 1 (:hits (access-control/search-for-acls (test-util/conn-context)
                                                        {:permitted-group [managing-group-id]}))))

        ;; the group starts with no members
        (is (= {:status 200 :body []} (test-util/get-members token-user1 concept_id)))

        ;; Add members to the group as a user in the managing group
        (is (= 200 (:status (test-util/add-members token-user1 concept_id ["user2"]))))
        ;; verify that the group now has a member added
        (is (= {:status 200 :body ["user2"]} (test-util/get-members token-user1 concept_id)))

        ;; Verify that user not in managing group does not have permission to add member to the group.
        ;; Remove grant-all ECHO acl from fixture
        (echo-util/ungrant (test-util/conn-context) "ACL1200000001-CMR")
        ;; Clear it from cmr ACL cache
        (access-control/clear-cache (test-util/conn-context))
        (is (= 401 (:status (test-util/add-members token-user3 concept_id ["user3"]))))))

    (testing "Attempt to create group with non-existent managing group id"
      (let [{:keys [status errors]} (create-group-with-managing-group "group2" "AG10000-PROV1")]
        (is (= 400 status))
        (is (= ["Managing group id [AG10000-PROV1] is invalid, no group with this concept id can be found."]
               errors))))
    (testing "Attempt to create group with more than one managing group id"
      (let [{:keys [status errors]} (create-group-with-managing-group
                                     "group2" [managing-group-id "AG10000-PROV1"])]
        (is (= 400 status))
        (is (= ["Parameter managing_group_id must have a single value."]
               errors))))
    (testing "Attempt to create group with a deleted managing group id"
      ;; delete the managing group
      (test-util/delete-group (transmit-config/echo-system-token) managing-group-id)
      (let [{:keys [status errors]} (create-group-with-managing-group "group2" managing-group-id)]
        (is (= 400 status))
        (is (= [(format "Managing group id [%s] is invalid, no group with this concept id can be found."
                        managing-group-id)]
               errors))))))

(deftest get-group-test
  (let [group (test-util/make-group)
        token (echo-util/login (test-util/conn-context) "user1")
        {:keys [concept_id]} (test-util/create-group token group)]
    (testing "Retrieve existing group"
      (is (= (assoc group :status 200 :num_members 0)
             (test-util/get-group token concept_id))))

    (testing "Retrieve unknown group"
      (is (= {:status 404
              :errors ["Group could not be found with concept id [AG100-CMR]"]}
             (test-util/get-group token "AG100-CMR"))))
    (testing "Retrieve group with bad concept-id"
      (is (= {:status 400
              :errors ["Concept-id [F100-CMR] is not valid."]}
             (test-util/get-group token "F100-CMR"))))
    (testing "Retrieve group with invalid parameters"
      (let [response (test-util/get-group token concept_id {"Authorization" "asdf" "bf2376tri7f" "true"})]
        (is (= {:status 400
                :errors #{"Parameter [Authorization] was not recognized." "Parameter [bf2376tri7f] was not recognized."}}
               (update-in response [:errors] set)))))
    (testing "Retrieve group with concept id for a different concept type"
      (is (= {:status 400
              :errors ["[C100-PROV1] is not a valid group concept id."]}
             (test-util/get-group token "C100-PROV1"))))
    (testing "Retrieve group with bad provider in concept id"
      (is (= {:status 404
              :errors ["Group could not be found with concept id [AG100-PROV3]"]}
             (test-util/get-group token "AG100-PROV3"))))
    (testing "Retrieve deleted group"
      (test-util/delete-group token concept_id)
      (is (= {:status 404
              :errors [(format "Group with concept id [%s] was deleted." concept_id)]}
             (test-util/get-group token concept_id))))))

(deftest delete-group-test
  (let [group1 (test-util/make-group {:name "other group"})
        group2 (test-util/make-group {:name "Some other group"})
        token (echo-util/login (test-util/conn-context) "user1")
        {:keys [concept_id revision_id]} (test-util/create-group token group1)
        group2-concept-id (:concept_id (test-util/create-group token group2))]
    (testing "Delete without token"
      (is (= {:status 401
              :errors ["Valid user token required."]}
             (test-util/delete-group nil concept_id))))

    (testing "Delete success"
      (is (= 3 (:hits (test-util/search-for-groups token nil))))
      (is (= {:status 200 :concept_id concept_id :revision_id 2}
             (test-util/delete-group token concept_id)))
      (test-util/assert-group-deleted group1 "user1" concept_id 2)
      (is (= [group2-concept-id]
             (map :concept_id (:items (test-util/search-for-groups
                                       token {:name "*other*"
                                              "options[name][pattern]" true}))))))

    (testing "Delete group that was already deleted"
      (is (= {:status 404
              :errors [(format "Group with concept id [%s] was deleted." concept_id)]}
             (test-util/delete-group token concept_id))))

    (testing "Delete group that doesn't exist"
      (is (= {:status 404
              :errors ["Group could not be found with concept id [AG100-CMR]"]}
             (test-util/delete-group token "AG100-CMR"))))))

(deftest delete-group-cascade-to-acl-test
  ;; Two groups will be created along with two ACLs referencing each respective group. After one
  ;; group is deleted, the ACL referencing it should also be deleted, and the other ACL should
  ;; still exist.
  (let [token (echo-util/login (test-util/conn-context) "user")
        ;; group 1 will be deleted
        group-1-concept-id (:concept_id (test-util/create-group
                                         token
                                         (test-util/make-group {:name "group 1" :provider_id "PROV1"})))
        acl-1-concept-id (:concept_id
                           (test-util/create-acl
                            token
                            {:group_permissions [{:user_type :registered
                                                  :permissions ["update"]}]
                             :single_instance_identity {:target "GROUP_MANAGEMENT"
                                                        :target_id group-1-concept-id}}))
        ;; group 2 won't be deleted
        group-2-concept-id (:concept_id (test-util/create-group
                                         token
                                         (test-util/make-group
                                          {:name "group 2" :provider_id "PROV1"})))
        acl-2-concept-id (:concept_id
                           (test-util/create-acl
                            token
                            {:group_permissions [{:user_type :registered
                                                  :permissions ["update"]}]
                             :single_instance_identity {:target "GROUP_MANAGEMENT"
                                                        :target_id group-2-concept-id}}))
        acl1 {:group_permissions [{:user_type :registered :permissions ["read"]}
                                  {:group_id group-1-concept-id :permissions ["read"]}
                                  {:group_id group-2-concept-id :permissions ["read"]}]
              :system_identity {:target "METRIC_DATA_POINT_SAMPLE"}}

        acl2 {:group_permissions [{:group_id group-1-concept-id :permissions ["delete"]}]
              :system_identity {:target "ARCHIVE_RECORD"}}
        resp1 (test-util/create-acl token acl1)
        resp2 (test-util/create-acl token acl2)
        ;;remove some ACLs created in fixtures which we dont want polluting tests
        fixture-acls (:items (test-util/search-for-acls (transmit-config/echo-system-token) {:target "GROUP"}))
        _ (doseq [fixture-acl fixture-acls]
            (echo-util/ungrant (test-util/conn-context) (:concept_id fixture-acl)))]

    ; (echo-util/ungrant (test-util/conn-context) (:concept_id fixtures/*fixture-system-acl*))
    ; (echo-util/ungrant (test-util/conn-context) (:concept_id fixtures/*fixture-provider-acl*))
    (test-util/wait-until-indexed)
    (is (= [acl-1-concept-id acl-2-concept-id]
           (sort
             (map :concept_id
                  (:items (test-util/search-for-acls token {:identity-type "single_instance"}))))))

    (test-util/delete-group token group-1-concept-id)
    (test-util/wait-until-indexed)

    (testing "No ACLs should be deleted beside group 1 single instance ACL"
      (is (= (set [(:concept_id resp1) (:concept_id resp2) (:concept-id fixtures/*fixture-system-acl*)
                   (:concept-id fixtures/*fixture-provider-acl*) acl-2-concept-id])
             (set (map :concept_id
                       (:items (test-util/search-for-acls token {})))))))

    (testing "group 2 shouldn't be removed from group permissions"
      (is (= (set [(:concept_id resp1)])
             (set (map :concept_id
                       (:items (test-util/search-for-acls token {:permitted_group group-2-concept-id})))))))

    (testing "registered shouldn't be removed from group permissions"
      (is (= (set [(:concept_id resp1) acl-2-concept-id (:concept-id fixtures/*fixture-system-acl*) (:concept-id fixtures/*fixture-provider-acl*)])
             (set (map :concept_id
                       (:items (test-util/search-for-acls token {:permitted_group "registered"})))))))

    (testing "No ACLs should have group 1 after delete"
      (is (empty? (set (:items (test-util/search-for-acls token {:permitted_group group-1-concept-id}))))))))

(deftest update-group-test
  (let [group (test-util/make-group {:members ["user1" "user2"]})
        token (echo-util/login (test-util/conn-context) "user1")
        {:keys [concept_id]} (test-util/create-group token group)]

    ;; Do not specify members in the update
    (let [updated-group {:name "Updated Group Name"
                         :description "Updated group description"}
          token2 (echo-util/login (test-util/conn-context) "user2")
          response (test-util/update-group token2 concept_id updated-group)]
      (is (= {:status 200 :concept_id concept_id :revision_id 2}
             response))
      ;; Members should be intact
      (test-util/assert-group-saved (assoc updated-group :members ["user1" "user2"]) "user2" concept_id 2))))

(deftest update-group-with-members-test
  (let [group (test-util/make-group {:members ["user1" "user2"]})
        token (echo-util/login (test-util/conn-context) "user1")
        {:keys [concept_id revision_id]} (test-util/create-group token group)]

    ;; Change members as part of the update
    (let [updated-group {:name "Administrators2" :description "A very good group updated" :members ["user1"]}
          token2 (echo-util/login (test-util/conn-context) "user2")
          response (test-util/update-group token2 concept_id updated-group)]
      (is (= {:status 200 :concept_id concept_id :revision_id 2}
             response))
      (test-util/assert-group-saved updated-group "user2" concept_id 2))))

(deftest update-group-failure-test
  (let [group (test-util/make-group)
        group2 (test-util/make-group {:name "Group2" :members ["user1"]})
        group3 (test-util/make-group {:name "Group3" :members ["user1"]})

        token (echo-util/login (test-util/conn-context) "user1")

        {:keys [concept_id revision_id]} (test-util/create-group token group)
        {concept_id2 :concept_id} (test-util/create-group token group2)
        {concept_id3 :concept_id} (test-util/create-group token group3)]

    (testing "Update group with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (test-util/update-group token concept_id group {:http-options {:content-type :xml}}))))

    (testing "Update without token"
      (is (= {:status 401
              :errors ["Valid user token required."]}
             (test-util/update-group nil concept_id group))))

    (testing "Fields that cannot be changed"
      (are [field human-name]
           (= {:status 400
               :errors [(format (str "%s cannot be modified. Attempted to change existing value"
                                     " [%s] to [updated]")
                                human-name
                                (get group field))]}
              (test-util/update-group token concept_id (assoc group field "updated")))
           :provider_id "Provider Id"
           :legacy_guid "Legacy Guid"))

    (testing "Updates applies JSON validations"
      (is (= {:status 400
              :errors ["#/description: expected minLength: 1, actual: 0"]}
             (test-util/update-group token concept_id (assoc group :description "")))))

    (testing "Update group that doesn't exist"
      (is (= {:status 404
              :errors ["Group could not be found with concept id [AG100-CMR]"]}
             (test-util/update-group token "AG100-CMR" group))))

    (testing "Update deleted group"
      (test-util/delete-group token concept_id)
      (is (= {:status 404
              :errors [(format "Group with concept id [%s] was deleted." concept_id)]}
             (test-util/update-group token concept_id group))))

    (testing "Update a group with conflicting name"
      (is (= {:status 409
              :errors [(format "A system group with name [%s] already exists with concept id [%s]."
                               (:name group2)
                               concept_id2)]}
             (test-util/update-group token concept_id3 (assoc group3 :name (:name group2))))))

    (testing "Update a group with conflicting name"
      (is (= {:status 409
              :errors [(format "A system group with name [%s] already exists with concept id [%s]."
                               (:name group2)
                               concept_id2)]}
             (test-util/update-group token concept_id3 (assoc group3 :name (:name group2))))))))

(deftest update-provider-groups-test
  (let [group1 (test-util/make-group {:name "group1" :provider_id "PROV1"})
        group1a (test-util/make-group {:name "group2" :provider_id "PROV1"})
        group2 (test-util/make-group {:name "group2" :provider_id "PROV2"})
        token (echo-util/login (test-util/conn-context) "user1")
        {concept_id1 :concept_id} (test-util/create-group token group1)
        {concept_id1a :concept_id} (test-util/create-group token group1a)
        {concept_id2 :concept_id} (test-util/create-group token group2)]

    (testing "prevent the same provider from re-using group names"
      (is (= {:status 409
              :errors [(format (str "A provider group with name [%s] already exists "
                                    "with concept id [%s] for provider [%s].")
                               (:name group1)
                               concept_id1
                               (:provider_id group1))]}
             (test-util/update-group token concept_id1a (assoc group1a :name (:name group1))))))

    (testing "allow different providers to re-use group names"
      (is (= {:status 200
              :revision_id 2
              :concept_id (format "%s" concept_id2)}
             (test-util/update-group token concept_id2 (assoc group2 :name (:name group1))))))))

(deftest update-group-legacy-guid-test
  (let [group1 (test-util/make-group {:legacy_guid "legacy_guid_1" :name "group1"})

        no-legacy-group (test-util/make-group {:name "group1"})
        same-legacy-group (test-util/make-group {:legacy_guid "legacy_guid_1" :name "group1"})
        diff-legacy-group (test-util/make-group {:legacy_guid "wrong_legacy_guid" :name "group1"})

        token (echo-util/login (test-util/conn-context) "user1")
        concept-id (:concept_id (test-util/create-group token group1))

        response-no-legacy (test-util/update-group token concept-id no-legacy-group)
        response-same-legacy (test-util/update-group token concept-id same-legacy-group)
        response-diff-legacy (test-util/update-group token concept-id diff-legacy-group)]

    (testing "We should now successfully update groups with a legacy_guid, without specifying the legacy_guid in the updated group"
      (is (= {:status 200 :concept_id concept-id :revision_id 2}
             response-no-legacy))
      (test-util/assert-group-saved (assoc no-legacy-group :legacy_guid "legacy_guid_1") "user1" concept-id 2))

    (testing "Specifying the same legacy_guid should also successfully update the group"
      (is (= {:status 200 :concept_id concept-id :revision_id 3}
             response-same-legacy))
      (test-util/assert-group-saved same-legacy-group "user1" concept-id 3))

    (testing "When specifying a different legacy_guid, an error message should be received"
      (is (= {:status 400,
              :errors ["Legacy Guid cannot be modified. Attempted to change existing value [legacy_guid_1] to [wrong_legacy_guid]"]}
             response-diff-legacy)))))
