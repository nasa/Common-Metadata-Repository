(ns cmr.system-int-test.search.autocomplete.suggestion-permissions-test
 "Tests permissions for autocomplete suggestions "
 (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-spec]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as transmit-config]))

(defn extract-autocomplete-entries
 "Helper to extract entries from autocomplete response"
 [response]
 (get-in response [:feed :entry]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                                            {:grant-all-search? false})]))

(deftest suggestion-permissions-test
 (testing "Suggestions respect collection access permissions"
  ;; Create groups for our test
  (let [authorized-group-id (e/get-or-create-group (s/context) "authorized-group")
         
        ;; Create restricted collection with specific data center to test for suggestions
        _ (d/ingest-umm-spec-collection
           "PROV1"
           (data-umm-spec/collection
           {:EntryTitle "Restricted Collection"
            :ShortName "RESTRICTED"
            :DataCenters [(data-umm-spec/data-center 
                           {:Roles ["ARCHIVER"] 
                            :ShortName "RESTRICTED-ORG"})]})
           {:format :umm-json
            :validate-keywords false})

        ;; Create another restricted collection with different data center, this one will will delete later
        second-restricted-collection (d/ingest-umm-spec-collection
                          "PROV1"
                          (data-umm-spec/collection
                          {:EntryTitle "Second Restricted Collection"
                            :ShortName "SECOND RESTRICTED COLLECTION"
                            :DataCenters [(data-umm-spec/data-center 
                                          {:Roles ["ARCHIVER"] 
                                           :ShortName "RESTRICTED-ORG2"})]})
                          {:format :umm-json
                            :validate-keywords false})

        ;; Create another restricted collection with different data center
        _ (d/ingest-umm-spec-collection
           "PROV1"
           (data-umm-spec/collection
           {:EntryTitle "Third Restricted Collection"
            :ShortName "THIRD RESTRICTED COLLECTION"
            :DataCenters [(data-umm-spec/data-center 
                           {:Roles ["ARCHIVER"] 
                            :ShortName "RESTRICTED-ORG3"})]})
           {:format :umm-json
            :validate-keywords false})

        ;; Create public collection with different data center
        _ (d/ingest-umm-spec-collection
           "PROV1"
           (data-umm-spec/collection
           {:EntryTitle "Public Collection"
           :ShortName "PUBLIC"
           :DataCenters [(data-umm-spec/data-center 
                         {:Roles ["ARCHIVER"] 
                         :ShortName "PUBLIC-ORG"})]})
           {:format :umm-json
           :validate-keywords false})       
         
        ;; Grant explicit permission to only the authorized group for restricted collections
        _ (e/grant-group (s/context) 
                         authorized-group-id 
                         (e/coll-catalog-item-id "PROV1" (e/coll-id ["Restricted Collection"])))

        _ (e/grant-group (s/context) 
                         authorized-group-id 
                         (e/coll-catalog-item-id "PROV1" (e/coll-id ["Second Restricted Collection"])))

        _ (e/grant-group (s/context) 
                         authorized-group-id 
                         (e/coll-catalog-item-id "PROV1" (e/coll-id ["Third Restricted Collection"])))
         
        ;; Grant guest permission to the public collection
        _ (e/grant-guest (s/context) 
                         (e/coll-catalog-item-id "PROV1" (e/coll-id ["Public Collection"])))

        ;; Grant registered users permission to the public collection
        _ (e/grant-registered-users 
           (s/context) 
           (e/coll-catalog-item-id "PROV1" (e/coll-id ["Public Collection"])))

        authorized-token (e/login (s/context) "authorized-user" [authorized-group-id])]

    ;; Delete second restricted collection collection, testing CMR-10362 solution 
    (ingest/delete-concept (d/item->concept second-restricted-collection :echo10))

    ;; Index the collections and suggestions
    (index/wait-until-indexed)
    (ingest/reindex-collection-permitted-groups transmit-config/mock-echo-system-token)
    (index/wait-until-indexed)
    (index/reindex-suggestions)
    (index/wait-until-indexed)
    (search/clear-caches)

    (testing "Guest user should not see suggestions for restricted collection but should see for public collection"
      (let [guest-results (extract-autocomplete-entries 
                           (search/get-autocomplete-json "q=ORG"))]
        ;; Should contain only the public organization
        (is (= #{"PUBLIC-ORG"}
                (->> guest-results
                     (map :value)
                     set)))))
     
    (testing "Authorized user should see suggestions for all collections"
      (let [authorized-results (extract-autocomplete-entries 
                                (search/get-autocomplete-json 
                                 "q=ORG" 
                                 {:headers {:authorization authorized-token}}))]
        ;; Should find all organizations in the results except the deleted collection's organization
        (is (= #{"RESTRICTED-ORG" "PUBLIC-ORG" "RESTRICTED-ORG3"}
                (->> authorized-results
                     (map :value)
                     set)))))

    ;; Delete all catalog item permissions, making all collections inaccessible      
    (e/ungrant-by-search (s/context) {:identity-type "catalog_item"})

    ;; Re-index the collections and suggestions
    (index/wait-until-indexed)
    (ingest/reindex-collection-permitted-groups transmit-config/mock-echo-system-token)
    (index/wait-until-indexed)
    (index/reindex-suggestions)
    (index/wait-until-indexed)
    (search/clear-caches)

    (let [unauthorized-token (e/login (s/context) "authorized-user" [authorized-group-id])]
      (testing "After revoking all permissions, user should not see suggestions for restricted collections"
        (let [unauthorized-results (extract-autocomplete-entries 
                                    (search/get-autocomplete-json 
                                     "q=RESTRICTED-ORG" 
                                     {:headers {:authorization unauthorized-token}}))]
          (is (empty? unauthorized-results))))
      
        (let [unauthorized-results (extract-autocomplete-entries 
                                    (search/get-autocomplete-json 
                                     "q=PUBLIC-ORG" 
                                     {:headers {:authorization unauthorized-token}}))]
          (is (empty? unauthorized-results)))

      ;; Now grant guest permission to the restricted collection
      (e/grant-guest (s/context) 
                     (e/coll-catalog-item-id "PROV1" (e/coll-id ["Restricted Collection"])))
      
      ;; Re-index and clear caches
      (index/wait-until-indexed)
      (ingest/reindex-collection-permitted-groups transmit-config/mock-echo-system-token)
      (index/wait-until-indexed)
      (index/reindex-suggestions)
      (index/wait-until-indexed)
      (search/clear-caches)
      
      (testing "After granting guest permission, users should see suggestions for previously restricted collection"
        (let [guest-results (extract-autocomplete-entries 
                             (search/get-autocomplete-json "q=RESTRICTED-ORG"))
              unauthorized-results (extract-autocomplete-entries 
                                    (search/get-autocomplete-json 
                                     "q=RESTRICTED-ORG" 
                                     {:headers {:authorization unauthorized-token}}))]

          ;; Should now contain the previously restricted organization for guest users, but not the other restricted organizations
          ;; nor the public organization that had its permissions revoked
          (is (= #{"RESTRICTED-ORG"}
              (->> guest-results
                   (map :value)
                   set)))

          ;; Because :contains-public-collections is true, unauthorized users should still see the restricted organization
          ;; that is now public even without permmissions specific to registered users or this users group, 
          ;; the other restricted organizations and the public organization should not be visible
          (is (= #{"RESTRICTED-ORG"}
              (->> unauthorized-results
                   (map :value)
                   set)))))))))
