(ns cmr.indexer.test.invalid-data-crud-tests
  "Contains integration tests to verify index-set crud operations with invalid data."
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [clojure.walk :as walk]
   [cmr.indexer.test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each util/reset-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Verify non numeric index-set id results in 422
(deftest invalid-index-set-id-test
  (testing "invalid index-set id"
    (let [index-set util/sample-index-set
          {:keys [status errors]} (util/create-index-set (assoc-in index-set [:index-set :id] "AA"))]
      (is (= 422 status))
      (is (re-find #"id: AA not a positive integer" (first errors))))))

;; Verify missing index-set name results in 422
(deftest no-name-index-set-test
  (testing "missing index-set name"
    (let [no-name-index-set (first (walk/postwalk #(if (map? %)
                                                     (dissoc % :name)
                                                     %)
                                                  (list util/sample-index-set)))
          {:keys [status errors]} (util/create-index-set no-name-index-set)]
      (is (= 422 status))
      (is (re-find #"missing id or name in index-set" (first errors))))))

;; Verify missing index config results in 422
(deftest index-config-missing-test
  (testing "missing index-config"
    (let [invalid-idx-set util/invalid-sample-index-set
          {:keys [status errors]} (util/create-index-set invalid-idx-set)]
      (is (= 422 status))
      (is (re-find #"missing index names or settings or mapping in given index-set" (first errors))))))

;; Verify index-set not found condition.
;; First create a index-set, fetch the index-set using an id successfully and then
;; try to fetch an index-set with invalid id to see 404
(deftest get-non-existent-index-set-test
  (testing "create index-set"
    (let [{:keys [status]} (util/create-index-set util/sample-index-set)]
      (is (= 201 status))))

  (testing "get existent index-set"
    (let [{:keys [status errors]} (util/get-index-set (get-in util/sample-index-set [:index-set :id]))]
      (is (= 200 status))))

  (testing "get non existent index-set"
    (let [{:keys [status errors]} (util/get-index-set "XXX")
          regex-err-msg #"index-set with id: XXX not found"]
      (is (= 404 status))
      (is (re-find regex-err-msg (first errors))))))

; Verify non existent index-set results in 404
(deftest del-non-existent-index-set-test
  (let [index-set-id (get-in util/sample-index-set [:index-set :id])
        {:keys [status errors]} (util/delete-index-set index-set-id)]
    (is (= 404 status))
    (is (re-find #"index-set" (first errors)))
    (is (re-find #"not found" (first errors)))))

;; Verify incorrect mapping issues etc are handled correctly
(deftest invalid-mapping-type-index-config-test
  (testing "index confing wrong mapping types"
    (let [{:keys [status errors]} (util/create-index-set util/index-set-w-invalid-idx-prop)]
      (is (= 400 status))
      (is (re-find #"Root mapping definition has unsupported parameters" (first errors))))))
