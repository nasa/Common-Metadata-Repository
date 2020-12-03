(ns cmr.system-int-test.bootstrap.bulk-index.variables-test
  "Integration test for CMR bulk index variable operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.collection :as data2-collection]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variable]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

(deftest ^:oracle bulk-index-variables-for-provider
  (testing "Bulk index variables for a single provider"
    (s/only-with-real-database
      (let [coll1 (data2-core/ingest
                   "PROV1"
                   (data2-collection/collection {:entry-title "ET1"
                                                 :short-name "S1"
                                                 :version-id "V1"}))
            coll2 (data2-core/ingest
                  "PROV2"
                  (data2-collection/collection {:entry-title "ET2"
                                                :short-name "S2"
                                                :version-id "V2"}))
            _ (index/wait-until-indexed)
            var1 (variable/make-variable-concept
                  {:Name "Variable1"}
                  {:native-id "var1"
                   :coll-concept-id (:concept-id coll1)})
            var2 (variable/make-variable-concept
                  {:Name "Variable2"}
                  {:native-id "var2"
                   :coll-concept-id (:concept-id coll2)})
            ;; Disable message publishing so items are not indexed.
            _ (core/disable-automatic-indexing)
            ;; The following is saved, but not indexed due to the above call
            {var1-concept-id :concept-id} (variable/ingest-variable-with-association var1)
            {var2-concept-id :concept-id} (variable/ingest-variable-with-association var2)
            {:keys [status errors]} (bootstrap/bulk-index-variables "PROV1" nil)]

        (is (= [401 ["You do not have permission to perform that action."]]
               [status errors]))
        (is (= 0 (:hits (variable/search {}))))
        (bootstrap/bulk-index-variables "PROV1")
        (index/wait-until-indexed)
        (testing "Variable concepts are indexed."
          (let [{:keys [hits items]} (variable/search {})]
            (is (= 1 hits))
            (is (= var1-concept-id
                   (:concept-id (first items)))))))
      (testing "Bulk index multilpe variables for a single provider")
      ;; Re-enable message publishing for collection to be indexed.
      (core/reenable-automatic-indexing)
      (let [coll1 (data2-core/ingest
                  "PROV1"
                  (data2-collection/collection {:entry-title "ET1"
                                                :short-name "S1"
                                                :version-id "V1"}))
           _ (index/wait-until-indexed)
           var2 (variable/make-variable-concept
                 {:Name "Variable2"}
                 {:native-id "var2"
                  :coll-concept-id (:concept-id coll1)})
           var3 (variable/make-variable-concept
                 {:Name "Variable3"}
                 {:native-id "var3"
                  :coll-concept-id (:concept-id coll1)})
           var4 (variable/make-variable-concept
                 {:Name "Variable4"}
                 {:native-id "var4"
                  :coll-concept-id (:concept-id coll1)})]
        ;; Disable message publishing so items are not indexed.
        (core/disable-automatic-indexing)
        ;; The following is saved, but not indexed due to the above call
        (variable/ingest-variable-with-association var2)
        (variable/ingest-variable-with-association var3)
        (variable/ingest-variable-with-association var4)
        (is (= 1 (:hits (variable/search {}))))
        (bootstrap/bulk-index-variables "PROV1")
        (index/wait-until-indexed)
        (let [{:keys [hits items]} (variable/search {})]
          (is (= 4 hits))
          (is (= 4 (count items))))
        ;; Re-enable message publishing.
        (core/reenable-automatic-indexing)))))

(deftest ^:oracle bulk-index-variables
  (testing "Bulk index variables for multiple providers, explicitly"
    (s/only-with-real-database
     (let [coll1 (data2-core/ingest
                  "PROV1"
                  (data2-collection/collection {:entry-title "ET1"
                                                :short-name "S1"
                                                :version-id "V1"}))
           coll2 (data2-core/ingest
                  "PROV2"
                  (data2-collection/collection {:entry-title "ET2"
                                                :short-name "S2"
                                                :version-id "V2"}))
           coll3 (data2-core/ingest
                  "PROV3"
                  (data2-collection/collection {:entry-title "ET3"
                                                :short-name "S3"
                                                :version-id "V3"}))
           _ (index/wait-until-indexed)
           var1 (variable/make-variable-concept
                 {:Name "Variable1"}
                 {:native-id "var1"
                  :coll-concept-id (:concept-id coll1)})
           var2 (variable/make-variable-concept
                 {:Name "Variable2"}
                 {:native-id "var2"
                  :coll-concept-id (:concept-id coll1)})
           var3 (variable/make-variable-concept
                 {:Name "Variable3"}
                 {:native-id "var3"
                  :coll-concept-id (:concept-id coll2)})
           var4 (variable/make-variable-concept
                 {:Name "Variable4"}
                 {:native-id "var4"
                  :coll-concept-id (:concept-id coll2)})
           var5 (variable/make-variable-concept
                 {:Name "Variable5"}
                 {:native-id "var5"
                  :coll-concept-id (:concept-id coll3)})
           var6 (variable/make-variable-concept
                 {:Name "Variable6"}
                 {:native-id "var6"
                  :coll-concept-id (:concept-id coll3)})]
       ;; Disable message publishing so items are not indexed.
       (core/disable-automatic-indexing)
       ;; The following are saved, but not indexed due to the above call
       (variable/ingest-variable-with-association var1)
       (variable/ingest-variable-with-association var2)
       (variable/ingest-variable-with-association var3)
       (variable/ingest-variable-with-association var4)
       (variable/ingest-variable-with-association var5)
       (variable/ingest-variable-with-association var6) 
       (is (= 0 (:hits (variable/search {}))))
       (bootstrap/bulk-index-variables "PROV1")
       (bootstrap/bulk-index-variables "PROV2")
       (bootstrap/bulk-index-variables "PROV3")
       (index/wait-until-indexed)
       (testing "Variable concepts are indexed."
         (let [{:keys [hits items]} (variable/search {})]
           (is (= 6 hits))
           (is (= 6 (count items)))))
       ;; Re-enable message publishing.
       (core/reenable-automatic-indexing)))))

(deftest ^:oracle bulk-index-all-variables
  (testing "Bulk index variables for multiple providers, implicitly"
    (s/only-with-real-database
     (let [coll1 (data2-core/ingest
                  "PROV1"
                  (data2-collection/collection {:entry-title "ET1"
                                                :short-name "S1"
                                                :version-id "V1"}))
           coll2 (data2-core/ingest
                  "PROV2"
                  (data2-collection/collection {:entry-title "ET2"
                                                :short-name "S2"
                                                :version-id "V2"}))
           coll3 (data2-core/ingest
                  "PROV3"
                  (data2-collection/collection {:entry-title "ET3"
                                                :short-name "S3"
                                                :version-id "V3"}))
           _ (index/wait-until-indexed)
           var1 (variable/make-variable-concept
                 {:Name "Variable1"}
                 {:native-id "var1"
                  :coll-concept-id (:concept-id coll1)})
           var2 (variable/make-variable-concept
                 {:Name "Variable2"}
                 {:native-id "var2"
                  :coll-concept-id (:concept-id coll2)})
           var3 (variable/make-variable-concept
                 {:Name "Variable3"}
                 {:native-id "var3"
                  :coll-concept-id (:concept-id coll3)})]
       ;; Disable message publishing so items are not indexed.
       (core/disable-automatic-indexing)
       ;; The following are saved, but not indexed due to the above call
       (variable/ingest-variable-with-association var1)
       (variable/ingest-variable-with-association var2)
       (variable/ingest-variable-with-association var3)
       (is (= 0 (:hits (variable/search {}))))
       (bootstrap/bulk-index-variables)
       (index/wait-until-indexed)
       (testing "Variable concepts are indexed."
         (let [{:keys [hits items]} (variable/search {})]
           (is (= 3 hits))
           (is (= 3 (count items)))))
       ;; Re-enable message publishing.
       (core/reenable-automatic-indexing)))))

(deftest ^:oracle bulk-index-variable-revisions
  (testing "Bulk index variables index all revisions index as well"
    (s/only-with-real-database
     (let [token (e/login (s/context) "user1")
           coll1 (data2-core/ingest
                  "PROV1"
                  (data2-collection/collection {:entry-title "ET1"
                                                :short-name "S1"
                                                :version-id "V1"}))
           coll2 (data2-core/ingest
                  "PROV2"
                  (data2-collection/collection {:entry-title "ET2"
                                                :short-name "S2"
                                                :version-id "V2"}))
           coll3 (data2-core/ingest
                  "PROV3"
                  (data2-collection/collection {:entry-title "ET3"
                                                :short-name "S3"
                                                :version-id "V3"}))
           _ (index/wait-until-indexed)
           ;; Disable message publishing so items are not indexed.
           _ (core/disable-automatic-indexing) 
           var1-concept (variable/make-variable-concept
                         {:Name "Variable1"}
                         {:native-id "var1" 
                          :coll-concept-id (:concept-id coll1)})
           var1-1 (variable/ingest-variable-with-association var1-concept)
           var1-2-tombstone (merge (ingest/delete-concept var1-concept {:token token})
                                   var1-concept
                                   {:deleted true
                                    :user-id "user1"})
           var1-3 (variable/ingest-variable-with-association var1-concept)

           var2-1-concept (variable/make-variable-concept 
                           {:Name "Variable2"
                            :LongName "LongName2"}
                           {:native-id "var2"
                            :coll-concept-id (:concept-id coll2)})
           var2-1 (variable/ingest-variable-with-association var2-1-concept)
           var2-2-concept (variable/make-variable-concept
                           {:Name "Variable2"
                            :LongName "LongName2-2"}
                           {:native-id "var2"
                            :provider-id "PROV2"
                            :coll-concept-id (:concept-id coll2)})
           var2-2 (variable/ingest-variable-with-association var2-2-concept)
           var2-3-tombstone (merge (ingest/delete-concept var2-2-concept {:token token})
                                   var2-2-concept
                                   {:deleted true
                                    :user-id "user1"})
           var3-concept (variable/make-variable-concept
                         {:Name "Variable1"
                          :LongName "LongName3"}
                         {:native-id "var3"
                          :coll-concept-id (:concept-id coll3)})
           var3 (variable/ingest-variable-with-association var3-concept)]
       ;; Before bulk indexing, search for variables found nothing
       (variable/assert-variable-references-match
        []
        (search/find-refs :variable {:all-revisions true}))

       ;; Just index PROV1
       (bootstrap/bulk-index-variables "PROV1")
       (index/wait-until-indexed)

       ;; After bulk indexing a provider, search found all variable revisions of that provider
       (variable/assert-variable-references-match
        [var1-1 var1-2-tombstone var1-3]
        (search/find-refs :variable {:all-revisions true}))

       ;; Now index all variables
       (bootstrap/bulk-index-variables)
       (index/wait-until-indexed)

       ;; After bulk indexing, search for variables found all revisions
       (variable/assert-variable-references-match
        [var1-1 var1-2-tombstone var1-3 var2-1 var2-2 var2-3-tombstone var3]
        (search/find-refs :variable {:all-revisions true}))

       ;; Re-enable message publishing.
       (core/reenable-automatic-indexing)))))
