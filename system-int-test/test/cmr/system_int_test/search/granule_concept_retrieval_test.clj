(ns cmr.system-int-test.search.granule-concept-retrieval-test
  "Integration test for granule retrieval via the /concepts/:concept-id and
  /concepts/:concept-id/:revision-id endpoints."
  (:require
   [cheshire.core :as json]
   [clj-time.format :as f]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :refer [are3] :as util]
   [cmr.common.xml :as cx]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.fast-xml :as fx]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm.echo10.echo10-collection :as c]
   [cmr.umm.iso-mends.iso-mends-collection :as umm-c]
   [cmr.umm.umm-core :as umm]))

(use-fixtures
  :each
  (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                        {:grant-all-search? true}))

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
                         (update-in [:EntryTitle]
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
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (e/grant-registered-users (s/context) (e/gran-catalog-item-id "PROV1"))

  (let [umm-coll1-1 (data-umm-c/collection {:EntryTitle "et1"
                                    :Version "v1"
                                    :ShortName "s1"})
        umm-coll1-2 (-> umm-coll1-1
                        (assoc :Version "v2"))
        umm-coll2-1 (data-umm-c/collection {:EntryTitle "et2"
                                    :Version "v2"
                                    :ShortName "s2"})
        umm-coll2-3 (-> umm-coll2-1
                        (assoc :Version "v6"))

        umm-gran1-1 (dg/granule-with-umm-spec-collection umm-coll2-1 (:concept-id umm-coll2-1) {:access-value 1.0})
        umm-gran1-2 (assoc umm-gran1-1 :access-value 2.0)

        ;; NOTE - most of the following bindings could be ignored with _, but they are assigned
        ;; to vars to make it easier to see what is being ingested.

        ;; Ingest a collection twice.
        coll1-1 (d/ingest-umm-spec-collection "PROV1" umm-coll1-1)
        coll1-2 (d/ingest-umm-spec-collection "PROV1" umm-coll1-2)

        ;; Ingest collection once, delete, then ingest again.
        coll2-1 (d/ingest-umm-spec-collection "PROV1" umm-coll2-1)
        _ (ingest/delete-concept (d/item->concept coll2-1))
        coll2-3 (d/ingest-umm-spec-collection "PROV1" umm-coll2-3)

        ;; Ingest a collection for PROV2 that is not visible to guests.
        coll3 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "et1"
                                                :Version "v1"
                                                :ShortName "s1"}))
        ;; ingest granule twice
        gran1-1 (d/ingest "PROV1" umm-gran1-1)
        gran1-2 (d/ingest "PROV1" umm-gran1-2)
        gran-concept-id (:concept-id gran1-1)

        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1")]
    (index/wait-until-indexed)

    (testing "retrieve metadata from search by concept-id/revision-id"
      (testing "collections and granules"
        (are3 [item format-key accept concept-id revision-id]
          (let [headers {transmit-config/token-header user1-token
                         "Accept" accept}
                response (search/retrieve-concept concept-id revision-id {:headers headers})]
            (result-matches? format-key item response))

          "echo10 granule revision 1"
          umm-gran1-1 :echo10 mt/echo10 gran-concept-id 1

          "echo10 granule revision 2"
          umm-gran1-2 :echo10 mt/echo10 gran-concept-id 2)))
    (testing "unsupported formats"
      (let [response (search/retrieve-concept
                      gran-concept-id nil
                      {:headers {transmit-config/token-header user1-token}
                       :accept mt/html})
            err-msg (first (:errors (json/decode (:body response) true)))]
        (is (= 400 (:status response)))
        (is (= (str "The mime types specified in the accept header ["
                    mt/html "] are not supported.")
               err-msg)))
      (let [response (search/retrieve-concept
                      gran-concept-id nil
                      {:headers {transmit-config/token-header user1-token}
                       :url-extension "html"})
            err-msg (first (:errors (json/decode (:body response) true)))]
        (is (= 400 (:status response)))
        (is (= "The URL extension [html] is not supported."
               err-msg))))))
