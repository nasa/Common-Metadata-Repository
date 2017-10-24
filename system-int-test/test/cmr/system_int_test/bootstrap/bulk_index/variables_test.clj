(ns cmr.system-int-test.bootstrap.bulk-index.variables-test
  "Integration test for CMR bulk index variable operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
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

(deftest bulk-index-variables-for-provider
  (testing "Bulk index variables for a single provider"
    (s/only-with-real-database
      ;; Disable message publishing so items are not indexed.
      (core/disable-automatic-indexing)
      ;; The following is saved, but not indexed due to the above call
      (let [var1 (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)]
        (is (= 0 (:hits (variable/search {}))))
        (bootstrap/bulk-index-variables "PROV1")
        (index/wait-until-indexed)
        (testing "Variable concepts are indexed."
          (let [{:keys [hits items]} (variable/search {})]
            (is (= 1 hits))
            (is (= (:concept-id var1)
                   (:concept-id (first items)))))))
      (testing "Bulk index multilpe variables for a single provider")
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 2)
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 3)
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 4)
      (is (= 1 (:hits (variable/search {}))))
      (bootstrap/bulk-index-variables "PROV1")
      (index/wait-until-indexed)
      (let [{:keys [hits items]} (variable/search {})]
        (is (= 4 hits))
        (is (= 4 (count items))))
      ;; Re-enable message publishing.
      (core/reenable-automatic-indexing))))

(deftest bulk-index-variables
  (testing "Bulk index variables for multiple providers, explicitly"
    (s/only-with-real-database
      ;; Disable message publishing so items are not indexed.
      (core/disable-automatic-indexing)
      ;; The following are saved, but not indexed due to the above call
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 2)
      (variable/ingest-variable-with-attrs {:provider-id "PROV2"} {} 3)
      (variable/ingest-variable-with-attrs {:provider-id "PROV2"} {} 4)
      (variable/ingest-variable-with-attrs {:provider-id "PROV3"} {} 5)
      (variable/ingest-variable-with-attrs {:provider-id "PROV3"} {} 6)
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
      (core/reenable-automatic-indexing))))

(deftest bulk-index-all-variables
  (testing "Bulk index variables for multiple providers, implicitly"
    (s/only-with-real-database
      ;; Disable message publishing so items are not indexed.
      (core/disable-automatic-indexing)
      ;; The following are saved, but not indexed due to the above call
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)
      (variable/ingest-variable-with-attrs {:provider-id "PROV2"} {} 2)
      (variable/ingest-variable-with-attrs {:provider-id "PROV3"} {} 3)
      (is (= 0 (:hits (variable/search {}))))
      (bootstrap/bulk-index-variables)
      (index/wait-until-indexed)
      (testing "Variable concepts are indexed."
        (let [{:keys [hits items]} (variable/search {})]
          (is (= 3 hits))
          (is (= 3 (count items)))))
      ;; Re-enable message publishing.
      (core/reenable-automatic-indexing))))

(deftest bulk-index-variable-revisions
  (testing "Bulk index variables index all revisions index as well"
    (s/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     (let [token (e/login (s/context) "user1")
           var1-concept (variable/make-variable-concept {:native-id "var1"
                                                         :Name "Variable1"
                                                         :provider-id "PROV1"})
           var1-1 (variable/ingest-variable var1-concept)
           var1-2-tombstone (merge (ingest/delete-concept var1-concept {:token token})
                                   var1-concept
                                   {:deleted true
                                    :user-id "user1"})
           var1-3 (variable/ingest-variable var1-concept)

           var2-1-concept (variable/make-variable-concept {:native-id "var2"
                                                           :Name "Variable2"
                                                           :LongName "LongName2"
                                                           :provider-id "PROV2"})
           var2-1 (variable/ingest-variable var2-1-concept)
           var2-2-concept (variable/make-variable-concept {:native-id "var2"
                                                           :Name "Variable2-2"
                                                           :LongName "LongName2-2"
                                                           :provider-id "PROV2"})
           var2-2 (variable/ingest-variable var2-2-concept)
           var2-3-tombstone (merge (ingest/delete-concept var2-2-concept {:token token})
                                   var2-2-concept
                                   {:deleted true
                                    :user-id "user1"})
           var3 (variable/ingest-variable-with-attrs {:native-id "var3"
                                                      :Name "Variable1"
                                                      :LongName "LongName3"
                                                      :provider-id "PROV3"})]
       ;; Before bulk indexing, search for variables found nothing
       (variable/assert-variable-references-match
        []
        (search/find-refs :variable {:all-revisions true}))

       ;; Just index PROV1
       (bootstrap/bulk-index-variables "PROV1")
       (index/wait-until-indexed)

       ;; CMR-4398 does not index variables under a provider correctly, enable the following verification once CMR-4398 is fixed.
       ;; After bulk indexing a provider, search found all variable revisions of that provider
       #_(variable/assert-variable-references-match
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
