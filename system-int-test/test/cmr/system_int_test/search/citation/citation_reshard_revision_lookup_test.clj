(ns cmr.system-int-test.search.citation.citation-reshard-revision-lookup-test
  "Verifies citation search behavior after resharding when an older DB revision has been removed."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.elastic-util :as es-util]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- create-citation-document
  [provider-id native-id name identifier identifier-type & [optional-fields]]
  (let [doc (merge
             {:Name name
              :Identifier identifier
              :IdentifierType identifier-type
              :ResolutionAuthority "https://doi.org"
              :MetadataSpecification
              {:URL "https://cdn.earthdata.nasa.gov/generics/citation/v1.0.0"
               :Name "Citation"
               :Version "1.0.0"}}
             optional-fields)]
    (ingest/ingest-concept
     {:provider-id provider-id
      :concept-type :citation
      :native-id native-id
      :format "application/json"
      :metadata (json/generate-string doc)})))

(deftest ^:oracle citation-umm-json-search-after-reshard-with-missing-old-db-revision
  (let [citation-index-name "1_generic_citation"
        resharded-index-name (str citation-index-name "_2_shards")
        native-id "reshard-citation"
        identifier "10.5067/RESHARD-CITATION"
        citation-r1 (create-citation-document
                     "PROV1"
                     native-id
                     "Reshard Citation"
                     identifier
                     "DOI"
                     {:CitationMetadata
                      {:Title "Reshard Citation"
                       :Year 2024
                       :Type "journal-article"}})
        concept-id (:concept-id citation-r1)
        citation-r2 (create-citation-document
                     "PROV1"
                     native-id
                     "Reshard Citation Updated"
                     identifier
                     "DOI"
                     {:CitationMetadata
                      {:Title "Reshard Citation Updated"
                       :Year 2025
                       :Type "journal-article"}})]
    (index/wait-until-indexed)

    (testing "sanity check before removing the old DB revision"
      (let [json-response (search/find-concepts-json :citation {:concept-id concept-id})
            umm-json-response (search/find-concepts-umm-json :citation {:concept-id concept-id})]
        (is (= 1 (:hits json-response)) (pr-str json-response))
        (is (= 200 (:status umm-json-response)) (pr-str umm-json-response))
        (is (= 1 (count (get-in umm-json-response [:results :items]))))
        (is (= 2 (get-in umm-json-response [:results :items 0 :meta :revision-id])))))

    ;; This simulates the post-cleanup production state where the citation's superseded
    ;; revision is gone from metadata-db, but the latest revision still exists.
    (is (= 200 (:status (mdb/force-delete-concept concept-id (:revision-id citation-r1)))))
    (is (nil? (mdb/get-concept concept-id (:revision-id citation-r1))))
    (is (= 2 (:revision-id (mdb/get-concept concept-id (:revision-id citation-r2)))))

    (testing "sanity check before resharding"
      (let [json-response (search/find-concepts-json :citation {:concept-id concept-id})
            umm-json-response (search/find-concepts-umm-json :citation {:concept-id concept-id})]
        (is (= 1 (:hits json-response)) (pr-str json-response))
        (is (= 200 (:status umm-json-response)) (pr-str umm-json-response))
        (is (= 1 (count (get-in umm-json-response [:results :items]))))
        (is (= 2 (get-in umm-json-response [:results :items 0 :meta :revision-id])))))

    (let [original-doc (es-util/get-doc citation-index-name concept-id "elastic")
          start-reshard-response (bootstrap/start-reshard-index
                                  citation-index-name
                                  {:synchronous true
                                   :num-shards 2
                                   :elastic-name "elastic"})
          task-id (:task-id start-reshard-response)
          _ (bootstrap/wait-for-reshard-complete
             citation-index-name
             "elastic"
             task-id
             {})
          finalize-reshard-response (bootstrap/finalize-reshard-index
                                     citation-index-name
                                     {:synchronous true
                                      :elastic-name "elastic"})
          resharded-doc (es-util/get-doc resharded-index-name concept-id "elastic")
          json-response (search/find-concepts-json :citation {:concept-id concept-id})
          umm-json-response (search/find-concepts-umm-json :citation {:concept-id concept-id})
          umm-json-items (get-in umm-json-response [:results :items])]
      (is (= 200 (:status start-reshard-response)) (pr-str start-reshard-response))
      (is (= 2 (get-in original-doc [:_source :revision-id])) (pr-str original-doc))
      (is (= 2 (:_version original-doc)) (pr-str original-doc))
      (is (= (:_version original-doc) (:_version resharded-doc))
          (pr-str {:original-es-version (:_version original-doc)
                   :resharded-es-version (:_version resharded-doc)
                   :resharded-source-revision (get-in resharded-doc [:_source :revision-id])}))
      (is (= 200 (:status finalize-reshard-response)) (pr-str finalize-reshard-response))
      (is (= 2 (get-in resharded-doc [:_source :revision-id])) (pr-str resharded-doc))

      ;; Citations use the generic UMM JSON handler, which reads revision-id from
      ;; the elastic result to fetch the latest metadata-db revision.
      (is (= 1 (:hits json-response)) (pr-str json-response))
      (is (= 200 (:status umm-json-response))
          (pr-str {:umm-json-response umm-json-response
                   :original-es-version (:_version original-doc)
                   :resharded-es-version (:_version resharded-doc)
                   :resharded-source-revision (get-in resharded-doc [:_source :revision-id])}))
      (is (= 1 (count umm-json-items))
          (pr-str {:umm-json-response umm-json-response
                   :json-response json-response
                   :original-es-version (:_version original-doc)
                   :resharded-es-version (:_version resharded-doc)
                   :resharded-source-revision (get-in resharded-doc [:_source :revision-id])}))
      (is (= 2 (get-in umm-json-items [0 :meta :revision-id]))
          (pr-str {:umm-json-items umm-json-items
                   :original-es-version (:_version original-doc)
                   :resharded-es-version (:_version resharded-doc)
                   :resharded-source-revision (get-in resharded-doc [:_source :revision-id])})))))
