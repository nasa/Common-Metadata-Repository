(ns cmr.system-int-test.search.granule-retrieval-test
  "Integration test for granule retrieval with cmr-concept-id"
  (:require [clojure.test :refer :all]
            [cmr.umm.echo10.granule :as g]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.mime-types :as mt]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest retrieve-granule-by-cmr-concept-id
  (let [coll1 (d/ingest "PROV1" (dc/collection {:projects (dc/projects "ABC" "KLM" "XYZ")}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                   :project-refs ["ABC"]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                   :project-refs ["KLM"]}))
        umm-gran (dg/granule coll1 {:granule-ur "Granule1"
                                    :project-refs ["XYZ"]})
        gran1 (d/ingest "PROV1" umm-gran)
        del-gran (d/ingest "PROV1" (dg/granule coll1))
        umm-gran (-> umm-gran
                     (assoc-in [:collection-ref :short-name] nil)
                     (assoc-in [:collection-ref :version-id] nil)
                     (assoc-in [:collection-ref :entry-id] nil)
                     (dissoc :collection-concept-id))]
    (ingest/delete-concept (d/item->concept del-gran :echo10))
    (index/wait-until-indexed)
    (testing "retrieval by granule cmr-concept-id returns the latest revision."
      (let [response (search/retrieve-concept (:concept-id gran1))
            parsed-granule (g/parse-granule (:body response))]
        (is (search/mime-type-matches-response? response mt/echo10))
        (is (= umm-gran
               parsed-granule))))
    (testing "retrieval of a deleted granule results in a 404"
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id del-gran) nil {:throw-exceptions true}))]
        (is (= 404 status))
        (is (= ["Concept with concept-id [G1200000003-PROV1] could not be found."] errors))))
    (testing "retrieval by granule cmr-concept-id, not found."
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        "G1111-PROV1" nil {:throw-exceptions true}))]
        (is (= 404 status))
        (is (= ["Concept with concept-id [G1111-PROV1] could not be found."] errors))))))
