(ns cmr.access-control.int-test.permission-check-test
  "Tests the access control permission check routes."
  (require [clojure.test :refer :all]
           [cheshire.core :as json]
           [cmr.transmit.access-control :as ac]
           [cmr.transmit.ingest :as ingest]
           [cmr.mock-echo.client.echo-util :as e]
           [cmr.common.util :as util :refer [are2]]
           [cmr.access-control.int-test.fixtures :as fixtures]
           [cmr.access-control.test.util :as u]
           [cmr.umm-spec.core :as umm-spec]
           [cmr.umm-spec.test.expected-conversion :refer [example-collection-record]]))

(use-fixtures :each
              (fixtures/int-test-fixtures)
              (fixtures/reset-fixture {"prov1guid" "PROV1" "prov2guid" "PROV2"})
              (fn [f]
                (e/grant-all-ingest (u/conn-context) "prov1guid")
                (f))
              (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"]))

(deftest invalid-params-test
  (are [params errors]
    (= {:status 400 :body {:errors errors} :content-type :json}
       (ac/get-permissions (u/conn-context) params {:raw? true}))
    {} ["Parameter [user_id] is required." "Parameter [concept_ids] is required."]
    {:user_id "" :concept_ids ""} ["Parameter [user_id] is required." "Parameter [concept_ids] is required."]
    {:user_id "foobar"} ["Parameter [concept_ids] is required."]
    {:not_a_valid_param "foo"} ["Parameter [not_a_valid_param] was not recognized."]))

(deftest concept-permission-check-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        ;; then create a group that contains our user, so we can find collections that grant access to this user
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        ;; create some collections
        coll1-umm (assoc example-collection-record :EntryTitle "coll1 entry title")
        coll1-metadata (umm-spec/generate-metadata (u/conn-context) coll1-umm :echo10)
        coll1 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata coll1-metadata
                                      :concept-type :collection
                                      :provider-id "PROV1"
                                      :native-id "coll1"
                                      :revision-id 1}
                                     {"Echo-Token" token})
        ;; local helpers to make the body of the test cleaner
        create-acl #(ac/create-acl (u/conn-context) % {:token token})
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-permissions #(json/parse-string
                          (ac/get-permissions
                            (u/conn-context)
                            {:concept_ids (:concept-id coll1) :user_id %1}))]

    (testing "no permissions granted"
      (is (= {"C1200000001-PROV1" []}
             (get-permissions "user1"))))

    (testing "concept level permissions"
      (let [acl {:group_permissions [{:permissions [:read :order]
                                      :user_type :guest}]
                 :catalog_item_identity {:name "coll1 read and order"
                                         :collection_applicable true
                                         :provider_id "PROV1"}}
            acl-concept-id (:concept_id (create-acl acl))]
        (u/wait-until-indexed)

        (testing "for guest users"
          (is (= {"C1200000001-PROV1" ["read" "order"]}
                 (get-permissions "user1"))))

        (testing "for registered users"
          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:read :order]
                                            :user_type :registered}]
                       :catalog_item_identity {:name "coll1 read and order"
                                               :collection_applicable true
                                               :provider_id "PROV1"}})
          (is (= {"C1200000001-PROV1" ["read" "order"]}
                 (get-permissions "user1"))))

        (testing "acls granting access to specific groups"

          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:read :order]
                                            :group_id created-group-concept-id}]
                       :catalog_item_identity {:name "coll1 read and order"
                                               :collection_applicable true
                                               :provider_id "PROV1"}})

          (testing "as a user in the group"
            (is (= {"C1200000001-PROV1" ["read" "order"]}
                   (get-permissions "user1"))))

          (testing "as a user not in the group"
            (is (= {"C1200000001-PROV1" []}
                   (get-permissions "notauser")))))))))

(deftest provider-permission-check-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        ;; then create a group that contains our user, so we can find collections that grant access to this user
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group-concept-id (:concept_id (u/create-group token group))
        ;; create some collections
        coll1-umm (assoc example-collection-record :EntryTitle "coll1 entry title")
        coll1-metadata (umm-spec/generate-metadata (u/conn-context) coll1-umm :echo10)
        coll1 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata coll1-metadata
                                      :concept-type :collection
                                      :provider-id "PROV1"
                                      :native-id "coll1"
                                      :revision-id 1}
                                     {"Echo-Token" token})
        ;; local helpers to make the body of the test cleaner
        create-acl #(ac/create-acl (u/conn-context) % {:token token})
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-permissions #(json/parse-string
                          (ac/get-permissions
                            (u/conn-context)
                            {:concept_ids (:concept-id coll1) :user_id %1}))]

    (testing "no permissions granted"
      (is (= {"C1200000001-PROV1" []}
             (get-permissions "user1"))))

    (testing "provider level permissions"
      (let [acl {:group_permissions [{:permissions [:update :delete]
                                      :user_type :guest}]
                 :provider_identity {:provider_id "PROV1"
                                     :target "INGEST_MANAGEMENT_ACL"}}
            acl-concept-id (:concept_id (create-acl acl))]
        (u/wait-until-indexed)

        (testing "for guest users"
          (is (= {"C1200000001-PROV1" ["update" "delete"]}
                 (get-permissions "user1"))))

        (testing "for registered users"

          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:update :delete]
                                            :user_type :registered}]
                       :provider_identity {:provider_id "PROV1"
                                           :target "INGEST_MANAGEMENT_ACL"}})

          (is (= {"C1200000001-PROV1" ["update" "delete"]}
                 (get-permissions "user1"))))

        (testing "for specific groups"

          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:update :delete]
                                            :group_id created-group-concept-id}]
                       :provider_identity {:provider_id "PROV1"
                                           :target "INGEST_MANAGEMENT_ACL"}})

          (testing "for a user in the group"
            (is (= {"C1200000001-PROV1" ["update" "delete"]}
                   (get-permissions "user1"))))

          (testing "for a user not in the group"
            (is (= {"C1200000001-PROV1" []}
                   (get-permissions "user2")))))))))
