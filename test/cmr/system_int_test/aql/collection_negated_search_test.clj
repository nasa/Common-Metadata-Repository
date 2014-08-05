(ns cmr.system-int-test.aql.collection-negated-search-test
  "Tests searching for collections using aql negated attribute"
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.common.services.messages :as msg]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))


(use-fixtures :each (ingest/reset-fixture "PROV1" "PROV2"))

(deftest aql-negated-search-test
  (let [coll1 (d/ingest "PROV1" (dc/collection {:short-name "S1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:short-name "S2"}))
        coll3 (d/ingest "PROV2" (dc/collection {:short-name "S3"}))
        coll4 (d/ingest "PROV2" (dc/collection {:short-name "T1"}))]
    (index/refresh-elastic-index)
    (testing "negated search"
      (are [items id options]
           (let [params (merge {:where {:short-name id}} options)]
           (d/refs-match? items (search/find-refs-with-aql :collection params)))

           [coll1] "S1" {:negated false}
           [coll2 coll3 coll4] "S1" {:negated true}
           [coll1 coll2 coll3 coll4] "NO" {:negated true}
           [coll4] ["S1" "S2" "S3"] {:negated true}
           [coll4] "S%" {:pattern true :negated true}
           [coll1 coll2 coll3] "T1" {:negated true}

           ;; negated does not affect provider-id (dataCenterId) condition
           [coll1 coll2] "T1" {:provider-ids "PROV1" :negated true}
           [coll3] "T1" {:provider-ids "PROV2" :negated true}))))


