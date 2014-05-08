(ns cmr.index-set.int-test.invalid-data-crud-tests
  "Contains integration tests to verify index-set crud operations with invalid data."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [cmr.index-set.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each util/reset-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Verify non numeric index-set id results in 422
(deftest invalid-index-set-id-test
  (testing "invalid index-set id"
    (let [index-set util/sample-index-set
          {:keys [status errors-str]} (util/submit-create-index-set-req (assoc-in index-set [:index-set :id] "AA"))]
      (is (= 422 status))
      (is (re-find #"id: AA not a positive integer" errors-str)))))

;; Verify missing index-set name results in 422
(deftest no-name-index-set-test
  (testing "missing index-set name"
    (let [no-name-index-set (first (walk/postwalk #(if (map? %)
                                                     (dissoc % :name)
                                                     %)
                                                  (list util/sample-index-set)))
          {:keys [status errors-str]} (util/submit-create-index-set-req no-name-index-set)]
      (is (= 422 status))
      (is (re-find #"missing id or name in index-set" errors-str)))))

;; Verify missing index config results in 422
(deftest index-config-missing-test
  (testing "missing index-config"
    (let [invalid-idx-set util/invalid-sample-index-set
          {:keys [status errors-str]} (util/submit-create-index-set-req invalid-idx-set)]
      (is (= 422 status))
      (is (re-find #"missing index names or settings or mapping in given index-set" errors-str)))))

;; Verify index-set not found condition.
;; First create a index-set, fetch the index-set using an id successfully and then
;; try to fetch an index-set with invalid id to see 404
(deftest get-non-existent-index-set-test
  (testing "create index-set"
    (let [{:keys [status]} (util/submit-create-index-set-req util/sample-index-set)]
      (is (= 201 status))))

  (testing "get existent index-set"
    (util/flush-elastic)
    (let [{:keys [status errors-str]} (util/get-index-set (get-in util/sample-index-set [:index-set :id]))]
      (is (= 200 status))))

  (testing "get non existent index-set"
    (let [{:keys [status errors-str]} (util/get-index-set "XXX")
          regex-err-msg #"index-set with id: XXX not found"]
      (is (= 404 status))
      (is (re-find regex-err-msg errors-str)))))

; Verify non existent index-set results in 404
(deftest del-non-existent-index-set-test
  (let [index-set-id (get-in util/sample-index-set [:index-set :id])
        {:keys [status errors-str]} (util/submit-delete-index-set-req index-set-id)]
    (is (= 404 status))
    (is (re-find #"index-set" errors-str))
    (is (re-find #"not found" errors-str))))

;; Verify incorrect mapping issues etc are handled correctly
(deftest invalid-mapping-type-index-config-test
  (testing "index confing wrong mapping types"
    (let [{:keys [status errors-str]} (util/submit-create-index-set-req util/index-set-w-invalid-idx-prop)]
      (is (= 400 status))
      (is (re-find #"MapperParsingException\[mapping \[collection\]\]" errors-str)))))





