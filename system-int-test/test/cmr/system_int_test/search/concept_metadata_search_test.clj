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
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.umm.iso-mends.collection :as umm-c]
            [clojure.string :as str]
            [cmr.common.mime-types :as mt]
            [clj-time.format :as f]))

(use-fixtures
  :each
  (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                        {:grant-all-search? false}))

(defmulti result-matches?
  "Compare UMM record to the response from search."
  (fn [format-key umm response]
    format-key))

;; ISO-19115 must be handled separately from the other formats becaue it uses xslt to translate
;; ECHO10 and the resulting XML is not quite the same as what we get when going from UMM to XML.
(defmethod result-matches? :iso19115
  [format-key umm response]
  (let [metadata-xml (:body response)
        metadata-umm (-> (umm-c/parse-collection metadata-xml)
                         ;; parser prepends "gov.nasa.echo:" to entry-title for some reason
                         (update-in [:entry-title]
                                    (fn [entry-title]
                                      (str/replace entry-title "gov.nasa.echo:" "")))
                         ;; remove default added by parser
                         (assoc :metadata-language nil)
                         ;; remove default added by parser
                         (assoc :use-constraints nil))]
    (is (= umm metadata-umm))))

(defmethod result-matches? :default
  [format-key umm response]
  (let [expected (umm/umm->xml umm format-key)
        metadata-xml (:body response)]
    (is (= expected metadata-xml))))

(deftest retrieve-metadata-from-search-by-concept-id-concept-revision
  ;; Grant permissions before creating data.
  ;; All collections in PROV1 granted to registered users.
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-registered-users (s/context) (e/gran-catalog-item-id "provguid1"))

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

        umm-gran1-1 (dg/granule umm-coll2-1 {:access-value 1.0})
        umm-gran1-2 (assoc umm-gran1-1 :access-value 2.0)

        ;; NOTE - most of the following bindings could be ignored with _, but they are assigned
        ;; to vars to make it easier to see what is being ingested.

        ;; Ingest a collection twice.
        coll1-1 (d/ingest "PROV1" umm-coll1-1)
        coll1-2 (d/ingest "PROV1" umm-coll1-2)

        ;; Ingest collection once, delete, then ingest again.
        coll2-1 (d/ingest "PROV1" umm-coll2-1)
        _ (ingest/delete-concept (d/item->concept coll2-1))
        coll2-3 (d/ingest "PROV1" umm-coll2-3)

        ;; Ingest a collection for PROV2 that is not visible to guests.
        coll3 (d/ingest "PROV2" (dc/collection {:entry-title "et1"
                                                :version-id "v1"
                                                :short-name "s1"}))
        ;; ingest granule twice
        gran1-1 (d/ingest "PROV1" umm-gran1-1)
        gran1-2 (d/ingest "PROV1" umm-gran1-2)

        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1")]
    (index/wait-until-indexed)

    (testing "retrieve metadata from search by concept-id/revision-id"
      (testing "collections and granules"
        (are2 [item format-key accept concept-id revision-id]
              (let [headers {transmit-config/token-header user1-token
                             "Accept" accept}
                    response (search/find-concept-metadata-by-id-and-revision
                               concept-id
                               revision-id
                               {:headers headers})]
                (result-matches? format-key item response))

              "echo10 collection revision 1"
              umm-coll1-1 :echo10 mt/echo10 "C1200000000-PROV1" 1

              "echo10 collection revision 2"
              umm-coll1-2 :echo10 mt/echo10 "C1200000000-PROV1" 2

              "dif collection revision 1"
              umm-coll2-1 :dif mt/dif "C1200000001-PROV1" 1

              "dif collection revision 3"
              umm-coll2-3 :dif mt/dif "C1200000001-PROV1" 3

              "dif10 collection revision 1"
              umm-coll2-1 :dif10 mt/dif10 "C1200000001-PROV1" 1

              "dif10 collection revision 3"
              umm-coll2-3 :dif10 mt/dif10 "C1200000001-PROV1" 3

              "iso-smap collection revision 1"
              umm-coll1-1 :iso-smap mt/iso-smap "C1200000000-PROV1" 1

              "iso-smap collection revision 2"
              umm-coll1-2 :iso-smap mt/iso-smap "C1200000000-PROV1" 2

              "iso19115 collection revision 1"
              umm-coll2-1 :iso19115 mt/iso19115 "C1200000001-PROV1" 1

              "iso19115 collection revision 3"
              umm-coll2-3 :iso19115 mt/iso19115 "C1200000001-PROV1" 3

              "native format collection revision 1"
              umm-coll1-1 :echo10 mt/native "C1200000000-PROV1" 1

              "native format collection revision 2"
              umm-coll1-2 :echo10 mt/native "C1200000000-PROV1" 2

              "echo10 granule revision 1"
              umm-gran1-1 :echo10 mt/echo10 "G1200000003-PROV1" 1

              "echo10 granule revision 2"
              umm-gran1-2 :echo10 mt/echo10 "G1200000003-PROV1" 2))

      (testing "Requests for tombstone revision returns a 400 error"
        (let [{:keys [status errors] :as response} (search/get-search-failure-xml-data
                                                     (search/find-concept-metadata-by-id-and-revision
                                                       (:concept-id coll2-1)
                                                       2
                                                       {:headers {transmit-config/token-header
                                                                  user1-token}}))]
          (is (= 400 status))
          (is (= #{"Deleted concepts do not contain metadata."}
                 (set errors)))))

      (testing "Unknown concept-id returns a 404 error"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        [(search/find-concept-metadata-by-id-and-revision
                                           "C1234-PROV1"
                                           1
                                           {:headers {transmit-config/token-header
                                                      user1-token}})])]
          (is (= 404 status))
          (is (= #{"Concept with concept-id [C1234-PROV1] and revision-id [1] does not exist."}
                 (set errors)))))

      (testing "Known concept-id with unavailable revision-id returns a 404 error"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/find-concept-metadata-by-id-and-revision
                                          "C1200000000-PROV1"
                                          1000000
                                          {:headers {transmit-config/token-header
                                                     user1-token}}))]
          (is (= 404 status))
          (is (= #{"Concept with concept-id [C1200000000-PROV1] and revision-id [1000000] does not exist."}
                 (set errors)))))

      (testing "Non-integer revision id returns a 422 error"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/find-concept-metadata-by-id-and-revision
                                          "C1200000000-PROV1"
                                          "FOO"
                                          {:headers {transmit-config/token-header
                                                     user1-token}}))]
          (is (= 422 status))
          (is (= #{"Revision id [FOO] must be an integer greater than 0."}
                 (set errors)))))

      ;; JSON output is waiting for the UMM JSON Schema implementation.
      (testing "JSON output not supported (yet)"
        (let [{:keys [status errors]} (search/get-search-failure-data
                                        (search/find-concept-metadata-by-id-and-revision
                                          "C1200000000-PROV1"
                                          1
                                          {:headers {transmit-config/token-header
                                                     user1-token
                                                     "Accept" "application/json"}}))]
          (is (= 400 status))
          (is (= #{"The mime types specified in the accept header [application/json] are not supported."}
                 (set errors)))))

      (testing "Atom output not supported"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/find-concept-metadata-by-id-and-revision
                                          "C1200000000-PROV1"
                                          1
                                          {:headers {transmit-config/token-header
                                                     user1-token
                                                     "Accept" "application/atom+xml"}}))]
          (is (= 400 status))
          (is (= #{"The mime types specified in the accept header [application/atom+xml] are not supported."}
                 (set errors)))))

      (testing "ACLs"
        ;; no token
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/find-concept-metadata-by-id-and-revision
                                          "C1200000001-PROV1"
                                          1
                                          {}))]
          (is (= 404 status))
          (is(= #{"Concept with concept-id [C1200000001-PROV1] and revision-id [1] could not be found."}
                (set errors))))
        ;; Guest token can't see PROV2 collections.
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/find-concept-metadata-by-id-and-revision
                                          "C1200000002-PROV2"
                                          1
                                          {:headers {transmit-config/token-header
                                                     guest-token}}))]
          (is (= 404 status))
          (is(= #{"Concept with concept-id [C1200000002-PROV2] and revision-id [1] could not be found."}
                (set errors))))))))
