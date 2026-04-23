(ns cmr.system-int-test.search.granule.granule-reshard-revision-lookup-test
  "Reproduces granule search behavior after resharding when an older DB revision has been removed."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.elastic-util :as es-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest ^:oracle granule-umm-json-search-after-reshard-with-missing-old-db-revision
  (let [small-collections-index-name "1_small_collections"
        resharded-index-name (str small-collections-index-name "_2_shards")
        granule-ur "reshard-granule"
        collection (d/ingest-umm-spec-collection
                    "PROV1"
                    (data-umm-c/collection
                     {:ShortName "RESHARD"
                      :Version "1"
                      :EntryTitle "reshard-collection"}))
        granule (dg/granule-with-umm-spec-collection
                 collection
                 (:concept-id collection)
                 {:granule-ur granule-ur})
        granule-r1 (d/ingest "PROV1" granule {:format :umm-json})
        granule-r2 (d/ingest "PROV1" granule {:format :umm-json})
        concept-id (:concept-id granule-r2)]
    (index/wait-until-indexed)

    (testing "sanity check before removing the old DB revision"
      (let [json-response (search/find-concepts-json :granule {:granule-ur granule-ur})
            umm-json-response (search/find-concepts-umm-json :granule {:granule-ur granule-ur})]
        (is (= 1 (:hits json-response)) (pr-str json-response))
        (is (= 200 (:status umm-json-response)) (pr-str umm-json-response))
        (is (= 1 (count (get-in umm-json-response [:results :items]))))
        (is (= 2 (get-in umm-json-response [:results :items 0 :meta :revision-id])))))

    ;; This simulates the post-cleanup production state where the granule's superseded
    ;; revision is gone from metadata-db, but the latest revision still exists.
    (is (= 200 (:status (mdb/force-delete-concept concept-id (:revision-id granule-r1)))))
    (is (nil? (mdb/get-concept concept-id (:revision-id granule-r1))))
    (is (= 2 (:revision-id (mdb/get-concept concept-id (:revision-id granule-r2)))))

    (testing "sanity check before resharding"
      (let [json-response (search/find-concepts-json :granule {:granule-ur granule-ur})
            umm-json-response (search/find-concepts-umm-json :granule {:granule-ur granule-ur})]
        (is (= 1 (:hits json-response)) (pr-str json-response))
        (is (= 200 (:status umm-json-response)) (pr-str umm-json-response))
        (is (= 1 (count (get-in umm-json-response [:results :items]))))
        (is (= 2 (get-in umm-json-response [:results :items 0 :meta :revision-id])))))

    (let [original-doc (es-util/get-doc small-collections-index-name concept-id "gran-elastic")
          start-reshard-response (bootstrap/start-reshard-index
                                  small-collections-index-name
                                  {:synchronous true
                                   :num-shards 2
                                   :elastic-name "gran-elastic"})
          task-id (:task-id start-reshard-response)
          resharded-doc (es-util/get-doc resharded-index-name concept-id "gran-elastic")
          _ (bootstrap/wait-for-reshard-complete
             small-collections-index-name
             "gran-elastic"
             task-id
             {})
          finalize-reshard-response (bootstrap/finalize-reshard-index
                                     small-collections-index-name
                                     {:synchronous true
                                      :elastic-name "gran-elastic"})
          json-response (search/find-concepts-json :granule {:granule-ur granule-ur})
          umm-json-response (search/find-concepts-umm-json :granule {:granule-ur granule-ur})
          umm-json-items (get-in umm-json-response [:results :items])]
      (is (= 200 (:status start-reshard-response)) (pr-str start-reshard-response))
      (is (= 2 (get-in original-doc [:_source :revision-id])) (pr-str original-doc))
      (is (= 2 (:_version original-doc)) (pr-str original-doc))
      (is (= 2 (get-in resharded-doc [:_source :revision-id])) (pr-str resharded-doc))
      (is (= (:_version original-doc) (:_version resharded-doc))
          (pr-str {:original-es-version (:_version original-doc)
                   :resharded-es-version (:_version resharded-doc)
                   :resharded-source-revision (get-in resharded-doc [:_source :revision-id])}))
      (is (= 200 (:status finalize-reshard-response)) (pr-str finalize-reshard-response))

      ;; The reshard path should preserve the elastic version so granule DB-backed
      ;; formats continue to look up the latest revision after finalize.
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
