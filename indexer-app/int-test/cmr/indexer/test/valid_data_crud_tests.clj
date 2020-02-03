(ns cmr.indexer.test.valid-data-crud-tests
  "Contains integration tests to verify index-set crud operations with good data."
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.walk :as walk]
   [clojurewerkz.elastisch.rest.index :as esi]
   [cmr.indexer.services.index-set-service :as svc]
   [cmr.indexer.test.utility :as util]))

(use-fixtures :each util/reset-fixture)

;; Verify index-set creation is successful.
;; use elastisch to verify all indices of index-set exist and the index-set doc has been indexed
;; in elastic
(deftest create-index-set-test
  (testing "create index-set"
    (let [index-set util/sample-index-set
          {:keys [status]} (util/create-index-set index-set)]
      (is (= 201 status))))
  (testing "indices existence"
    (let [index-set util/sample-index-set
          index-set-id (get-in index-set [:index-set :id])
          index-names (svc/get-index-names index-set)]
      (for [idx-name index-names]
        (is (esi/exists? @util/elastic-connection idx-name)))))
  (testing "index-set doc existence"
    (let [index-set util/sample-index-set
          index-set-id (get-in index-set [:index-set :id])
          {:keys [status]} (util/get-index-set index-set-id)]
      (is (= 200 status)))))

;; Verify index-set fetch is successful.
;; First create a index-set, fetch the index-set using an id successfully and then
;; assert one of the expected index by name in index-set is created in elastic.
(deftest get-index-set-test
  (testing "index-set fetch by id"
    (let [index-set util/sample-index-set
          suffix-idx-name "C4-PROV2"
          index-set-id (get-in index-set [:index-set :id])
          expected-idx-name (svc/gen-valid-index-name index-set-id suffix-idx-name)
          {:keys [status]} (util/create-index-set index-set)
          fetched-index-set (-> (util/get-index-set index-set-id) :response :body)
          actual-idx-name (get-in fetched-index-set [:index-set :concepts :collection (keyword suffix-idx-name)])]
      (is (= 201 status))
      (is (= expected-idx-name actual-idx-name)))))

;; Verify index-set delete is successful.
;; First create a index-set, verify a specified index in index-set is created, delete index-set
;; and verify specified index is not present now to ensure delete is successful
(deftest delete-index-set-test
  (testing "create index-set"
    (let [index-set util/sample-index-set
          suffix-idx-name "C4-PROV2"
          index-set-id (get-in index-set [:index-set :id])
          expected-idx-name (svc/gen-valid-index-name index-set-id suffix-idx-name)
          {:keys [status]} (util/create-index-set index-set)]
      (is (= 201 status))
      (is (esi/exists? @util/elastic-connection expected-idx-name))))
  (testing "delete index-set"
    (let [index-set util/sample-index-set
          index-set-id (get-in index-set [:index-set :id])
          suffix-idx-name "C99-Collections"
          expected-idx-name (svc/gen-valid-index-name index-set-id suffix-idx-name)
          {:keys [status]} (util/delete-index-set index-set-id)]
      (is (= 204 status))
      (is (not (esi/exists? @util/elastic-connection expected-idx-name))))))

;; Verify get index-sets fetches all index-sets in elastic.
;; Create 2 index-sets with different ids but with same number of concepts and indices associated
;; with each concept. Remember total number of indices in index-sets. Fetch all index-sets
;; from elastic to count indices. Count should match and all of the indices listed in index-sets
;; should be present in elastic
(deftest get-index-sets-test
  (testing "fetch all index-sets"
    (let [index-set util/sample-index-set
          _ (util/create-index-set index-set)
          _ (util/create-index-set (assoc-in index-set [:index-set :id] 77))
          indices-cnt (->> util/cmr-concepts
                           (map (:index-set index-set))
                           (mapcat :indexes)
                           count)
          expected-idx-cnt (* 2 indices-cnt)
          body (-> (util/get-index-sets) :response :body)
          actual-es-indices (util/list-es-indices body)]
      (for [es-idx-name actual-es-indices]
        (is (esi/exists? @util/elastic-connection es-idx-name)))
      (is (= expected-idx-cnt (count actual-es-indices))))))


;; manual reset
(comment
 (util/reset-fixture (constantly true))
 (get-in (util/get-index-set util/sample-index-set-id) [:response :body]))


(defn assert-rebalancing-collections
  "Asserts that the index set contains the listed rebalancing collections."
  ([expected-colls]
   (assert-rebalancing-collections expected-colls nil))
  ([expected-colls already-rebalanced]
   (let [index-set (get-in (util/get-index-set util/sample-index-set-id) [:response :body])
         base-coll-indexes (->> (get-in util/sample-index-set [:index-set :granule :indexes]) (map :name))
         expected-coll-indexes (set (concat base-coll-indexes expected-colls already-rebalanced))]
     (is (= (set expected-colls) (set (get-in index-set [:index-set :granule :rebalancing-collections]))))
     (is (= expected-coll-indexes (->> (get-in index-set [:index-set :granule :indexes])
                                       (map :name)
                                       set))
         "The expected granule indexes were incorrect")

     ;; Verify the collection indexes were created in elasticsearch.
     (doseq [collection expected-coll-indexes
             :let [collection-index-part (-> collection (str/replace "-" "_") str/lower-case)
                   elastic-index-name (str util/sample-index-set-id "_" collection-index-part)]]
       (is (esi/exists? @util/elastic-connection elastic-index-name))))))

;; Tests adding a collection that is rebalancing its granules from small_collections to a separate
;; granule index
(deftest add-rebalancing-collection-test
  (testing "Initial rebalancing collections"
    (util/create-index-set util/sample-index-set)
    (assert-rebalancing-collections []))
  (testing "Add collection that is already an index"
    (is (= {:status 400
            :errors ["The collection [C4-PROV3] already has a separate granule index."]}
           (select-keys (util/mark-collection-as-rebalancing util/sample-index-set-id "C4-PROV3")
                        [:status :errors])))
    (assert-rebalancing-collections []))
  (testing "Add first collection"
    (is (= 200 (:status (util/mark-collection-as-rebalancing util/sample-index-set-id "C5-PROV1"))))
    (assert-rebalancing-collections ["C5-PROV1"]))
  (testing "Add another collection"
    (is (= 200 (:status (util/mark-collection-as-rebalancing util/sample-index-set-id "C6-PROV1"))))
    (assert-rebalancing-collections ["C5-PROV1" "C6-PROV1"]))
  (testing "Add duplicate collection"
    (is (= {:status 400
            :errors ["The index set already contains rebalancing collection [C5-PROV1]"]}
           (select-keys (util/mark-collection-as-rebalancing util/sample-index-set-id "C5-PROV1")
                        [:status :errors])))
    ;; Rebalancing collections have not changed
    (assert-rebalancing-collections ["C5-PROV1" "C6-PROV1"])))

(deftest remove-rebalancing-collection-test
  (util/create-index-set util/sample-index-set)
  (testing "Remove rebalancing collection when none are rebalancing"
    (is (= {:status 400
            :errors ["The index set does not contain the rebalancing collection [C4-PROV3]"]}
           (select-keys (util/finalize-rebalancing-collection util/sample-index-set-id "C4-PROV3")
                        [:status :errors]))))

  ;; Add some rebalancing collections
  (is (= 200 (:status (util/mark-collection-as-rebalancing util/sample-index-set-id "C5-PROV1"))))
  (is (= 200 (:status (util/mark-collection-as-rebalancing util/sample-index-set-id "C6-PROV1"))))
  (assert-rebalancing-collections ["C5-PROV1" "C6-PROV1"])

  (testing "Remove a rebalancing collection"
    (is (= 200 (:status (util/finalize-rebalancing-collection util/sample-index-set-id "C6-PROV1"))))
    (assert-rebalancing-collections ["C5-PROV1"] ["C6-PROV1"]))

  (testing "Remove rebalancing collection already removed"
    (is (= {:status 400
            :errors ["The index set does not contain the rebalancing collection [C6-PROV1]"]}
           (select-keys (util/finalize-rebalancing-collection util/sample-index-set-id "C6-PROV1")
                        [:status :errors]))))

  (testing "Remove last rebalancing collection"
    (is (= 200 (:status (util/finalize-rebalancing-collection util/sample-index-set-id "C5-PROV1"))))
    (assert-rebalancing-collections [] ["C5-PROV1" "C6-PROV1"])))

(deftest update-rebalancing-collection-status-test
  (util/create-index-set util/sample-index-set)
  (testing "Update status of collection not being rebalanced"
    (is (= {:status 400
            :errors ["The index set does not contain the rebalancing collection [C1234]"]}
           (select-keys
            (util/update-rebalancing-collection-status
             util/sample-index-set-id
             "C1234"
             "COMPLETE")
            [:errors :status]))))
  (testing "Update status with invalid status value"
    (let [coll "test-invalid-status"]
      (util/mark-collection-as-rebalancing
       util/sample-index-set-id
       coll)
      (is (= {:status 400
              :errors ["Invalid status [INVALID_STATUS]. Only [\"IN_PROGRESS\" \"COMPLETE\"] are allowed."]}
           (select-keys
            (util/update-rebalancing-collection-status
             util/sample-index-set-id
             coll
             "INVALID_STATUS")
            [:errors :status])))
      (is (= {:status 400
              :errors ["Invalid status []. Only [\"IN_PROGRESS\" \"COMPLETE\"] are allowed."]}
           (select-keys
            (util/update-rebalancing-collection-status
             util/sample-index-set-id
             coll
             nil)
            [:errors :status])))))
  (let [coll "C5-PROV1"
        _ (util/mark-collection-as-rebalancing
           util/sample-index-set-id
           coll)]
    (testing "Status is IN_PROGRESS when rebalancing starts"
      (is (= "IN_PROGRESS"
             (get (get-in (util/get-index-set util/sample-index-set-id)
                          [:response :body :index-set :granule :rebalancing-status])
                  (keyword coll)))))
    (testing "Change status from IN_PROGRESS to COMPLETE"
      (is (= 200
             (:status (util/update-rebalancing-collection-status
                       util/sample-index-set-id
                       coll
                       "COMPLETE"))))
      (is (= "COMPLETE"
             (get (get-in (util/get-index-set util/sample-index-set-id)
                          [:response :body :index-set :granule :rebalancing-status])
                  (keyword coll)))))
    (testing "Finalizing rebalancing clears from rebalancing-status"
      (util/finalize-rebalancing-collection util/sample-index-set-id coll)
      (is (nil?
           (get (get-in (util/get-index-set util/sample-index-set-id)
                        [:response :body :index-set :granule :rebalancing-status])
                (keyword coll)))))))

;; Verify creating same index-set twice will result in 409
(deftest create-index-set-twice-test
  (testing "create index-set"
    (let [index-set util/sample-index-set
          {:keys [status]} (util/create-index-set index-set)]
      (is (= 201 status))))
  (testing "create same index-set"
    (let [index-set util/sample-index-set
          index-set-id (get-in index-set [:index-set :id])
          {:keys [status errors]} (util/create-index-set index-set)]
      (is (= 409 status))
      (is (re-find #"already exists" (first errors))))))

;; Verify reset deletes all of the indices assoc with index-sets and index-set docs
(deftest reset-index-sets-test
  (testing "reset index-set app"
    (let [{:keys [status]} (util/reset)]
      (is (= 204 status)))))
