(ns cmr.system-int-test.ingest.umm-json-ingest-test
  "Tests verifying ingest of UMM JSON collections."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.umm-spec.test.expected-conversion :as exc]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]
            [cmr.umm-spec.json-schema :as js]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.granule :as dg]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def test-context (lkt/setup-context-for-test lkt/sample-keyword-map))

(deftest ingest-umm-json
  (let [json (umm-spec/generate-metadata test-context exc/example-collection-record :umm-json)
        coll-map {:provider-id "PROV1"
                  :native-id "umm_json_coll_V1"
                  :revision-id "1"
                  :concept-type :collection
                  ;; assumes the current version
                  :format "application/vnd.nasa.cmr.umm+json"
                  :metadata json}
        response (ingest/ingest-concept coll-map)]
    (is (= 200 (:status response)))
    (index/wait-until-indexed)
    (is (mdb/concept-exists-in-mdb? (:concept-id response) 1))
    (is (= 1 (:revision-id response)))

    (testing "UMM-JSON collections are searchable after ingest"
      (is (= 1 (count (:refs (search/find-refs :collection {"entry-title" "The entry title V5"}))))))

    (testing "Updating a UMM-JSON collection"
      (let [response (ingest/ingest-concept (assoc coll-map :revision-id "2"))]
        (is (= 200 (:status response)))
        (index/wait-until-indexed)
        (is (mdb/concept-exists-in-mdb? (:concept-id response) 2))
        (is (= 2 (:revision-id response))))))

  (testing "ingesting UMM JSON with parsing errors"
    (let [json (umm-spec/generate-metadata test-context (assoc exc/example-collection-record
                                                         :DataDates
                                                         [{:Date "invalid date"
                                                           :Type "CREATE"}])
                                           :umm-json)
          concept-map {:provider-id "PROV1"
                       :native-id "umm_json_coll_2"
                       :revision-id "1"
                       :concept-type :collection
                       :format "application/vnd.nasa.cmr.umm+json"
                       :metadata json}
          response (ingest/ingest-concept concept-map {:accept-format :json})]
      (is (= ["/DataDates/0/Date string \"invalid date\" is invalid against requested date format(s) [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.SSSZ]"] (:errors response)))
      (is (= 400 (:status response))))))

(deftest ingest-old-json-versions
  (let [json     (umm-spec/generate-metadata test-context exc/example-collection-record "application/vnd.nasa.cmr.umm+json;version=1.0")
        coll-map {:provider-id  "PROV1"
                  :native-id    "umm_json_coll_V1"
                  :concept-type :collection
                  :format       "application/vnd.nasa.cmr.umm+json;version=1.0"
                  :metadata     json}
        response (ingest/ingest-concept coll-map {:accept-format :json})]
    (is (= 200 (:status response)))))

(deftest ingest-invalid-umm-version
  (let [coll-map {:provider-id  "PROV1"
                  :native-id    "umm_json_coll_V1"
                  :concept-type :collection
                  :format       "application/vnd.nasa.cmr.umm+json;version=9000.1"
                  :metadata     "{\"foo\":\"bar\"}"}
        response (ingest/ingest-concept coll-map {:accept-format :json})]
    (is (= 400 (:status response)))
    (is (= ["Unknown UMM JSON schema version: \"9000.1\""]
           (:errors response)))))

(deftest ingest-granule-with-parent-umm-collection-test
  (let [cddis-umm (-> "example_data/umm-json/1.2/CDDIS.json" io/resource slurp)
        metadata-format "application/vnd.nasa.cmr.umm+json;version=1.2"
        coll-concept-id "C1-PROV1"
        gran-concept-id "G1-PROV1"
        coll-map {:provider-id  "PROV1"
                  :native-id    "umm_json_cddis_V1"
                  :concept-type :collection
                  :concept-id   coll-concept-id
                  :format       metadata-format
                  :metadata     cddis-umm}
        ingest-collection-response (ingest/ingest-concept coll-map {:accept-format :json})
        _ (index/wait-until-indexed)
        coll-retrieval-response (search/retrieve-concept coll-concept-id 1 {:url-extension "native"})
        umm-collection (-> coll-retrieval-response :body json/parse-string)
        collection-content-type (-> coll-retrieval-response :headers (get "Content-Type"))
        granule (d/item->concept (dg/granule-with-umm-spec-collection umm-collection
                                                                      coll-concept-id
                                                                      {:concept-id gran-concept-id}))
        ingest-granule-response (ingest/ingest-concept granule)
        _ (index/wait-until-indexed)
        granule-search-response (search/find-refs :granule {:concept-id gran-concept-id})]
    (testing "Collection ingested successfully as version 1.2 UMM JSON"
      (is (= 200 (:status ingest-collection-response)))
      (is (= "application/vnd.nasa.cmr.umm+json;version=1.2; charset=utf-8"
             collection-content-type)))
    (testing "Granule ingested successfully"
      (is (= 200 (:status ingest-granule-response))))
    (testing "Granule successfully indexed for search"
      (is (= 1 (:hits granule-search-response)))
      (is (= gran-concept-id (-> granule-search-response :refs first :id))))))
