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
        coll2-umm (-> example-collection-record
                      (assoc :EntryTitle "coll2 entry title"
                             :ShortName "coll2"))
        coll2-metadata (umm-spec/generate-metadata (u/conn-context) coll2-umm :echo10)
        coll2 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata coll2-metadata
                                      :concept-type :collection
                                      :provider-id "PROV1"
                                      :native-id "coll2"
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
      (are [user permissions]
        (= {"C1200000001-PROV1" permissions}
           (get-collection-permissions user))
        :guest []
        :registered []
        "user1" []))

    (testing "collection level permissions"
      (let [acl-concept-id (create-acl {:group_permissions [{:permissions [:read :order]
                                                             :user_type :guest}]
                                        :catalog_item_identity {:name "coll1 read and order"
                                                                :collection_applicable true
                                                                :provider_id "PROV1"}})]
        (u/wait-until-indexed)

        (testing "for guest users"
          (are [user permissions]
            (= {"C1200000001-PROV1" permissions}
               (get-collection-permissions user))
            :guest ["read" "order"]
            :registered []
            "user1" []))

        (testing "for registered users"
          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:read :order]
                                            :user_type :registered}]
                       :catalog_item_identity {:name "coll1 read and order"
                                               :collection_applicable true
                                               :provider_id "PROV1"}})
          (are [user permissions]
            (= {"C1200000001-PROV1" permissions}
               (get-collection-permissions user))
            :guest []
            :registered ["read" "order"]
            "user1" ["read" "order"]))

        (testing "acls granting access to specific groups"

          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:read :order]
                                            :group_id user1-group}]
                       :catalog_item_identity {:name "coll1 read and order"
                                               :collection_applicable true
                                               :provider_id "PROV1"}})

          (are [user permissions]
            (= {"C1200000001-PROV1" permissions}
               (get-collection-permissions user))
            :guest []
            :registered []
            "user1" ["read" "order"]
            "user2" [])

          (testing "with a complex ACL distributing permissions across multiple groups"
            (let [user2-group1 (create-group {:name "group1withuser2" :members ["user2"]})
                  user2-group2 (create-group {:name "group2withuser1and2" :members ["user1" "user2"]})]
              (u/wait-until-indexed)
              (create-acl {:group_permissions [{:permissions [:read] :group_id user1-group}
                                               {:permissions [:read] :group_id user2-group1}
                                               {:permissions [:order] :group_id user2-group2}]
                           :catalog_item_identity {:name "PROV1 complex ACL"
                                                   :collection_applicable true
                                                   :provider_id "PROV1"}})
              (are [user permissions]
                (= {"C1200000001-PROV1" permissions}
                   (get-collection-permissions user))
                :guest []
                :registered []
                "user1" ["read" "order"]
                "user2" ["read" "order"]))))

        (testing "acls granting access to collection by collection identifier"
          (testing "by entry title"
            (create-acl
              {:group_permissions [{:permissions [:read]
                                    :user_type :guest}]
               :catalog_item_identity {:name "coll2 guest read"
                                       :collection_applicable true
                                       :collection_identifier {:entry_titles ["coll2 entry title"]}
                                       :provider_id "PROV1"}})
            (are [user permissions]
              (= {"C1200000002-PROV1" permissions}
                 (get-permissions user "C1200000002-PROV1"))
              :guest ["read"]
              :registered [])))))

    (testing "granule level permissions"
      (testing "no permissions granted"
        (is (= {"G1200000003-PROV1" []}
               (get-granule-permissions "user1")))))))

(deftest collection-identifier-access-value-test
  (let [token (e/login (u/conn-context) "user1" [])
        ;; one collection with a low access value
        coll1-umm (-> example-collection-record
                      (assoc :EntryTitle "coll1 entry title")
                      (update-in [:AccessConstraints] assoc :Value "1"))
        coll1 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata (umm-spec/generate-metadata (u/conn-context) coll1-umm :echo10)
                                      :concept-type :collection
                                      :provider-id "PROV1"
                                      :native-id "coll1"
                                      :revision-id 1}
                                     {"Echo-Token" token})
        ;; one with an intermediate access value
        coll2-umm (-> example-collection-record
                      (assoc :EntryTitle "coll2 entry title"
                             :ShortName "coll2")
                      (update-in [:AccessConstraints] assoc :Value "4"))
        coll2 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata (umm-spec/generate-metadata (u/conn-context) coll2-umm :echo10)
                                      :concept-type :collection
                                      :provider-id "PROV1"
                                      :native-id "coll2"
                                      :revision-id 1}
                                     {"Echo-Token" token})
        ;; one with a higher access value
        coll3-umm (-> example-collection-record
                      (assoc :EntryTitle "coll3 entry title"
                             :ShortName "coll3")
                      (update-in [:AccessConstraints] assoc :Value "9"))
        coll3 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata (umm-spec/generate-metadata (u/conn-context) coll3-umm :echo10)
                                      :concept-type :collection
                                      :provider-id "PROV1"
                                      :native-id "coll3"
                                      :revision-id 1}
                                     {"Echo-Token" token})
        create-acl #(:concept_id (ac/create-acl (u/conn-context) % {:token token}))
        update-acl #(ac/update-acl (u/conn-context) %1 %2 {:token token})
        get-coll-permissions #(get-permissions :guest "C1200000000-PROV1" "C1200000001-PROV1" "C1200000002-PROV1")]
    (u/wait-until-indexed)
    (is (= {"C1200000000-PROV1" []
            "C1200000001-PROV1" []
            "C1200000002-PROV1" []}
           (get-coll-permissions)))
    (let [acl-id (create-acl
                   {:group_permissions [{:permissions [:read]
                                         :user_type :guest}]
                    :catalog_item_identity {:name "coll2 guest read"
                                            :collection_applicable true
                                            :collection_identifier {:access_value {:min_value 1 :max_value 10}}
                                            :provider_id "PROV1"}})]
      (is (= {"C1200000000-PROV1" ["read"]
              "C1200000001-PROV1" ["read"]
              "C1200000002-PROV1" ["read"]}
             (get-coll-permissions)))

      (update-acl acl-id {:group_permissions [{:permissions [:read]
                                               :user_type :guest}]
                          :catalog_item_identity {:name "coll2 guest read"
                                                  :collection_applicable true
                                                  :collection_identifier {:access_value {:min_value 4 :max_value 10}}
                                                  :provider_id "PROV1"}})

      (is (= {"C1200000000-PROV1" []
              "C1200000001-PROV1" ["read"]
              "C1200000002-PROV1" ["read"]}
             (get-coll-permissions)))

      (update-acl acl-id {:group_permissions [{:permissions [:read]
                                               :user_type :guest}]
                          :catalog_item_identity {:name "coll2 guest read"
                                                  :collection_applicable true
                                                  :collection_identifier {:access_value {:min_value 4 :max_value 5}}
                                                  :provider_id "PROV1"}})

      (is (= {"C1200000000-PROV1" []
              "C1200000001-PROV1" ["read"]
              "C1200000002-PROV1" []}
             (get-coll-permissions))))))

;; TODO CMR-2900 add tests for access value and temporal ACL conditions

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
        get-coll1-permissions #(get-permissions % (:concept-id coll1))]

    (testing "no permissions granted"
      (are [user permissions]
        (= {"C1200000001-PROV1" permissions}
           (get-coll1-permissions user))
        :guest []
        :registered []
        "user1" []))

    (testing "provider level permissions"
      (let [acl {:group_permissions [{:permissions [:update :delete]
                                      :user_type :guest}]
                 :provider_identity {:provider_id "PROV1"
                                     :target "INGEST_MANAGEMENT_ACL"}}
            acl-concept-id (:concept_id (create-acl acl))]
        (u/wait-until-indexed)

        (are [user permissions]
          (= {"C1200000001-PROV1" permissions}
             (get-coll1-permissions user))
          :guest ["update" "delete"]
          :registered []
          "user1" [])

        (testing "granted to registered users"
          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:update :delete]
                                            :user_type :registered}]
                       :provider_identity {:provider_id "PROV1"
                                           :target "INGEST_MANAGEMENT_ACL"}})

          (are [user permissions]
            (= {"C1200000001-PROV1" permissions}
               (get-coll1-permissions user))
            :guest []
            :registered ["update" "delete"]
            "user1" ["update" "delete"]))

        (testing "granted to specific groups"
          (update-acl acl-concept-id
                      {:group_permissions [{:permissions [:update :delete]
                                            :group_id created-group-concept-id}]
                       :provider_identity {:provider_id "PROV1"
                                           :target "INGEST_MANAGEMENT_ACL"}})

          (are [user permissions]
            (= {"C1200000001-PROV1" permissions}
               (get-coll1-permissions user))
            :guest []
            :registered []
            "user1" ["update" "delete"]
            "user2" []))))))
