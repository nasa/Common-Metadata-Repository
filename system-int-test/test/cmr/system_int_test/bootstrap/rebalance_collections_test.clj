(ns cmr.system-int-test.bootstrap.rebalance-collections-test
  "Tests rebalancing granule indexes by moving collections's granules from the small collections
   index to separate collection indexes"
  (require [clojure.test :refer :all]
           [cmr.system-int-test.utils.metadata-db-util :as mdb]
           [cmr.system-int-test.utils.ingest-util :as ingest]
           [cmr.system-int-test.utils.search-util :as search]
           [cmr.system-int-test.utils.index-util :as index]
           [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
           [cmr.system-int-test.data2.collection :as dc]
           [cmr.system-int-test.data2.granule :as dg]
           [cmr.system-int-test.data2.core :as d]
           [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
           [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(comment
 ;; Use this to manually run the fixture
 ((ingest/reset-fixture {"provguid1" "PROV1"
                         "provguid2" "PROV2"})
  (constantly true)))


;; This is the initial test of kicking off balancing of collections.
;; Look at acceptance criteria for issues to make sure we're testing everything
;; including failure cases of rebalancing stuff that shouldn't be or has already been rebalanced.

(defn count-by-params
  "Returns the number of granules found by the given params"
  [params]
  (let [response (search/find-refs :granule (assoc params :page-size 0))]
    (when (= 400 (:status response))
      (throw (Exception. (str "Search by params failed:" (pr-str response)))))
    (:hits response)))

(defn verify-provider-holdings
  "Verifies counts in the search application by searching several different ways for counts."
  [expected-provider-holdings]
  ;; Verify search counts in provider holdings
  (is (= expected-provider-holdings (:results (search/provider-holdings-in-format :json))))

  ;; Verify search counts when searching individually by concept id
  (let [separate-holdings (for [coll-holding expected-provider-holdings]
                            (assoc coll-holding
                                   :granule-count
                                   (count-by-params {:concept-id (:concept-id coll-holding)})))]

    (is (= expected-provider-holdings separate-holdings)))
  ;; Verify search counts when searching by provider id
  (let [expected-counts-by-provider (reduce (fn [count-map {:keys [provider-id granule-count]}]
                                              (update count-map provider-id #(+ (or % 0) granule-count)))
                                            {}
                                            expected-provider-holdings)
        actual-counts-by-provider (into {} (for [provider-id (keys expected-counts-by-provider)]
                                             [provider-id (count-by-params {:provider-id provider-id})]))]
    (is (= expected-counts-by-provider actual-counts-by-provider))))

(defn assert-rebalance-status
  [expected-counts collection]
  (is (= (assoc expected-counts :status 200)
         (bootstrap/get-rebalance-status (:concept-id collection)))))

(deftest rebalance-collection-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
         coll3 (d/ingest "PROV2" (dc/collection {:entry-title "coll3"}))
         coll4 (d/ingest "PROV2" (dc/collection {:entry-title "coll4"}))
         granules (doall (for [coll [coll1 coll2 coll3 coll4]
                               n (range 4)
                               :let [granule-ur (str (:entry-title coll) "_gran_" n)]]
                          (d/ingest (:provider-id coll) (dg/granule coll {:granule-ur granule-ur}))))
         expected-provider-holdings (for [coll [coll1 coll2 coll3 coll4]]
                                     (-> coll
                                         (select-keys [:provider-id :concept-id :entry-title])
                                         (assoc :granule-count 4)))]
     (index/wait-until-indexed)

     (assert-rebalance-status {:small-collections 4} coll1)
     (verify-provider-holdings expected-provider-holdings)

     ;; Start rebalancing of collection 1. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     (assert-rebalance-status {:small-collections 4 :separate-index 4} coll1)
     ;; Clear the search cache so it will get the last index set
     (search/clear-caches)
     (verify-provider-holdings expected-provider-holdings))))

    ;; TODO index data and make sure that counts are correct
    ;; use verify to get the counts


;; rebalance one and then test results. Make sure data when indexed goes to the right place.
;; make sure searches are correct
