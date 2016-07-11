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
           [cmr.umm-spec.test.expected-conversion :refer [example-collection-record]]
           [clojure.string :as str]))

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
    {} ["Parameter [concept_id] is required." "One of parameters [user_type] or [user_id] are required."]
    {:user_id "" :concept_id []} ["Parameter [concept_id] is required." "One of parameters [user_type] or [user_id] are required."]
    {:user_id "foobar"} ["Parameter [concept_id] is required."]
    {:concept_id "C1200000-PROV1" :user_type "guest" :user_id "foo"} ["One of parameters [user_type] or [user_id] are required."]
    {:not_a_valid_param "foo"} ["Parameter [not_a_valid_param] was not recognized."]
    {:user_id "foo" :concept_id ["XXXXX"]} ["Concept-id [XXXXX] is not valid."]))

(defn get-permissions
  "Helper to get permissions with the current context and the specified username string or user type keyword and concept ids."
  [user & concept-ids]
  (json/parse-string
    (ac/get-permissions
      (u/conn-context)
      (merge {:concept_id concept-ids}
             (if (keyword? user)
               {:user_type (name user)}
               {:user_id user})))))

(def granule-metadata
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
				<Granule>
				    <GranuleUR>GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</GranuleUR>
				    <InsertTime>2012-01-11T10:00:00.000Z</InsertTime>
				    <LastUpdate>2012-01-19T18:00:00.000Z</LastUpdate>
				    <Collection>
				        <DataSetId>coll1 entry title</DataSetId>
				    </Collection>
              		<OnlineResources>
                      <OnlineResource>
                        <URL>http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4</URL>
                        <Description>OpenDAP URL</Description>
                        <Type>GET DATA : OPENDAP DATA (DODS)</Type>
              		    <MimeType>application/x-netcdf</MimeType>
                      </OnlineResource>
            		</OnlineResources>
				    <Orderable>true</Orderable>
				</Granule>")

(deftest concept-permission-check-test
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        ;; helper for easily creating a group, returns concept id
        create-group #(:concept_id (u/create-group token (u/make-group %)))
        ;; then create a group that contains our user, so we can find collections that grant access to this user
        user1-group (create-group {:name "groupwithuser1" :members ["user1"]})
        ;; create some collections
        coll1-umm (-> example-collection-record
                      (assoc :EntryTitle "coll1 entry title")
                      (assoc-in [:SpatialExtent :GranuleSpatialRepresentation] "NO_SPATIAL"))
        coll1-metadata (umm-spec/generate-metadata (u/conn-context) coll1-umm :echo10)
        coll1 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata coll1-metadata
                                      :concept-type :collection
                                      :provider-id "PROV1"
                                      :native-id "coll1"
                                      :revision-id 1}
                                     {"Echo-Token" token})
        gran1 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata granule-metadata
                                      :concept-type :granule
                                      :provider-id "PROV1"
                                      :native-id "gran1"
                                      :revision-id 1}
                                     {"Echo-Token" token})
        ;; local helpers to make the body of the test cleaner
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-collection-permissions #(get-permissions %1 (:concept-id coll1))
        get-granule-permissions #(get-permissions %1 (:concept-id gran1))]

    (testing "no permissions granted"
      (is (= {"C1200000001-PROV1" []}
             (get-collection-permissions :guest)))
      (is (= {"C1200000001-PROV1" []}
             (get-collection-permissions :registered)))
      (is (= {"C1200000001-PROV1" []}
             (get-collection-permissions "user1"))))

    (testing "collection level permissions"
      (let [acl-concept-id (create-acl {:group_permissions [{:permissions [:read :order]
                                                             :user_type :guest}]
                                        :catalog_item_identity {:name "coll1 read and order"
                                                                :collection_applicable true
                                                                :provider_id "PROV1"}})]
        (u/wait-until-indexed)

        (testing "for guest users"
          (is (= {"C1200000001-PROV1" ["read" "order"]}
                 (get-collection-permissions :guest)))
          (is (= {"C1200000001-PROV1" []}
                 (get-collection-permissions :registered)))
          (is (= {"C1200000001-PROV1" []}
                 (get-collection-permissions "user1"))))

        (testing "for registered users"
          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:read :order]
                                            :user_type :registered}]
                       :catalog_item_identity {:name "coll1 read and order"
                                               :collection_applicable true
                                               :provider_id "PROV1"}})
          (is (= {"C1200000001-PROV1" []}
                 (get-collection-permissions :guest)))
          (is (= {"C1200000001-PROV1" ["read" "order"]}
                 (get-collection-permissions :registered)))
          (is (= {"C1200000001-PROV1" ["read" "order"]}
                 (get-collection-permissions "user1"))))

        (testing "acls granting access to specific groups"

          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:read :order]
                                            :group_id user1-group}]
                       :catalog_item_identity {:name "coll1 read and order"
                                               :collection_applicable true
                                               :provider_id "PROV1"}})

          (testing "as a guest"
            (is (= {"C1200000001-PROV1" []}
                   (get-collection-permissions :guest))))

          (testing "as a registered user"
            (is (= {"C1200000001-PROV1" []}
                   (get-collection-permissions :registered))))

          (testing "as a user in the group"
            (is (= {"C1200000001-PROV1" ["read" "order"]}
                   (get-collection-permissions "user1"))))

          (testing "as a user not in the group"
            (is (= {"C1200000001-PROV1" []}
                   (get-collection-permissions "user2"))))

          (testing "as a user not in the group"
            (is (= {"C1200000001-PROV1" []}
                   (get-collection-permissions "notauser"))))

          (testing "with a complex ACL distributing permissions across multiple groups"
            (let [user2-group1 (create-group {:name "group1withuser2" :members ["user2"]})
                  user2-group2 (create-group {:name "group2withuser2" :members ["user2"]})]
              (u/wait-until-indexed)
              (create-acl {:group_permissions [{:permissions [:read] :group_id user2-group1}
                                               {:permissions [:order] :group_id user2-group2}]
                           :catalog_item_identity {:name "PROV1 complex ACL"
                                                   :collection_applicable true
                                                   :provider_id "PROV1"}})
              (is (= {"C1200000001-PROV1" ["read" "order"]}
                     (get-collection-permissions "user2"))))))))

    (testing "granule level permissions"
      (testing "no permissions granted"
        (is (= {"G1200000002-PROV1" []}
               (get-granule-permissions "user1")))))))

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
                            {:concept_id (:concept-id coll1) :user_id %1}))]

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
                 (get-permissions :guest)))
          (is (= {"C1200000001-PROV1" []}
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
