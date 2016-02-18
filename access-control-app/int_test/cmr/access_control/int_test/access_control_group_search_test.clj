(ns cmr.access-control.int-test.access-control-group-search-test
  "Tests searching for access control groups"
    (:require [clojure.test :refer :all]
              [cmr.mock-echo.client.echo-util :as e]
              [cmr.common.util :as util :refer [are2]]
              [cmr.access-control.int-test.access-control-test-util :as u]))

(use-fixtures :each
              (u/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"} ["user1" "user2" "user3" "user4" "user5"])
              (u/grant-all-group-fixture ["prov1guid" "prov2guid"]))
(use-fixtures :once (u/int-test-fixtures))

(defn ingest-group
  "Ingests the group and returns a group such that it can be matched with a search result."
  [token attributes members]
  (let [group (u/make-group attributes)
        {:keys [concept-id status revision-id] :as resp} (u/create-group-with-members token group members)]
    (when-not (= status 200)
      (throw (Exception. (format "Unexpected status [%s] when creating group %s" status (pr-str resp)))))
    (assoc group
           :member-count (count members)
           :concept-id concept-id
           :revision-id revision-id)))

(defn sort-groups
  "Sorts the groups by provider and then by name within a provider. System groups come last."
  [groups]
  (let [groups-by-prov (group-by :provider-id groups)
        ;; Put system groups at the end
        provider-ids-in-order (conj (vec (sort (remove nil? (keys groups-by-prov)))) nil)]
    (for [provider provider-ids-in-order
          group (sort-by :name (groups-by-prov provider))]
      group)))

(deftest invalid-search-test
  (let [token (e/login (u/conn-context) "user1")]
    (is (= {:status 400
            :errors ["The mime types specified in the accept header [application/text] are not supported."]}
           (u/search token {} {:http-options {:accept "application/text"}})))
    (is (= {:status 400 :errors ["Parameter [foo] was not recognized."]}
           (u/search token {:foo "bar"})))
    (is (= {:status 400 :errors ["Parameter [options[provider]] must include a nested key, options[provider][...]=value."]}
           (u/search token {:provider "foo"
                            "options[provider]" "foo"})))
    (is (= {:status 400 :errors ["Parameter [options] must include a nested key, options[...]=value."]}
           (u/search token {"options" "bar"})))
    (is (= {:status 400 :errors ["Option [foo] is not supported for param [provider]"]}
           (u/search token {:provider "PROV1"
                            "options[provider][foo]" "bar"})))

    ;; Members search is always case insensitive
    (is (= {:status 400 :errors ["Option [ignore_case] is not supported for param [member]"]}
           (u/search token {:member "foo"
                            "options[member][ignore_case]" true})))))

(deftest group-search-test
  (let [token (e/login (u/conn-context) "user1")
        cmr-group1 (ingest-group token {:name "group1"} ["user1"])
        cmr-group2 (ingest-group token {:name "group2"} ["USER1" "user2"])
        cmr-group3 (ingest-group token {:name "group3"} nil)
        prov1-group1 (ingest-group token {:name "group1" :provider-id "PROV1"} ["user1"])
        prov1-group2 (ingest-group token {:name "group2" :provider-id "PROV1"} ["user1" "user3"])
        prov2-group1 (ingest-group token {:name "group1" :provider-id "PROV2"} ["user2"])
        prov2-group2 (ingest-group token {:name "group2" :provider-id "PROV2"} ["user2" "user3"])
        cmr-groups [cmr-group1 cmr-group2 cmr-group3]
        prov1-groups [prov1-group1 prov1-group2]
        prov2-groups [prov2-group1 prov2-group2]
        prov-groups (concat prov1-groups prov2-groups)
        all-groups (concat cmr-groups prov1-groups prov2-groups)]
    (u/wait-until-indexed)

    (testing "Search by member"
      (are2 [expected-groups params]
        (is (= {:status 200 :items (sort-groups expected-groups) :hits (count expected-groups)}
               (select-keys (u/search token params) [:status :items :hits :errors])))

        "Normal case is case insensitive"
        [cmr-group1 cmr-group2 prov1-group1 prov1-group2] {:member "UsEr1"}

        "Pattern"
        [cmr-group1 cmr-group2 prov1-group1 prov1-group2 prov2-group1 prov2-group2]
        {:member "user*" "options[member][pattern]" true}

        "Multiple members"
        [cmr-group2 prov1-group2 prov2-group1 prov2-group2] {:member ["user3" "user2"]}

        "Multiple members - AND false (default)"
        [cmr-group2 prov1-group2 prov2-group1 prov2-group2] {:member ["user3" "user2"]
                                                             "options[member][and]" false}

        "Multiple members - AND'd"
        [prov2-group2] {:member ["user3" "user2"] "options[member][and]" true}))



    (testing "Search by name"
      (are2 [expected-groups params]
        (is (= {:status 200 :items (sort-groups expected-groups) :hits (count expected-groups)}
               (select-keys (u/search token params) [:status :items :hits])))

        "Normal case insensitive"
        [cmr-group1 prov1-group1 prov2-group1] {:name "Group1"}

        "Case sensitive - no match"
        [] {:name "Group1" "options[name][ignore_case]" false}
        "Case sensitive - matches"
        [cmr-group1 prov1-group1 prov2-group1] {:name "group1" "options[name][ignore_case]" false}

        "Pattern - no match"
        [] {:name "*oup" "options[name][pattern]" true}
        "Pattern - matches"
        all-groups {:name "Gr?up*" "options[name][pattern]" true}

        "Multiple matches"
        [cmr-group1 prov1-group1 prov2-group1
         cmr-group2 prov1-group2 prov2-group2] {:name ["group1" "group2"]}))


    (testing "Search by provider"
      (are2 [expected-groups params]
        (is (= {:status 200 :items (sort-groups expected-groups) :hits (count expected-groups)}
               (select-keys (u/search token params) [:status :items :hits])))

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
        (concat cmr-groups prov1-groups) {:provider ["CMR" "prov1"]}))))



