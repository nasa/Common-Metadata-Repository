(ns cmr.system-int-test.search.granule-psa-search-test
  "Tests searching for granules by product specific attributes."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.search.services.parameter-converters.attribute :as am]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(comment
  (ingest/create-provider "PROV1")
)


(deftest invalid-psa-searches
  (are [v error]
       (= {:status 422 :errors [error]}
         (search/get-search-failure-data (search/find-refs :granule {"attribute[]" v})))
       ",alpha,a" (am/invalid-type-msg "")
       ",alpha,a,b" (am/invalid-type-msg "")
       "string,,a" (am/invalid-name-msg "")
       "string,,a,b" (am/invalid-name-msg "")
       "string,alpha," (am/invalid-value-msg :string "")
       "string,alpha" (am/invalid-num-parts-msg))

  (is (= {:status 422 :errors [(am/attributes-must-be-sequence-msg)]}
         (search/get-search-failure-data
           (search/find-refs :granule {"attribute" "string,alpha,a"})))))


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
      (are [v items]
           (d/refs-match? items (search/find-refs :granule {"attribute[]" v}))
           "string,alpha,a" [gran1]
           "string,alpha,c" []
           "string,bravo,b" [gran1 gran3]))))



