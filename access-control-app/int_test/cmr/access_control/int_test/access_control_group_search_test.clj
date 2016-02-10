(ns cmr.access-control.int-test.access-control-group-search-test
  "Tests searching for access control groups"
    (:require [clojure.test :refer :all]
              [cmr.mock-echo.client.echo-util :as e]
              [cmr.access-control.int-test.access-control-test-util :as u]))

(use-fixtures :each (u/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"} ["user1" "user2" "user3" "user4" "user5"]))
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

(deftest group-search-test
  (let [token (e/login (u/conn-context) "user1")
        cmr-group1 (ingest-group token {:name "group1"} ["user1"])
        cmr-group2 (ingest-group token {:name "group2"} ["user1" "user2"])
        cmr-group3 (ingest-group token {:name "group3"} nil)
        prov1-group1 (ingest-group token {:name "group1" :provider-id "PROV1"} ["user1"])
        prov1-group2 (ingest-group token {:name "group2" :provider-id "PROV1"} ["user1" "user3"])
        prov2-group1 (ingest-group token {:name "group1" :provider-id "PROV2"} ["user2"])
        prov2-group2 (ingest-group token {:name "group2" :provider-id "PROV2"} ["user2" "user3"])
        cmr-groups [cmr-group1 cmr-group2 cmr-group3]
        prov1-groups [prov1-group1 prov1-group2]
        prov2-groups [prov2-group1 prov2-group2]
        all-groups (concat cmr-groups prov1-groups prov2-groups)]
    (u/wait-until-indexed)
    (are [expected-groups params]
      (is (= {:status 200 :items (sort-groups expected-groups) :hits (count expected-groups)}
             (select-keys (u/search params) [:status :items :hits])))
      all-groups {})))








