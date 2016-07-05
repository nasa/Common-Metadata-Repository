(ns cmr.access-control.int-test.permission-check-test
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

(def provider-acl
  {:legacy_guid "ABCD-EFG-HIJK-LMNOP"
   :group_permissions [{:group_id "groupwithuser1"
                        :permissions ["read"]}]
   :provider_identity {:name "Group with user 1 provider read access"
                       :provider_id "PROV1"
                       :target "INGEST_MANAGEMENT_ACL"}})

(def catalog-item-acl
  {:group_permissions [{:user_type "guest"
                        :permissions ["read"]}]
   :catalog_item_identity {:name "coll1 guest read ACL"
                           :provider_id "PROV1"
                           :collection_identifier {:entry_titles ["coll1"]}}})

(deftest permission-check-test
  (e/grant-system-group-permissions-to-group (u/conn-context) "group-create-group" :create)
  (e/grant-groups-ingest (u/conn-context) "prov2guid" ["group-create-group"])
  (let [token (e/login (u/conn-context) "user1" ["group-create-group"])
        group (u/make-group {:name "groupwithuser1" :members ["user1"]})
        created-group (u/create-group token group)
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
        coll2-umm (assoc example-collection-record :EntryTitle "coll2 entry title")
        coll2-metadata (umm-spec/generate-metadata (u/conn-context) coll2-umm :echo10)
        coll2 (ingest/ingest-concept (u/conn-context)
                                     {:format "application/echo10+xml"
                                      :metadata coll2-metadata
                                      :concept-type :collection
                                      :provider-id "PROV2"
                                      :native-id "coll2"
                                      :revision-id 1}
                                     {"Echo-Token" token})
        acl-1 {:group_permissions [{:permissions [:read :order]
                                    :user_type :registered}]
               :catalog_item_identity {:name "coll1 guest read"
                                       :collection_applicable true
                                       :provider_id "PROV1"}}
        acl-2 {:group_permissions [{:permissions [:update :delete]
                                    :group_id (:concept_id created-group)}]
               :provider_identity {:name "PROV1 group update, delete"
                                   :provider_id "PROV1"
                                   :target "INGEST_MANAGEMENT_ACL"}}]
    (ac/create-acl (u/conn-context) acl-1 {:token token})
    (ac/create-acl (u/conn-context) acl-2 {:token token})
    (u/wait-until-indexed)
    (is (= {"C1200000001-PROV1" ["read" "update" "delete" "order"]}
           (json/parse-string
             (ac/get-permissions (u/conn-context) {:concept_ids (:concept-id coll1)
                                                   :user_id "user1"}))))))
