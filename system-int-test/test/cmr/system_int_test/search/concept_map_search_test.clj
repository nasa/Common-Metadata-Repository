(ns cmr.system-int-test.search.concept-map-search-test
  "Integration test for concept map format search"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :refer [are2]]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- collection->concept-map
  "Returns the collection in concept-map format."
  [collection]
  (let [{{:keys [short-name version-id]} :product
         {:keys [delete-time]} :data-provider-timestamps
         :keys [entry-id entry-title format-key revision-id concept-id provider-id deleted]} collection]
    {:concept-type "collection"
     :concept-id concept-id
     :revision-id revision-id
     :native-id entry-title
     :provider-id provider-id
     :entry-title entry-title
     :entry-id entry-id
     :short-name short-name
     :version-id version-id
     :deleted (boolean deleted)
     :format (mt/format->mime-type format-key)}))


(defn- collections->concept-maps
  "Returns the collections in a set of concept-maps."
  [collections]
  (set (map collection->concept-map collections)))

(defn- concept-maps-match?
  "Returns true if the UMM collection concept-maps match the concept-maps returned from the search."
  [collections search-result]
  ;; We do not check the revision-date in concept-map as it is not available in UMM record.
  (is (= (collections->concept-maps collections)
         (set (map #(dissoc % :revision-date) (:results search-result))))))

(deftest search-collection-concept-map
  (let [coll1-1 (d/ingest "PROV1" (dc/collection {:entry-title "et1"
                                                  :entry-id "s1_v1"
                                                  :version-id "v1"
                                                  :short-name "s1"}))
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll1-1)}
        coll1-2-tombstone (merge coll1-1 {:deleted true} (ingest/delete-concept concept1))
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
                                                :short-name "s1"}))]
    (index/wait-until-indexed)
    (testing "find collections in concept-map format"
      (are2 [collections params]
            (concept-maps-match? collections (search/find-concepts-concept-map :collection params))

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

    (testing "find collections in concept-map extension"
      (is (= (search/find-concepts-concept-map :collection {})
             (search/find-concepts-concept-map :collection {} {:url-extension "concept-map"}))))))

(deftest search-concept-map-error-cases
  (testing "granule concept-map search is not supported"
    (let [{:keys [status errors]} (search/find-concepts-concept-map :granule {})]
      (is (= [400 ["The mime type [application/concept-map+json] is not supported for granules."]]
             [status errors])))))

