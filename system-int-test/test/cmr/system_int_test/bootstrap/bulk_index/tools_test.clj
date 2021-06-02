(ns cmr.system-int-test.bootstrap.bulk-index.tools-test
  "Integration test for CMR bulk index tool operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.collection :as data-collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-json :as data-umm-json]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.association-util :as assoc-util]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tool-util :as tool]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

(deftest ^:oracle bulk-index-tools-for-provider
  (testing "Bulk index tools for a single provider"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following is saved, but not indexed due to the above call
     (let [tool1 (tool/ingest-tool-with-attrs {:provider-id "PROV1"} {} 1)
           ;; create a tool on a different provider PROV2
           ;; and this tool won't be indexed as a result of indexing tools of PROV1
           tool2 (tool/ingest-tool-with-attrs {:provider-id "PROV2"} {} 1)
           {:keys [status errors]} (bootstrap/bulk-index-tools "PROV1" nil)]
       
       ;; The above bulk-index-tools call with nil headers has no token 
       (is (= [401 ["You do not have permission to perform that action."]]
              [status errors]))
       (is (= 0 (:hits (search/find-refs :tool {}))))

       ;; The following bulk-index-tools call uses system token.
       (bootstrap/bulk-index-tools "PROV1")
       (index/wait-until-indexed)

       (testing "Tool concepts are indexed."
         ;; Note: only tool1 is indexed, tool2 is not.
         (let [{:keys [hits refs]} (search/find-refs :tool {})]
           (is (= 1 hits))
           (is (= (:concept-id tool1)
                  (:id (first refs))))))

       (testing "Bulk index multilpe tools for a single provider"
         ;; Ingest three more tools
         (tool/ingest-tool-with-attrs {:provider-id "PROV1"} {} 2)
         (tool/ingest-tool-with-attrs {:provider-id "PROV1"} {} 3)
         (tool/ingest-tool-with-attrs {:provider-id "PROV1"} {} 4)
         
         ;; The above three new tools are not indexed, only tool1 is indexed. 
         (is (= 1 (:hits (search/find-refs :tool {}))))
    
         ;; bulk index again, all the tools in PROV1 should be indexed. 
         (bootstrap/bulk-index-tools "PROV1")
         (index/wait-until-indexed)
     
         (let [{:keys [hits refs]} (search/find-refs :tool {})]
           (is (= 4 hits))
           (is (= 4 (count refs))))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-tools
  (testing "Bulk index tools for multiple providers, explicitly"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (tool/ingest-tool-with-attrs {:provider-id "PROV1"} {} 1)
     (tool/ingest-tool-with-attrs {:provider-id "PROV1"} {} 2)
     (tool/ingest-tool-with-attrs {:provider-id "PROV2"} {} 3)
     (tool/ingest-tool-with-attrs {:provider-id "PROV2"} {} 4)
     (tool/ingest-tool-with-attrs {:provider-id "PROV3"} {} 5)
     (tool/ingest-tool-with-attrs {:provider-id "PROV3"} {} 6)

     (is (= 0 (:hits (search/find-refs :tool {}))))

     (bootstrap/bulk-index-tools "PROV1")
     (bootstrap/bulk-index-tools "PROV2")
     (bootstrap/bulk-index-tools "PROV3")
     (index/wait-until-indexed)

     (testing "Tool concepts are indexed."
       (let [{:keys [hits refs] :as response} (search/find-refs :tool {})]
         (is (= 6 hits))
         (is (= 6 (count refs)))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-all-tools
  (testing "Bulk index tools for multiple providers, implicitly"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (tool/ingest-tool-with-attrs {:provider-id "PROV1"} {} 1)
     (tool/ingest-tool-with-attrs {:provider-id "PROV2"} {} 2)
     (tool/ingest-tool-with-attrs {:provider-id "PROV3"} {} 3)

     (is (= 0 (:hits (search/find-refs :tool {}))))

     (bootstrap/bulk-index-tools)
     (index/wait-until-indexed)

     (testing "Tool concepts are indexed."
       (let [{:keys [hits refs]} (search/find-refs :tool {})]
         (is (= 3 hits))
         (is (= 3 (count refs)))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-tool-revisions
  (testing "Bulk index tools index all revisions index as well"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     (let [token (echo-util/login (system/context) "user1")
           tool1-concept (tool/make-tool-concept {:native-id "TOOL1"
                                                  :Name "Tool1"
                                                  :provider-id "PROV1"})
           tool2-concept (tool/make-tool-concept {:native-id "TOOL2"
                                                  :Name "Tool2"
                                                  :provider-id "PROV2"})
           tool2-2-concept (tool/make-tool-concept {:native-id "TOOL2"
                                                    :Name "Tool2-2"
                                                    :provider-id "PROV2"})
           tool3-concept (tool/make-tool-concept {:native-id "TOOL3"
                                                  :Name "Tool1"
                                                  :provider-id "PROV3"})
           tool1-1 (tool/ingest-tool tool1-concept)
           tool1-2-tombstone (merge (ingest/delete-concept
                                      tool1-concept (tool/token-opts token))
                                    tool1-concept
                                    {:deleted true
                                     :user-id "user1"})
           tool1-3 (tool/ingest-tool tool1-concept)
           tool2-1 (tool/ingest-tool tool2-concept)
           tool2-2 (tool/ingest-tool tool2-2-concept)
           tool2-3-tombstone (merge (ingest/delete-concept
                                      tool2-2-concept (tool/token-opts token))
                                    tool2-2-concept
                                    {:deleted true
                                     :user-id "user1"})
           tool3 (tool/ingest-tool tool3-concept)]
       ;; Before bulk indexing, search for services found nothing
       (data-umm-json/assert-tool-umm-jsons-match
        umm-version/current-tool-version
        []
        (search/find-concepts-umm-json :tool {:all-revisions true}))

       ;; Just index PROV1
       (bootstrap/bulk-index-tools "PROV1")
       (index/wait-until-indexed)

       ;; After bulk indexing a provider, search found all tool revisions for the provider
       ;; of that provider
       (data-umm-json/assert-tool-umm-jsons-match
        umm-version/current-tool-version
        [tool1-1 tool1-2-tombstone tool1-3]
        (search/find-concepts-umm-json :tool {:all-revisions true}))

       ;; Now index all tools
       (bootstrap/bulk-index-tools)
       (index/wait-until-indexed)

       ;; After bulk indexing, search for tools found all revisions
       (data-umm-json/assert-tool-umm-jsons-match
        umm-version/current-tool-version
        [tool1-1 tool1-2-tombstone tool1-3 tool2-1 tool2-2 tool2-3-tombstone tool3]
        (search/find-concepts-umm-json :tool {:all-revisions true}))

       ;; Re-enable message publishing.
       (core/reenable-automatic-indexing)))))

(deftest bulk-index-collections-with-tool-association-test
  (system/only-with-real-database
   (let [coll1 (data-core/ingest "PROV1" (data-collection/collection {:entry-title "coll1"}))
         coll1-concept-id (:concept-id coll1)
         token (echo-util/login (system/context) "user1")
         {tool1-concept-id :concept-id} (tool/ingest-tool-with-attrs
                                         {:native-id "tool1"
                                          :Name "toolname1"})
         {tool2-concept-id :concept-id} (tool/ingest-tool-with-attrs
                                         {:native-id "tool2"
                                          :Name "toolname2"})]
     ;; index the collection and tools so that they can be found during tool association
     (index/wait-until-indexed)

     (core/disable-automatic-indexing)
     (assoc-util/associate-by-concept-ids token tool1-concept-id [{:concept-id coll1-concept-id}])
     ;; tool 2 is used to test tool association tombstone is indexed correctly
     (assoc-util/associate-by-concept-ids token tool2-concept-id [{:concept-id coll1-concept-id}])
     (assoc-util/dissociate-by-concept-ids token tool2-concept-id [{:concept-id coll1-concept-id}])
     (core/reenable-automatic-indexing)

     ;; bulk index the collection
     (bootstrap/bulk-index-provider "PROV1")
     (index/wait-until-indexed)

     ;; verify collection is associated with tool1, not tool2
     (tool/assert-collection-search-result coll1 {:has-formats false} [tool1-concept-id]))))
