(ns cmr.index-set.int-test.invalid-data-crud-tests
  "Contains integration tests to verify index-set crud operations with invalid data."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [clojurewerkz.elastisch.rest.index :as esi]
            [cmr.index-set.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn setup [] (util/flush-elastic))
(defn teardown [] (util/reset))

(defn each-fixture [f]
  (setup)
  (f)
  (teardown))

(use-fixtures :each each-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; Verify non numeric index-set id results in 422
(deftest invalid-index-set-id-test
  (testing "invalid index-set id"
    (let [index-set util/sample-index-set
          {:keys [status errors-str]} (util/submit-create-index-set-req (assoc-in index-set [:index-set :id] "AA"))]
      (is (= status 422))
      (is (re-find #"id: AA not a positive integer" errors-str)))))

;; Verify missing index-set name results in 422
(deftest no-name-index-set-test
  (testing "missing index-set name"
    (let [no-name-index-set (first (walk/postwalk #(if (map? %) (dissoc % :name) %) (list util/sample-index-set)))
          {:keys [status errors-str]} (util/submit-create-index-set-req no-name-index-set)]
      (is (= status 422))
      (is (re-find #"missing id or name in index-set" errors-str)))))

;; Verify missing index config results in 422
(deftest index-config-missing-test
  (testing "missing index-config"
    (let [invalid-idx-set util/invalid-sample-index-set
          {:keys [status errors-str]} (util/submit-create-index-set-req invalid-idx-set)]
      (is (= status 422))
      (is (re-find #"missing index names or settings or mapping in given index-set" errors-str)))))

;; Verify index-set not found condition.
;; First create a index-set, fetch the index-set using an id successfully and then
;; try to fetch an index-set with invalid id to see 404
(deftest get-non-existent-index-set-test
  (testing "create index-set"
    (let [index-set util/sample-index-set
          {:keys [status]} (util/submit-create-index-set-req index-set)]
      (is (= status 201))))
  (testing "get existent index-set"
    (let [idx-set util/sample-index-set
          _ (util/flush-elastic)
          {:keys [status errors-str]} (util/get-index-set (-> idx-set :index-set :id))]
      (is (= status 200))))
  (testing "get non existent index-set"
    (let [idx-set util/sample-index-set
          x-idx-set (assoc-in idx-set [:index-set :id] "XXX")
          {:keys [status errors-str]} (util/get-index-set (-> x-idx-set :index-set :id))
          regex-err-msg #"index-set with id: XXX not found"]
      (is (= status 404))
      (is (re-find regex-err-msg errors-str)))))

;; Verify non existent index-set results in 404
(deftest del-non-existent-index-set-test
  (testing "delete non existent index-set"
    (let [_ (util/reset)
          index-set util/sample-index-set
          index-set-id (-> index-set :index-set :id)
          {:keys [status errors-str]} (util/submit-delete-index-set-req index-set-id)]
      (is (= status 404))
      (is (re-find #"index-set" errors-str))
      (is (re-find #"not found" errors-str)))))

;; Verify incorrect mapping issues etc are handled correctly
(deftest invalid-mapping-type-index-config-test
  (testing "index confing wrong mapping types"
    (let [index-set util/index-set-w-invalid-idx-prop
          index-set-id (-> index-set :index-set :id)
          {:keys [status errors-str]} (util/submit-create-index-set-req index-set)]
      (is (= status 400))
      (is (re-find #"MapperParsingException\[mapping \[collection\]\]" errors-str)))))





