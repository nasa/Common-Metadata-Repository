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
     [cmr.transmit.config :as transmit-config]))

(use-fixtures :each
              (fixtures/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"}
                                      ["user1" "user2" "user3" "user4" "user5"])
              (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"]))
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
                                       "options[member][ignore_case]" true})))))

(defn expected-search-response
  "Returns the expected search response for a set of groups that matches a search result."
  [expected-groups include-members?]
  (let [groups (map (fn [group]
                      (as-> group g
                            (assoc g :member_count (count (:members g)))
                            (if include-members? g (dissoc g :members))))
                    expected-groups)]
    {:status 200 :items (sort-groups groups) :hits (count groups)}))

(deftest group-search-test
  (let [token (e/login (u/conn-context) "user1")
        existing-admin-group (-> (u/search-for-groups transmit-config/mock-echo-system-token {:include_members true})
                                 :items
                                 first)
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
    (u/wait-until-indexed)

    (testing "Search by member"
      (are3 [expected-groups params]
        (is (= (expected-search-response expected-groups (:include_members params))
               (select-keys (u/search-for-groups token params) [:status :items :hits :errors])))

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
               (select-keys (u/search-for-groups token params) [:status :items :hits])))

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
               (select-keys (u/search-for-groups token params) [:status :items :hits])))

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
              :errors ["Parameter [echo_token] was not recognized."]}
             (u/search-for-groups token {"Echo-Token" "true"}))))))
