(ns cmr.system-int-test.search.collection-concept-revision-search-test
  "Integration test for collection all revisions search"
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :refer [are2] :as util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(def test-context (lkt/setup-context-for-test))

(deftest search-collection-all-revisions
  (let [coll1-1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "et1"
                                                                              :Version "v1"
                                                                              :ShortName "s1"}))
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:EntryTitle coll1-1)}
        coll1-2-tombstone (merge (ingest/delete-concept concept1) concept1 {:deleted true})
        coll1-3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "et1"
                                                                              :Version "v2"
                                                                              :ShortName "s1"}))

        coll2-1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "et2"
                                                                              :Version "v1"
                                                                              :ShortName "s2"}))
        coll2-2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "et2"
                                                                              :Version "v2"
                                                                              :ShortName "s2"}))
        concept2 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:EntryTitle coll2-2)}
        coll2-3-tombstone (merge (ingest/delete-concept concept2) concept2 {:deleted true})

        coll3 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "et3"
                                                                            :Version "v4"
                                                                            :ShortName "s1"}))]
    (index/wait-until-indexed)
    (testing "find-references-with-all-revisions parameter"
      (are2 [collections params]
            (d/refs-match? collections (search/find-refs :collection params))

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
            {:concept-id "C1200000009-PROV1" :all-revisions false}

            "concept-id all-revisions unspecified"
            [coll1-3]
            {:concept-id "C1200000009-PROV1"}

            "concept-id all-revisions true"
            [coll1-1 coll1-2-tombstone coll1-3]
            {:concept-id "C1200000009-PROV1" :all-revisions true}

            "all-revisions true"
            [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone coll3]
            {:all-revisions true}))))

(deftest search-umm-json-tombstones
  (let [coll4-umm expected-conversion/example-collection-record
        mime-type "application/vnd.nasa.cmr.umm+json;version=1.0"
        json (umm-spec/generate-metadata test-context coll4-umm mime-type)
        coll (d/ingest-concept-with-metadata {:provider-id "PROV1"
                                              :concept-type :collection
                                              :format mime-type
                                              :native-id "coll"
                                              :metadata json})
        tombstone (ingest/delete-concept {:provider-id "PROV1"
                                          :concept-type :collection
                                          :native-id "coll"})
        tombstone (assoc tombstone :deleted true)]
    (index/wait-until-indexed)
    (is (= (set (for [{:keys [concept-id revision-id]} [coll tombstone]]
                  {:id concept-id
                   :revision-id revision-id}))
           (set (for [ref (:refs (search/find-refs :collection {:all-revisions true}))]
                  (select-keys ref [:id :revision-id])))))))

(deftest search-all-revisions-error-cases
  (testing "collection search with all_revisions bad value"
    (let [{:keys [status errors]} (search/find-refs :collection {:all-revisions "foo"})]
      (is (= [400 ["Parameter all_revisions must take value of true, false, or unset, but was [foo]"]]
             [status errors]))))
  (testing "granule search with all_revisions parameter is not supported"
    (let [{:keys [status errors]} (search/find-refs :granule {:provider-id "PROV1"
                                                              :all-revisions false})]
      (is (= [400 ["Parameter [all_revisions] was not recognized."]]
             [status errors]))))
  (testing "granule search with all_revisions bad value"
    (let [{:keys [status errors]} (search/find-refs :granule {:provider-id "PROV1"
                                                              :all-revisions "foo"})]
      (is (= [400 ["Parameter [all_revisions] was not recognized."
                   "Parameter all_revisions must take value of true, false, or unset, but was [foo]"]]
             [status errors]))))
  (testing "unsupported format for all_revisions search"
    (testing "formats return common json response"
      (are [search-format]
           (let [mime-type (mt/format->mime-type search-format)
                 {:keys [status errors]} (search/get-search-failure-data
                                           (search/find-concepts-in-format mime-type :collection {:all-revisions true}))]
             (= [400 [(format "The mime type [%s] is not supported when all_revisions = true." mime-type)]]
                [status errors]))

           :json
           :opendata))

    (testing "formats return common xml response"
      (are [search-format]
           (let [mime-type (mt/format->mime-type search-format)
                 {:keys [status errors]} (search/get-search-failure-xml-data
                                           (search/find-concepts-in-format mime-type :collection {:all-revisions true}))]
             (= [400 [(format "The mime type [%s] is not supported when all_revisions = true." mime-type)]]
                [status errors]))

           :echo10
           :iso19115
           :dif
           :dif10
           :atom
           :kml
           :native))

    ;; iso-smap and csv are in their own tests since they return different error messages
    ;; because they are not supported for collection searches.
    (testing "iso-smap"
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                      (search/find-concepts-in-format mt/iso-smap :collection {:all-revisions true}))]
        (is (= [400 ["The mime types specified in the accept header [application/iso:smap+xml] are not supported."]]
               [status errors]))))
    (testing "csv"
      (let [{:keys [status errors]} (search/get-search-failure-data
                                      (search/find-concepts-in-format mt/csv :collection {:all-revisions true}))]
        (is (= [400 ["The mime type [text/csv] is not supported when all_revisions = true."]]
               [status errors]))))))
