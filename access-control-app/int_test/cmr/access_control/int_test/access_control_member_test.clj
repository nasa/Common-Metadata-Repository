(ns cmr.access-control.int-test.access-control-member-test
  (:require [clojure.test :refer :all]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.access-control.int-test.access-control-test-util :as u]))

(use-fixtures :once (u/int-test-fixtures))
(use-fixtures :each (u/reset-fixture {"prov1guid" "PROV1"} ["user1" "user2" "user3" "user4"]))

(deftest group-members-test
  (let [token (e/login (u/conn-context) "user1")
        group (u/make-group)
        {:keys [status concept-id revision-id]} (u/create-group token group)]

    (testing "Initially no members are in the group"
      (= {:status 200 :body []} (u/get-members token concept-id)))

    (testing "Successful add members to group"
      (let [response (u/add-members token concept-id ["user1" "user2" "user1"])]
        (is (= {:status 200 :concept-id concept-id :revision-id 2} response))
        (= {:status 200 :body ["user1" "user2"]} (u/get-members token concept-id))))

    (testing "Add more members to the group"
      (let [response (u/add-members token concept-id ["user3" "user4" "user3" "user1"])]
        (is (= {:status 200 :concept-id concept-id :revision-id 3} response))
        (= {:status 200 :body ["user1" "user2" "user3" "user4"]} (u/get-members token concept-id))))))

;; TODO add members that don't exist to the group.
;; TODO retrieve group should include group count.
;; TODO test add more members to the group including some duplicates. Order should be kept.

;; TODO removing members not in the group is not an error


    ; (testing "Update group with members that do not exist"
    ;   (is (= {:status 400
    ;           :errors ["The following users do not exist [notexist1, notexist2]"]}
    ;          (u/update-group
    ;           token concept-id
    ;           (assoc group :members ["notexist1" "user1" "notexist2" "user2" "notexist2"])))))
    ;
    ; (testing "Successful update group"
    ;   (let [updated-group (assoc group :members ["user2" "user4" "user2"])
    ;         response (u/update-group token concept-id updated-group)
    ;         ;; Duplicate usernames should be removed
    ;         updated-group (assoc group :members ["user2" "user4"])]
    ;     (is (= {:status 200 :concept-id concept-id :revision-id 2} response))
    ;     (u/assert-group-saved updated-group "user1" concept-id 2)
    ;     (is (= (assoc updated-group :status 200) (u/get-group concept-id)))))))
