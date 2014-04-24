(ns cmr.system-int-test.search.granule-psa-search-test
  "Tests searching for granules by product specific attributes."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(comment

  (require '[cmr.umm.echo10.core :as echo10])
  (echo10/umm->echo10-xml (dg/granule (dc/collection {}) {:product-specific-attributes
                                                          [(dg/psa "alpha" ["a" "b"])
                                                           (dg/psa "bravo" ["c" "b"])]}))

  (ingest/create-provider "PROV1")
  (d/ingest "PROV1" (dc/collection {}))

  (def psa1 (dc/psa "alpha" :string))
  (def psa2 (dc/psa "bravo" :string))
  (def psa3 (dc/psa "charlie" :string))
  (def coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]})))
  (def gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" ["a" "b"])
                                                                                 (dg/psa "bravo" ["c" "b"])]})))



)

;; TODO I can create local helper functions later to make construction of data easier

(deftest string-psas-search-test
  (let [psa1 (dc/psa "alpha" :string)
        psa2 (dc/psa "bravo" :string)
        psa3 (dc/psa "charlie" :string)

        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" ["a" "b"])
                                                                                 (dg/psa "bravo" ["c" "b"])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "bravo" ["a"])]}))

        coll2 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa2 psa3]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "bravo" ["a" "b"])]}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:product-specific-attributes [(dg/psa "charlie" ["a"])]}))]
    (index/flush-elastic-index)

    (testing "search by value"
      (is (d/refs-match?
            [gran1]
            (search/find-refs :granule {"attribute[]" "string,alpha,a"}))))))


;; TODO check that validation is working

