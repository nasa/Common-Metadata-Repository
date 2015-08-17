(ns cmr.system-int-test.search.umm-json-search-test
  "Integration test for UMMJSON format search"
  (:require [clojure.test :refer :all]
            [clojure.core.incubator :as incubator]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :refer [are2]]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- collection->umm-json
  "Returns the collection in umm-json format."
  [collection]
  (let [{{:keys [short-name version-id]} :product
         {:keys [delete-time]} :data-provider-timestamps
         :keys [entry-id entry-title user-id format-key
                revision-id concept-id provider-id deleted]} collection]
    {:meta {:concept-type "collection"
            :concept-id concept-id
            :revision-id revision-id
            :native-id entry-title
            :user-id user-id
            :provider-id provider-id
            :format (mt/format->mime-type format-key)
            :deleted (boolean deleted)}
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defn- collections->umm-jsons
  "Returns the collections in a set of umm-jsons."
  [collections]
  (set (map collection->umm-json collections)))

(defn- umm-jsons-match?
  "Returns true if the UMM collection umm-jsons match the umm-jsons returned from the search."
  [collections search-result]
  ;; We do not check the revision-date in umm-json as it is not available in UMM record.
  ;; We also don't check hits and tooks in the UMMJSON.
  (is (= (collections->umm-jsons collections)
         (set (map #(incubator/dissoc-in % [:meta :revision-date]) (get-in search-result [:results  :items]))))))

(deftest search-collection-umm-json
  (let [coll1-1 (d/ingest "PROV1" (dc/collection {:entry-title "et1"
                                                  :entry-id "s1_v1"
                                                  :version-id "v1"
                                                  :short-name "s1"})
                          {:user-id "user1"})
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll1-1)}
        coll1-2-tombstone (merge coll1-1
                                 {:deleted true :user-id "user2"}
                                 (ingest/delete-concept concept1 {:user-id "user2"}))
        coll1-3 (d/ingest "PROV1" (dc/collection {:entry-title "et1"
                                                  :entry-id "s1_v2"
                                                  :version-id "v2"
                                                  :short-name "s1"}))

        coll2-1 (d/ingest "PROV1" (dc/collection {:entry-title "et2"
                                                  :entry-id "s2_v1"
                                                  :version-id "v1"
                                                  :short-name "s2"}))
        coll2-2 (d/ingest "PROV1" (dc/collection {:entry-title "et2"
                                                  :entry-id "s2_v2"
                                                  :version-id "v2"
                                                  :short-name "s2"}))
        concept2 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll2-2)}
        coll2-3-tombstone (merge coll2-2 {:deleted true} (ingest/delete-concept concept2))

        coll3 (d/ingest "PROV2" (dc/collection {:entry-title "et3"
                                                :entry-id "s1_v4"
                                                :version-id "v4"
                                                :short-name "s1"}) {:user-id "user3"})]
    (index/wait-until-indexed)
    (testing "find collections in umm-json format"
      (are2 [collections params]
            (umm-jsons-match? collections (search/find-concepts-umm-json :collection params))

            ;; Should not get matching tombstone for second collection back
            "provider-id all-revisions=false"
            [coll1-3]
            {:provider-id "PROV1" :all-revisions false}

            "provider-id all-revisions unspecified"
            [coll1-3]
            {:provider-id "PROV1"}

            "provider-id all-revisions=true"
            [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone]
            {:provider-id "PROV1" :all-revisions true}

            "native-id all-revisions=false"
            [coll1-3]
            {:native-id "et1" :all-revisions false}

            "native-id all-revisions unspecified"
            [coll1-3]
            {:native-id "et1"}

            "native-id all-revisions=true"
            [coll1-1 coll1-2-tombstone coll1-3]
            {:native-id "et1" :all-revisions true}

            "version all-revisions=false"
            [coll1-3]
            {:version "v2" :all-revisions false}

            "version all-revisions unspecified"
            [coll1-3]
            {:version "v2"}

            "version all-revisions=true"
            [coll1-3 coll2-2 coll2-3-tombstone]
            {:version "v2" :all-revisions true}

            ;; verify that "finding latest", i.e., all-revisions=false, does not return old revisions
            "version all-revisions=false - no match to latest"
            []
            {:version "v1" :all-revisions false}

            "short-name all-revisions false"
            [coll1-3 coll3]
            {:short-name "s1" :all-revisions false}

            ;; this test is across providers
            "short-name all-revisions unspecified"
            [coll1-3 coll3]
            {:short-name "s1"}

            "short-name all-revisions true"
            [coll1-1 coll1-2-tombstone coll1-3 coll3]
            {:short-name "s1" :all-revisions true}

            "concept-id all-revisions false"
            [coll1-3]
            {:concept-id "C1200000000-PROV1" :all-revisions false}

            "concept-id all-revisions unspecified"
            [coll1-3]
            {:concept-id "C1200000000-PROV1"}

            "concept-id all-revisions true"
            [coll1-1 coll1-2-tombstone coll1-3]
            {:concept-id "C1200000000-PROV1" :all-revisions true}

            "all-revisions true"
            [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone coll3]
            {:all-revisions true}))

    (testing "find collections in umm-json extension"
      (let [results (search/find-concepts-umm-json :collection {})
            extension-results (search/find-concepts-umm-json :collection {} {:url-extension "umm-json"})]
        (is (= (incubator/dissoc-in results [:results :took])
               (incubator/dissoc-in extension-results [:results :took])))))))

(deftest search-umm-json-error-cases
  (testing "granule umm-json search is not supported"
    (let [{:keys [status errors]} (search/find-concepts-umm-json :granule {})]
      (is (= [400 ["The mime type [application/umm+json] is not supported for granules."]]
             [status errors])))))

