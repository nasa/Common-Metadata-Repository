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
 ((ingest/reset-fixture {"provguid1" "PROV1"
                         "provguid2" "PROV2"})
  (constantly true)))


;; TODO look at acceptance criteria for issues to make sure we're testing everything
;; including failure cases of rebalancing stuff that shouldn't be or has already been rebalanced.


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

     (is (= expected-provider-holdings (:results (search/provider-holdings-in-format :json))))

     ;; Start rebalancing of collection 1. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     ;; Clear the search cache so it will get the last index set
     (search/clear-caches)
     (is (= expected-provider-holdings (:results (search/provider-holdings-in-format :json)))))))


;; setup some data with multiple collections, multiple providers and multiple granules
;; test counts etc.
;; rebalance one and then test results. Make sure data when indexed goes to the right place.
;; make sure searches are correct
