(ns cmr.system-int-test.search.concept-search-result-test
  "Integration tests for search result from concept searches"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.echo10.granule :as g]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(deftest search-result
  (let [umm-coll (dc/collection {:short-name "OneShort"})
        coll1 (d/ingest "CMR_PROV1" umm-coll)
        umm-gran (dg/granule coll1 {:granule-ur "Granule1"})
        gran1 (d/ingest "CMR_PROV1" umm-gran)]

    (index/refresh-elastic-index)

    (testing "search collection search result."
      (let [refs (search/find-refs :collection {:short-name "OneShort"})
            location (:location (first (:refs refs)))]
        (is (d/refs-match? [coll1] refs))
        (let [response (client/get location
                                   {:accept :xml
                                    :connection-manager (url/conn-mgr)})
              parsed-coll (c/parse-collection (:body response))]
          (is (= umm-coll parsed-coll)))))

    (testing "search granule search result."
      (let [refs (search/find-refs :granule {:granule-ur "Granule1"})
            location (:location (first (:refs refs)))]
        (is (d/refs-match? [gran1] refs))
        (let [response (client/get location
                                   {:accept :xml
                                    :connection-manager (url/conn-mgr)})
              parsed-gran (g/parse-granule (:body response))]
          (is (= umm-gran parsed-gran)))))))
