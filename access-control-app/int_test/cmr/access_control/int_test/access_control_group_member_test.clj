(ns cmr.access-control.int-test.access-control-group-member-test
  (:require [clojure.test :refer :all]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.access-control.int-test.fixtures :as fixtures]
            [cmr.access-control.test.util :as u]))

(use-fixtures :once (fixtures/int-test-fixtures))
(use-fixtures :each
              (fixtures/reset-fixture {"prov1guid" "PROV1"} ["user1" "user2" "user3" "user4" "user5"])
              (fixtures/grant-all-group-fixture ["PROV1"]))

(defn- assert-group-member-count-correct
  "Asserts that when retrieving the group the correct number of members is returned."
  [token concept-id num-expected]
  (is (= num-expected (:num_members (u/get-group token concept-id)))))

(deftest group-members-test
  (let [token (e/login (u/conn-context) "user1")
        group (u/make-group)
        {:keys [status concept_id revision_id]} (u/create-group token group)]

    (testing "Initially no members are in the group"
      (assert-group-member-count-correct token concept_id 0)
      (is (= {:status 200 :body []} (u/get-members token concept_id))))

    (testing "Successful add members to group"
      (let [token2 (e/login (u/conn-context) "user1")
            response (u/add-members token concept_id ["user1" "user2" "user1"])]
        (assert-group-member-count-correct token concept_id 2)
        (is (= {:status 200 :concept_id concept_id :revision_id 2} response))
        (is (= {:status 200 :body ["user1" "user2"]} (u/get-members token concept_id)))))

    (testing "Add more members to the group"
      (let [response (u/add-members token concept_id ["user3" "user4" "user3" "user1"])]
        (assert-group-member-count-correct token concept_id 4)
        (is (= {:status 200 :concept_id concept_id :revision_id 3} response))
        (is (= {:status 200 :body ["user1" "user2" "user3" "user4"]} (u/get-members token concept_id)))))

    (testing "Add members that do not exist"
      (is (= {:status 400
              :errors ["The following users do not exist [notexist1, notexist2]"]}
             (u/add-members
              token concept_id
              ["notexist1" "user1" "notexist2" "user2" "notexist2"])))
      (assert-group-member-count-correct token concept_id 4))

    (testing "Removing members"
      ;; Removing duplicates, members not in the group, and non-existent users is allowed.
      (let [response (u/remove-members token concept_id
                                       ["user3"
                                        "user1"
                                        "user3" ; duplicate
                                        "notexist3" ; doesn't exist
                                        "user5"])] ; exists but not in group
        (assert-group-member-count-correct token concept_id 2)
        (is (= {:status 200 :concept_id concept_id :revision_id 4} response))
        (is (= {:status 200 :body ["user2" "user4"]} (u/get-members token concept_id)))))

    (testing "Add and remove with a deleted group"
      (is (= 200 (:status (u/delete-group token concept_id))))
      (is (= {:status 404
              :errors [(format "Group with concept id [%s] was deleted." concept_id)]}
             (u/add-members token concept_id ["user1"])))
      (is (= {:status 404
              :errors [(format "Group with concept id [%s] was deleted." concept_id)]}
             (u/remove-members token concept_id ["user1"]))))

    (testing "Add and remove with a non existent group"
      (is (= {:status 404
              :errors ["Group could not be found with concept id [AG1234-CMR]"]}
             (u/add-members token "AG1234-CMR" ["user1"])))
      (is (= {:status 404
              :errors ["Group could not be found with concept id [AG1234-CMR]"]}
             (u/remove-members token "AG1234-CMR" ["user1"]))))))
