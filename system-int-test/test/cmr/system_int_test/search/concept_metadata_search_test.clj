(ns cmr.system-int-test.search.concept-metadata-search-test
  "Integration test for retrieving collection metadata from search by concept-id and revision-id"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.umm.echo10.collection :as c]
            [cmr.common.util :refer [are2] :as util]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.mime-types :as mt]
            [cmr.umm.core :as umm]
            [clj-time.format :as f]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- concept-with-metadata
  "Create metadata for a umm concept and attach it"
  [concept format]
  (assoc concept :metadata (umm/umm->xml concept format)))

(defn- search-results-match
  "Compare the expected results to the actual results of a find by concept-id/revision-id search"
  [concepts search-response]
  (let [result-set (set (map :metadata (:body search-response)))
        expected (set (map :metadata concepts))]
    (is (= expected result-set))))

(deftest retrieve-metadata-from-search-by-concept-id-concept-revision

  (let [umm-coll1-1 (dc/collection {:entry-title "et1"
                                    :entry-id "s1_v1"
                                    :version-id "v1"
                                    :short-name "s1"})
        umm-coll1-2 (-> umm-coll1-1
                        (assoc-in [:product :version-id] "v2")
                        (assoc :entry-id "s1_v2"))
        umm-coll2-1 (dc/collection {:entry-title "et2"
                                    :entry-id "s2_v2"
                                    :version-id "v2"
                                    :short-name "s2"})
        umm-coll2-3 (-> umm-coll2-1
                        (assoc-in [:product :version-id] "v6")
                        (assoc :entry-id "s2_v6"))

        umm-gran1-1 (dg/granule umm-coll2-3)

        ;; Ingest collection twice.
        coll1-1 (d/ingest "PROV1" umm-coll1-1)
        coll1-2 (d/ingest "PROV1" umm-coll1-2)

        ; Ingest collection once, delete, then ingest again.
        coll2-1 (d/ingest "PROV1" umm-coll2-1)
        _ (ingest/delete-concept (d/item->concept coll2-1))
        coll2-3 (d/ingest "PROV1" umm-coll2-3)

        ;; Ingest a couple of collections once each.
        coll3 (d/ingest "PROV2" (dc/collection {:entry-title "et3"
                                                :version-id "v3"
                                                :short-name "s1"}))
        coll3+metadata (assoc coll3 :metadata (umm/umm->xml coll3 :echo10))
        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "et1"
                                                :version-id "v3"
                                                :short-name "s4"}))
        gran1-1 (d/ingest "PROV1" umm-gran1-1)]
    (index/wait-until-indexed)

    (testing "retrieve metadata from search by concept-id/revision-id"
      (testing "collections and granules"
        (are2 [item format-key accept concept-id revision-id]
              (let [headers {transmit-config/token-header (transmit-config/echo-system-token)
                             "Accept" accept}
                    response (search/find-concept-metadata-by-id-and-revision
                               concept-id
                               revision-id
                               {:headers headers})
                    expected (:metadata (d/item->metadata-result false format-key item))]
                (is (= expected (:body response))))

              "echo10 collection revision 1"
              umm-coll1-1 :echo10 "application/echo10+xml" "C1200000000-PROV1" 1

               "echo10 collection revision 2"
              umm-coll1-2 :echo10 "application/echo10+xml" "C1200000000-PROV1" 2

              " echo10 granule"
              umm-gran1-1 :echo10 "application/echo10+xml" "G1200000004-PROV1" 1))

      (testing "Requests for tombstone revision returns a 400 error"
        (let [{:keys [status errors] :as response} (search/get-search-failure-xml-data
                                        (search/find-concept-metadata-by-id-and-revision
                                                  (:concept-id coll2-1)
                                                  2
                                                  {:headers {transmit-config/token-header
                                                             (transmit-config/echo-system-token)}}))]
          (is (= 400 status))
          (is (= #{"Deleted concepts do not contain metadata"}
                 (set errors)))))

      (testing "Unknown concept-id returns a 404 error"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        [(search/find-concept-metadata-by-id-and-revision
                                                  "C1234-PROV1"
                                                  1
                                                  {:headers {transmit-config/token-header
                                                             (transmit-config/echo-system-token)}})])]
          (is (= 404 status))
          (is (= #{"Concept with concept-id [C1234-PROV1] and revision-id [1] does not exist."}
                 (set errors)))))

      (testing "Known concept-id with unavailable revision-id returns a 404 error"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/find-concept-metadata-by-id-and-revision
                                                  (:concept-id coll1-1)
                                                  1000000
                                                  {:headers {transmit-config/token-header
                                                             (transmit-config/echo-system-token)}}))]
          (is (= 404 status))
          (is (= #{"Concept with concept-id [C1200000000-PROV1] and revision-id [1000000] does not exist."}
                 (set errors)))))

      #_(testing "ACLs"
        ;; no token - This is temporary and will be updated in issue CMR-1771.
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/find-concept-revisions :collection {:provider-id "PROV1"}))]
          (is (= 401 status))
          (is(= #{"You do not have permission to perform that action."}
                (set errors))))))))
