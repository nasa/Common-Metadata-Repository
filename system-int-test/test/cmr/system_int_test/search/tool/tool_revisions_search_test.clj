(ns cmr.system-int-test.search.tool.tool-revisions-search-test
  "Integration test for search all revisions search"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tool-util :as tool]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                tool/grant-all-tool-fixture]))

(deftest search-tool-all-revisions-after-cleanup
  (let [tool1 {:native-id "TL1"
               :Name "Tool1"
               :provider-id "PROV1"}
        tool2 {:native-id "TL2"
               :Name "Tool2"
               :provider-id "PROV2"}
        tool1s (doall (for [n (range 12)]
                        (tool/ingest-tool (tool/make-tool-concept tool1))))
        tool2s (doall (for [n (range 10)]
                        (tool/ingest-tool (tool/make-tool-concept tool2))))
        all-tools-after-cleanup (concat (drop 2 tool1s) tool2s)]
    (index/wait-until-indexed)

    (is (= 204 (:status (mdb/cleanup-old-revisions))))
    (index/wait-until-indexed)

    (du/assert-tool-umm-jsons-match
      umm-version/current-tool-version
      all-tools-after-cleanup
      (search/find-concepts-umm-json :tool {:all-revisions true
                                            :page-size 20}))

    (d/assert-refs-match
      all-tools-after-cleanup
      (search/find-refs :tool {:all-revisions true
                               :page-size 20}))))

(deftest search-tool-all-revisions
  (let [token (e/login (s/context) "user1")
        tl1-concept (tool/make-tool-concept {:native-id "TL1"
                                             :Name "Tool1"
                                             :provider-id "PROV1"})
        tl2-concept (tool/make-tool-concept {:native-id "TL2"
                                             :Name "Tool2"
                                             :provider-id "PROV1"})
        tl2-2-concept (tool/make-tool-concept {:native-id "TL2"
                                               :Name "Tool2-2"
                                               :provider-id "PROV1"})
        tl3-concept (tool/make-tool-concept {:native-id "T3"
                                             :Name "Tool1"
                                             :provider-id "PROV2"})
        tl1-1 (tool/ingest-tool tl1-concept)
        tl1-2-tombstone (merge (ingest/delete-concept
                                 tl1-concept (tool/token-opts token))
                                tl1-concept
                                {:deleted true
                                 :user-id "user1"})
        tl1-3 (tool/ingest-tool tl1-concept)
        tl2-1 (tool/ingest-tool tl2-concept)
        tl2-2 (tool/ingest-tool tl2-2-concept)
        tl2-3-tombstone (merge (ingest/delete-concept
                                 tl2-2-concept (tool/token-opts token))
                                tl2-2-concept
                                {:deleted true
                                 :user-id "user1"})
        tl3 (tool/ingest-tool tl3-concept)]
    (index/wait-until-indexed)
    (testing "search tools for all revisions"
      (are3 [tools params]
        (do
          ;; find references with all revisions
          (d/assert-refs-match
            tools (tool/search-refs params))
          ;; search in JSON with all-revisions
          (tool/assert-tool-search tools (tool/search-json params))
          ;; search in UMM JSON with all-revisions
          (du/assert-tool-umm-jsons-match
           umm-version/current-tool-version
           tools
           (search/find-concepts-umm-json :tool params)))

        "provider-id all-revisions=false"
        [tl1-3]
        {:provider-id "PROV1" :all-revisions false}

        "provider-id all-revisions unspecified"
        [tl1-3]
        {:provider-id "PROV1"}

        "provider-id all-revisions=true"
        [tl1-1 tl1-2-tombstone tl1-3 tl2-1 tl2-2 tl2-3-tombstone]
        {:provider-id "PROV1" :all-revisions true}

        "native-id all-revisions=false"
        [tl1-3]
        {:native-id "tl1" :all-revisions false}

        "native-id all-revisions unspecified"
        [tl1-3]
        {:native-id "tl1"}

        "native-id all-revisions=true"
        [tl1-1 tl1-2-tombstone tl1-3]
        {:native-id "tl1" :all-revisions true}

        ;; this test is across providers
        "name all-revisions false"
        [tl1-3 tl3]
        {:name "Tool1" :all-revisions false}

        ;; this test is across providers
        "name all-revisions unspecified"
        [tl1-3 tl3]
        {:name "Tool1"}

        "name all-revisions true"
        [tl1-1 tl1-2-tombstone tl1-3 tl3]
        {:name "Tool1" :all-revisions true}

        "name is updated on revision -- not found without all-revisions true"
        []
        {:name "Tool2"}

        "name is updated on revision -- found with all-revisions true"
        [tl2-1]
        {:name "Tool2" :all-revisions true}

        "all-revisions true"
        [tl1-1 tl1-2-tombstone tl1-3 tl2-1 tl2-2 tl2-3-tombstone tl3]
        {:all-revisions true}))))

(deftest search-all-revisions-error-cases
  (testing "tool search with all_revisions bad value"
    (let [{:keys [status errors]} (search/find-refs :tool {:all-revisions "foo"})]
      (is (= [400 ["Parameter all_revisions must take value of true, false, or unset, but was [foo]"]]
             [status errors])))))
