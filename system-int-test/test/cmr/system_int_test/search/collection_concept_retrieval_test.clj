(ns cmr.system-int-test.search.collection-concept-retrieval-test
  "Integration test for collection retrieval via the /concepts/:concept-id and
  /concepts/:concept-id/:revision-id endpoints."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.atom-json :as dj]
            [cmr.common.xml :as cx]
            [cmr.system-int-test.data2.kml :as dk]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.umm.echo10.collection :as c]
            [cmr.common.util :refer [are2] :as util]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.mime-types :as mt]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.codec :as codec]
            [cmr.umm.spatial :as umm-s]
            [cmr.umm.core :as umm]
            [clojure.data.xml :as x]
            [cmr.system-int-test.utils.fast-xml :as fx]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.umm.iso-mends.collection :as umm-c]
            [clojure.string :as str]
            [cmr.common.mime-types :as mt]
            [clj-time.format :as f]))

(use-fixtures
  :each
  (ingest/reset-fixture
    {"provguid1" "PROV1"
     "provguid2" "PROV2"
     "provguid3" "PROV3"
     "provguid4" "PROV4"
     "usgsguid"  "USGS_EROS"}
    {:grant-all-search? false}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Utility functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-concept-by-id-helper
  [concept options]
  (:body (search/retrieve-concept (:concept-id concept) nil options)))

(defmulti result-matches?
  "Compare UMM record to the response from concept retrieval"
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
                         (assoc :use-constraints nil))
        expected (assoc-in umm [:product :short-name] (:entry-id umm))]
    (is (= expected metadata-umm))))

(defmethod result-matches? :default
  [format-key umm response]
  (let [expected (umm/umm->xml umm format-key)
        metadata-xml (:body response)]
    (is (= expected metadata-xml))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Retrieve by concept-id - general test
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest retrieve-collection-by-cmr-concept-id

  ;; Registered users have access to coll1
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1" ["coll1"]))

  ;; Ingest 2 early versions of coll1
  (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                    :projects (dc/projects "ESI_1")}))
  (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                    :projects (dc/projects "ESI_2")}))

  (let [umm-coll (dc/collection {:entry-title "coll1"
                                 :projects (dc/projects "ESI_3")})
        coll1 (d/ingest "PROV1" umm-coll)
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset2"}))
        del-coll (d/ingest "PROV1" (dc/collection))
        ;; tokens
        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1")]
    (ingest/delete-concept (d/item->concept del-coll :echo10))
    (index/wait-until-indexed)

    (testing "retrieval of a deleted collection results in a 404"
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id del-coll) nil {:throw-exceptions true
                                                                    :headers {transmit-config/token-header
                                                                              user1-token}}))]
        (is (= 404 status))
        (is (= ["Concept with concept-id [C1200000002-PROV1] could not be found."] errors))))
    (testing "retrieval by collection cmr-concept-id returns the latest revision."
      (let [response (search/retrieve-concept
                       (:concept-id coll1) nil {:query-params {:token user1-token}})
            parsed-collection (c/parse-collection (:body response))]
        (is (search/mime-type-matches-response? response mt/echo10))
        (is (= umm-coll parsed-collection))))
    (testing "retrieval with .xml extension returns correct mime type"
      (let [response (search/retrieve-concept
                       (:concept-id coll1) nil {:query-params {:token user1-token}
                                                :url-extension "xml"})
            parsed-collection (c/parse-collection (:body response))]
        (is (search/mime-type-matches-response? response mt/echo10))
        (is (= umm-coll parsed-collection))))
    (testing "retrieval by collection cmr-concept-id, not found."
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        "C1111-PROV1"  nil {:throw-exceptions true
                                                            :headers {transmit-config/token-header
                                                                      user1-token}}))]
        (is (= 404 status))
        (is (= ["Concept with concept-id [C1111-PROV1] could not be found."] errors))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Retrieve by concept-id - format test
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest multi-format-search-test
  (e/grant-all (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-all (s/context) (e/gran-catalog-item-id "provguid1"))
  (e/grant-all (s/context) (e/coll-catalog-item-id "provguid2"))

  (let [c1-echo (d/ingest "PROV1" (dc/collection {:short-name "S1"
                                                  :version-id "V1"
                                                  :entry-title "ET1"})
                          {:format :echo10})
        c2-echo (d/ingest "PROV2" (dc/collection {:short-name "S2"
                                                  :version-id "V2"
                                                  :entry-title "ET2"})
                          {:format :echo10})
        c3-dif (d/ingest "PROV1" (dc/collection-dif {:entry-id "S3"
                                                     :short-name "S3"
                                                     :version-id "V3"
                                                     :entry-title "ET3"
                                                     :long-name "ET3"})
                         {:format :dif})
        c4-dif (d/ingest "PROV2" (dc/collection-dif {:entry-id "S4"
                                                     :short-name "S4"
                                                     :version-id "V4"
                                                     :entry-title "ET4"
                                                     :long-name "ET4"})
                         {:format :dif})
        c5-iso (d/ingest "PROV1" (dc/collection {:short-name "S5"
                                                 :entry-id "S5"
                                                 :version-id "V5"})
                         {:format :iso19115})
        c6-iso (d/ingest "PROV2" (dc/collection {:short-name "S6"
                                                 :entry-id "S6"
                                                 :version-id "V6"})
                         {:format :iso19115})
        c7-smap (d/ingest "PROV1" (dc/collection {:short-name "S7"
                                                  :version-id "V7"})
                          {:format :iso-smap})
        c8-dif10 (d/ingest "PROV1" (dc/collection-dif10 {:entry-id "S8"
                                                         :short-name "S8"
                                                         :version-id "V8"
                                                         :entry-title "ET8"
                                                         :long-name "ET8"})
                           {:format :dif10})
        c9-dif10 (d/ingest "PROV2" (dc/collection-dif10 {:entry-id "S9"
                                                         :short-name "S9"
                                                         :version-id "V9"
                                                         :entry-title "ET9"
                                                         :long-name "ET9"})
                           {:format :dif10})
        all-colls [c1-echo c2-echo c3-dif c4-dif c5-iso c6-iso c7-smap c8-dif10 c9-dif10]]
    (index/wait-until-indexed)

    (testing "Get by concept id in formats"
      (testing "XML Metadata formats"
        (are [concept mime-type format-key url-extension]
             (= (umm/umm->xml concept format-key)
                (get-concept-by-id-helper concept {:url-extension url-extension :accept mime-type}))
             c1-echo "application/dif+xml" :dif nil
             c1-echo nil :dif "dif"
             c1-echo "application/echo10+xml" :echo10 nil
             c1-echo nil :echo10 "echo10"
             c3-dif "application/dif+xml" :dif nil
             c3-dif nil :dif "dif"
             c5-iso "application/iso19115+xml" :iso19115 nil
             c5-iso nil :iso19115 "iso19115"
             c5-iso nil :iso19115 "iso"
             c7-smap "application/iso:smap+xml" :iso-smap nil
             c7-smap nil :iso-smap "iso_smap"
             c8-dif10 "application/dif10+xml" :dif10 nil
             c8-dif10 nil :dif10 "dif10"))

      (testing "json"
        (are [concept options]
             (= (da/collection->expected-atom concept)
                (dj/parse-json-collection (get-concept-by-id-helper concept options)))
             c1-echo {:url-extension "json"}
             c1-echo {:accept        "application/json"}
             c3-dif  {:url-extension "json"}
             c3-dif  {:accept        "application/json"}
             c5-iso  {:url-extension "json"}
             c5-iso  {:accept        "application/json"}))

      (testing "atom"
        (are [concept options]
             (= [(da/collection->expected-atom concept)]
                (:entries (da/parse-atom-result :collection (get-concept-by-id-helper concept options))))
             c1-echo {:url-extension "atom"}
             c1-echo {:accept        "application/atom+xml"}
             c3-dif  {:url-extension "atom"}
             c3-dif  {:accept        "application/atom+xml"}
             c5-iso  {:url-extension "atom"}
             c5-iso  {:accept        "application/atom+xml"}))

      (testing "native format direct retrieval"
        ;; Native format can be specified using application/xml, application/metadata+xml,
        ;; .native extension, or not specifying any format.
        (util/are2 [concept format-key extension accept]
                   (let [options (-> {:accept nil}
                                     (merge (when extension {:url-extension extension}))
                                     (merge (when accept {:accept accept})))
                         response (search/retrieve-concept (:concept-id concept) nil options)]
                     (is (= (umm/umm->xml concept format-key) (:body response))))
                   "ECHO10 no extension" c1-echo :echo10 nil nil
                   "DIF no extension" c3-dif :dif nil nil
                   "ISO MENDS no extension" c5-iso :iso19115 nil nil
                   "SMAP ISO no extension" c7-smap :iso-smap nil nil
                   "ECHO10 .native extension" c1-echo :echo10 "native" nil
                   "DIF .native extension" c3-dif :dif "native" nil
                   "ISO MENDS .native extension" c5-iso :iso19115 "native" nil
                   "SMAP ISO .native extension" c7-smap :iso-smap "native" nil
                   "ECHO10 accept application/xml" c1-echo :echo10 nil "application/xml"
                   "DIF accept application/xml" c3-dif :dif nil "application/xml"
                   "ISO MENDS accept application/xml" c5-iso :iso19115 nil "application/xml"
                   "SMAP ISO accept application/xml" c7-smap :iso-smap nil "application/xml"
                   "ECHO10 accept application/metadata+xml" c1-echo :echo10 nil "application/metadata+xml"
                   "DIF accept application/metadata+xml" c3-dif :dif nil "application/metadata+xml"
                   "ISO MENDS accept application/metadata+xml" c5-iso :iso19115 nil "application/metadata+xml"
                   "SMAP ISO accept application/metadata+xml" c7-smap :iso-smap nil "application/metadata+xml"))

      (testing "unsupported formats"
        (are [mime-type xml?]
             (let [response (search/retrieve-concept (:concept-id c1-echo) nil {:accept mime-type})
                   err-msg (if xml?
                             (cx/string-at-path (fx/parse-str (:body response)) [:error])
                             (first (:errors (json/decode (:body response) true))))]
               (and (= 400 (:status response))
                    (= (str "The mime types specified in the accept header [" mime-type "] are not supported.") err-msg)))
             "text/csv" false)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Retrieve by concept-id + revision-id - format test
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest retrieve-metadata-from-search-by-concept-id-concept-revision
  (e/grant-all (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-all (s/context) (e/gran-catalog-item-id "provguid1"))
  (e/grant-all (s/context) (e/coll-catalog-item-id "provguid2"))

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
                                                :short-name "s1"}))]
    (index/wait-until-indexed)

    (testing "retrieve metadata from search by concept-id/revision-id"
      (testing "collections and granules"
        (are2 [item format-key accept concept-id revision-id]
              (let [response (search/retrieve-concept
                               concept-id
                               revision-id
                               {:accept accept})]
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
              umm-coll1-2 :echo10 mt/native "C1200000000-PROV1" 2))

      (testing "Requests for tombstone revision returns a 400 error"
        (let [{:keys [status errors] :as response} (search/get-search-failure-xml-data
                                                     (search/retrieve-concept
                                                       (:concept-id coll2-1) 2 {:throw-exceptions true}))]
          (is (= 400 status))
          (is (= #{"The revision [2] of concept [C1200000001-PROV1] represents a deleted concept and does not contain metadata."}
                 (set errors)))))

      (testing "Unknown concept-id returns a 404 error"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/retrieve-concept
                                          "C1234-PROV1" 1 {:throw-exceptions true}))]
          (is (= 404 status))
          (is (= #{"Concept with concept-id [C1234-PROV1] and revision-id [1] does not exist."}
                 (set errors)))))

      (testing "Known concept-id with unavailable revision-id returns a 404 error"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/retrieve-concept
                                          "C1200000000-PROV1" 1000000 {:throw-exceptions true}))]
          (is (= 404 status))
          (is (= #{"Concept with concept-id [C1200000000-PROV1] and revision-id [1000000] does not exist."}
                 (set errors)))))

      (testing "Non-integer revision id returns a 422 error"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/retrieve-concept
                                          "C1200000000-PROV1" "FOO" {:throw-exceptions true}))]
          (is (= 422 status))
          (is (= #{"Revision id [FOO] must be an integer greater than 0."}
                 (set errors)))))

      (testing "JSON output not supported (yet)"
        (let [{:keys [status errors]} (search/get-search-failure-data
                                        (search/retrieve-concept
                                          "C1200000000-PROV1" 1 {:accept mt/json :throw-exceptions true}))]
          (is (= 400 status))
          (is (= #{"The mime types specified in the accept header [application/json] are not supported."}
                 (set errors)))))

      (testing "Atom output not supported"
        (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                        (search/retrieve-concept
                                          "C1200000000-PROV1" 1 {:accept "application/atom+xml" :throw-exceptions true}))]
          (is (= 400 status))
          (is (= #{"The mime types specified in the accept header [application/atom+xml] are not supported."}
                 (set errors))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ACLs
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest acl-enforcement
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid3"))
  (e/grant-registered-users (s/context) (e/gran-catalog-item-id "provguid3"))
  (let [coll1 (d/ingest "PROV3" (dc/collection {:short-name "S1"
                                                :version-id "V1"
                                                :entry-title "ET1"}))
        coll2 (d/ingest "PROV4" (dc/collection {:short-name "S2"
                                                :version-id "V2"
                                                :entry-title "ET2"}))
        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1")]

    (testing "ACLs - concept-id retrieval"
      ;; no token
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id coll1) nil {:throw-exceptions true
                                                                 :query-params {}}))]
        (is (= 404 status))
        (is(= #{"Concept with concept-id [C1200000000-PROV3] could not be found."}
              (set errors))))
      ;; Guest users can't see PROV3 collections.
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id coll1) nil {:throw-exceptions true
                                                                 :headers {transmit-config/token-header
                                                                           guest-token}}))]
        (is (= 404 status))
        (is(= #{"Concept with concept-id [C1200000000-PROV3] could not be found."}
              (set errors))))
      ;; But registered users can see PROV3 collections.
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id coll1) nil {:throw-exceptions true
                                                                 :headers {transmit-config/token-header
                                                                           user1-token}}))]
        (is (= 200 status))
        (is(= nil errors)))
      ;; Even registered users can't see PROV4 collections.
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id coll2)  nil {:throw-exceptions true
                                                                  :headers {transmit-config/token-header
                                                                            user1-token}}))]
        (is (= 404 status))
        (is(= #{"Concept with concept-id [C1200000001-PROV4] could not be found."}
              (set errors)))))

    (testing "ACLs - concept-id + revision-id retrieval"
      ;; no token
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id coll1)
                                        (:revision-id coll1)
                                        {:throw-exceptions true}))]
        (is (= 404 status))
        (is(= #{"Concept with concept-id [C1200000000-PROV3] and revision-id [1] could not be found."}
              (set errors))))
      ;; Guest users can't see PROV3 collections.
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id coll1)
                                        (:revision-id coll1)
                                        {:throw-exceptions true
                                         :headers {transmit-config/token-header
                                                   guest-token}}))]
        (is (= 404 status))
        (is(= #{"Concept with concept-id [C1200000000-PROV3] and revision-id [1] could not be found."}
              (set errors))))
      ;; But registered users can see PROV3 collections.
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id coll1)
                                        (:revision-id coll1)
                                        {:throw-exceptions true
                                         :headers {transmit-config/token-header
                                                   user1-token}}))]
        (is (= 200 status))
        (is(= nil errors)))
      ;; Even registered users can't see PROV4 collections.
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/retrieve-concept
                                        (:concept-id coll2)
                                        (:revision-id coll2)
                                        {:throw-exceptions true
                                         :headers {transmit-config/token-header
                                                   user1-token}}))]
        (is (= 404 status))
        (is(= #{"Concept with concept-id [C1200000001-PROV4] and revision-id [1] could not be found."}
              (set errors)))))))


