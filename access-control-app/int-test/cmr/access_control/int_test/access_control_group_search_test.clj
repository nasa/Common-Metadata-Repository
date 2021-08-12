(ns cmr.access-control.int-test.access-control-group-search-test
  "Tests searching for access control groups"
    (:require
     [clojure.set :as set]
     [clojure.test :refer :all]
     [cmr.access-control.int-test.fixtures :as fixtures]
     [cmr.access-control.test.bootstrap :as bootstrap]
     [cmr.access-control.test.util :as u]
     [cmr.common.util :as util :refer [are3]]
     [cmr.mock-echo.client.echo-util :as e]
     [cmr.transmit.access-control :as access-control]
     [cmr.transmit.config :as transmit-config]))

(use-fixtures :each
              (fixtures/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"}
                                      ["user1" "user2" "user3" "user4" "user5"])
              (fixtures/grant-all-group-fixture ["PROV1" "PROV2"]))
(use-fixtures :once (fixtures/int-test-fixtures))

(defn sort-groups
  "Sorts the groups by provider and then by name within a provider. System groups come last."
  [groups]
  (let [groups-by-prov (group-by :provider_id groups)
        ;; Put system groups at the end
        provider-ids-in-order (conj (vec (sort (remove nil? (keys groups-by-prov)))) nil)]
    (for [provider provider-ids-in-order
          group (sort-by :name (groups-by-prov provider))]
      group)))

(deftest invalid-search-test
  (let [token (e/login (u/conn-context) "user1")]
    (is (= {:status 400
            :errors ["The mime types specified in the accept header [application/text] are not supported."]}
           (u/search-for-groups token {} {:http-options {:accept "application/text"}})))
    (is (= {:status 400 :errors ["Parameter [foo] was not recognized."]}
           (u/search-for-groups token {:foo "bar"})))
    (is (= {:status 400 :errors ["Parameter [options[provider]] must include a nested key, options[provider][...]=value."]}
           (u/search-for-groups token {:provider "foo"
                                       "options[provider]" "foo"})))
    (is (= {:status 400 :errors ["Parameter [options] must include a nested key, options[...]=value."]}
           (u/search-for-groups token {"options" "bar"})))
    (is (= {:status 400 :errors ["Option [foo] is not supported for param [provider]"]}
           (u/search-for-groups token {:provider "PROV1"
                                       "options[provider][foo]" "bar"})))
    (is (= {:status 400 :errors ["Parameter include_members must take value of true or false but was [foo]"]}
           (u/search-for-groups token {:include_members "foo"})))

    ;; Members search is always case insensitive
    (is (= {:status 400 :errors ["Option [ignore_case] is not supported for param [member]"]}
           (u/search-for-groups token {:member "foo"
                                       "options[member][ignore_case]" true})))

    (is (= {:status 400 :errors ["Option [ignore_case] is not supported for param [concept_id]"]}
           (u/search-for-groups token {:concept_id "AG12345-PROV"
                                       "options[concept_id][ignore_case]" true})))
    (is (= {:status 400 :errors ["Option [and] is not supported for param [concept_id]"]}
           (u/search-for-groups token {:concept_id "AG12345-PROV"
                                       "options[concept_id][and]" true})))
    (is (= {:status 400 :errors ["Option [pattern] is not supported for param [concept_id]"]}
           (u/search-for-groups token {:concept_id "AG12345-PROV"
                                       "options[concept_id][pattern]" true})))))

(defn expected-search-response
  "Returns the expected search response for a set of groups that matches a search result."
  [expected-groups include-members?]
  (let [groups (map (fn [group]
                      (as-> group g
                            (assoc g :member_count (count (:members g)))
                            (if include-members? g (dissoc g :members))))
                    expected-groups)]
    {:status 200 :items (sort-groups groups) :hits (count groups)}))

(defn get-existing-admin-group
  "Returns the bootstrapped administrators group"
  []
  (-> (u/search-for-groups transmit-config/mock-echo-system-token {:include_members true})
      :items
      first))

(defn search-for-groups
  "Searches for groups with the token and params. Returns a response that can be used for comparing"
  [token params]
  (select-keys (u/search-for-groups token params) [:status :items :hits :errors]))

(deftest group-reindexing-test
  (u/without-publishing-messages
    (let [token (e/login (u/conn-context) "user1")
          existing-admin-group (get-existing-admin-group)
          cmr-group (u/ingest-group token {:name "cmr-group"} ["user1"])
          deleted-group (u/ingest-group token {:name "deleted-group"})
          prov-group (u/ingest-group token {:name "prov-group" :provider_id "PROV1"} ["user1"])
          all-groups [existing-admin-group cmr-group prov-group]]

      ;; Delete the group so we can test with a tombstone.
      (is (= 200 (:status (u/delete-group token (:concept_id deleted-group)))))
      (u/wait-until-indexed)

      ;; Group indexing is synchronous so we must manually unindex all groups
      (u/unindex-all-groups)

      (is (= (expected-search-response [] true)
             (search-for-groups token {:include_members true}))
          "Found groups after unindexing all groups")

      (access-control/reindex-groups (u/conn-context))
      (u/wait-until-indexed)

      ;; Now all groups should be found.
      (is (= (expected-search-response all-groups true)
             (search-for-groups token {:include_members true}))
          "Did not find groups after reindexing all groups."))))

;; This tests that groups are synchronously indexed correctly.
(deftest synchronous-group-indexing-test
  (u/without-publishing-messages
   (let [token (e/login (u/conn-context) "user1")
         existing-admin-group (get-existing-admin-group)
         group1 (u/ingest-group token {:name "group1"})
         group2 (u/ingest-group token {:name "group2" :provider_id "PROV1"})
         group3 (u/ingest-group token {:name "group3" :provider_id "PROV1"})]

     (testing "Created groups should be found"
       (is (= (expected-search-response [existing-admin-group group1 group2 group3] true)
              (search-for-groups token {:include_members true}))))

     (testing "Deleted groups are unindexed"
       (let [resp (u/delete-group token (:concept_id group3))]
         (is (= 200 (:status resp)) (pr-str resp)))
       (is (= (expected-search-response [existing-admin-group group1 group2] true)
              (search-for-groups token {:include_members true}))))

     (let [;; Group 2 will have an updated description
           updated-group2 (assoc group2 :description "Updated desc" :revision_id 2)
           resp (u/update-group token (:concept_id group2) updated-group2)
           _ (is (= 200 (:status resp)) (pr-str resp))
           ;; Group 1 will have new members
           resp (u/add-members token (:concept_id group1) ["user1" "user2"])
           _ (is (= 200 (:status resp)) (pr-str resp))
           updated-group1 (assoc group1 :members ["user1" "user2"] :revision_id 2)]

       (testing "Updated groups are indexed correctly"
         (is (= (expected-search-response [existing-admin-group updated-group1 updated-group2] true)
                (search-for-groups token {:include_members true}))))

       (let [updated-group1 (assoc group1 :members ["user1"] :revision_id 3)
             resp (u/remove-members token (:concept_id group1) ["user2"])
             _ (is (= 200 (:status resp)) (pr-str resp))]

         (testing "Groups with removed members are indexed correctly"
           (is (= (expected-search-response [existing-admin-group updated-group1 updated-group2] true)
                  (search-for-groups token {:include_members true})))))))))


(deftest group-search-test
  (let [token (e/login (u/conn-context) "user1")
        existing-admin-group (get-existing-admin-group)
        cmr-group1 (u/ingest-group token {:name "group1"} ["user1"])
        cmr-group2 (u/ingest-group token {:name "group2"} ["USER1" "user2"])
        cmr-group3 (u/ingest-group token {:name "group3"} nil)
        prov1-group1 (u/ingest-group token {:name "group1" :provider_id "PROV1"} ["user1"])
        prov1-group2 (u/ingest-group token {:name "group2" :provider_id "PROV1"} ["user1" "user3"])
        prov2-group1 (u/ingest-group token {:name "group1" :provider_id "PROV2"} ["user2"])
        prov2-group2 (u/ingest-group token {:name "group2" :provider_id "PROV2"} ["user2" "user3"])
        cmr-added-groups [cmr-group1 cmr-group2 cmr-group3]
        cmr-groups (cons existing-admin-group cmr-added-groups)
        prov1-groups [prov1-group1 prov1-group2]
        prov2-groups [prov2-group1 prov2-group2]
        prov-groups (concat prov1-groups prov2-groups)
        all-groups (concat cmr-groups prov1-groups prov2-groups)]

    (testing "Search by concept id"
      (are3 [expected-groups params]
        (is (= (expected-search-response expected-groups (:include_members params))
               (search-for-groups token params)))

        "Multiple is OR'd"
        [cmr-group1 prov1-group1 prov1-group2] {:concept-id (map :concept_id
                                                                 [cmr-group1 prov1-group1 prov1-group2])}
        "Single"
        [cmr-group1] {:concept-id [(:concept_id cmr-group1)]}))

    (testing "Search by member"
      (are3 [expected-groups params]
        (is (= (expected-search-response expected-groups (:include_members params))
               (search-for-groups token params)))

        "Include members"
        all-groups {:include_members true}

        "Normal case is case insensitive"
        [cmr-group1 cmr-group2 prov1-group1 prov1-group2] {:member "UsEr1" :include_members false}

        "Pattern"
        [existing-admin-group cmr-group1 cmr-group2 prov1-group1 prov1-group2 prov2-group1 prov2-group2]
        {:member "user*" "options[member][pattern]" true}

        "Multiple members"
        [cmr-group2 prov1-group2 prov2-group1 prov2-group2] {:member ["user3" "user2"]}

        "Multiple members - AND false (default)"
        [cmr-group2 prov1-group2 prov2-group1 prov2-group2] {:member ["user3" "user2"]
                                                             "options[member][and]" false}

        "Multiple members - AND'd"
        [prov2-group2] {:member ["user3" "user2"] "options[member][and]" true}))

    (testing "Search by name"
      (are3 [expected-groups params]
        (is (= (expected-search-response expected-groups false)
               (search-for-groups token params)))

        "Normal case insensitive"
        [cmr-group1 prov1-group1 prov2-group1] {:name "Group1"}

        "Case sensitive - no match"
        [] {:name "Group1" "options[name][ignore_case]" false}
        "Case sensitive - matches"
        [cmr-group1 prov1-group1 prov2-group1] {:name "group1" "options[name][ignore_case]" false}

        "Pattern - no match"
        [] {:name "*oupx" "options[name][pattern]" true}
        "Pattern - matches"
        (concat cmr-added-groups prov1-groups prov2-groups) {:name "*Gr?up*" "options[name][pattern]" true}

        "Multiple matches"
        [cmr-group1 prov1-group1 prov2-group1
         cmr-group2 prov1-group2 prov2-group2] {:name ["group1" "group2"]}))

    (testing "Search by provider"
      (are3 [expected-groups params]
        (is (= (expected-search-response expected-groups false)
               (search-for-groups token params)))

        "No parameters finds all groups"
        all-groups {}

        "Single provider id"
        prov1-groups {:provider "PROV1"}

        "Provider id is case insensitve"
        prov1-groups {:provider "prov1"}

        "Provider id is case sensitive"
        [] {:provider "prov1" "options[provider][ignore_case]" false}

        "Provider id is case insensitive"
        prov1-groups {:provider "prov1" "options[provider][ignore_case]" true}

        "Provider id pattern"
        prov-groups {:provider "prov?" "options[provider][pattern]" true}

        "Multiple provider ids - 1 matches"
        prov1-groups {:provider ["PROV1" "PROV3"]}

        "Multiple provider ids - 2 matches"
        prov-groups {:provider ["PROV1" "PROV2"]}

        "Provider id that does not match"
        [] {:provider "not_exist"}

        "System level groups"
        cmr-groups {:provider "CMR"}

        "System level groups - case insensitive"
        cmr-groups {:provider "cmr"}

        "System level groups are always case insensitive"
        cmr-groups {:provider "cmr" "options[provider][ignore_case]" false}

        "System level and provider groups"
        (concat cmr-groups prov1-groups) {:provider ["CMR" "prov1"]}))

    (testing "with invalid parameters"
      (is (= {:status 400
              :errors ["Parameter [authorization] was not recognized."]}
             (u/search-for-groups token {"Authorization" "true"}))))))
