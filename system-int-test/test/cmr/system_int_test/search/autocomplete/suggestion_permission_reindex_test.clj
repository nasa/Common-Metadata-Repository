(ns cmr.system-int-test.search.autocomplete.suggestion-permission-reindex-test
  "Tests that autocomplete suggestions maintain correct public visibility during reindexing."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-spec]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as transmit-config]))

(defn extract-autocomplete-values
  [response]
  (->> (get-in response [:feed :entry])
       (map :value)
       set))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"}
                                            {:grant-all-search? false})]))

(deftest reindex-preserves-public-project-suggestions-test
  (testing "Reindexing with public then private collections preserves public visibility"
    (let [_ (d/ingest-umm-spec-collection
            "PROV1"
            (data-umm-spec/collection
             {:EntryTitle "Public Collection"
              :ShortName "PUBLIC-1"
              :Version "V1"
              :Projects [(data-umm-spec/project "TESTPROJ" "Test Project")]})
            {:format :umm-json
             :validate-keywords false})

          _ (e/grant-guest (s/context)
                          (e/coll-catalog-item-id "PROV1" (e/coll-id ["Public Collection"])))]

      (index/wait-until-indexed)
      (ingest/reindex-collection-permitted-groups transmit-config/mock-echo-system-token)
      (index/wait-until-indexed)
      (index/reindex-suggestions)
      (index/wait-until-indexed)
      (search/clear-caches)

      (testing "TESTPROJ visible for logged-out users after initial indexing"
        (let [results (search/get-autocomplete-json "q=TESTPROJ&type[]=project")]
          (is (contains? (extract-autocomplete-values results) "TESTPROJ"))))

      (let [_ (d/ingest-umm-spec-collection
              "PROV1"
              (data-umm-spec/collection
               {:EntryTitle "Restricted Collection"
                :ShortName "RESTRICTED-1"
                :Version "V2"
                :Projects [(data-umm-spec/project "TESTPROJ" "Test Project")]})
              {:format :umm-json
               :validate-keywords false})]

        (index/wait-until-indexed)
        (ingest/reindex-collection-permitted-groups transmit-config/mock-echo-system-token)
        (index/wait-until-indexed)
        (index/reindex-suggestions)
        (index/wait-until-indexed)
        (search/clear-caches)

        (testing "TESTPROJ remains visible after reindexing with private collection"
          (let [results (search/get-autocomplete-json "q=TESTPROJ&type[]=project")]
            (is (contains? (extract-autocomplete-values results) "TESTPROJ"))))))))

(deftest reindex-preserves-public-science-keywords-test
  (testing "Reindexing with public then private collections preserves science keyword visibility"
    (let [test-science-keyword {:Category "EARTH SCIENCE"
                                :Topic "ATMOSPHERE"
                                :Term "CLOUDS"}
          _ (d/ingest-umm-spec-collection
             "PROV1"
             (data-umm-spec/collection
              {:EntryTitle "Public Science Collection"
               :ShortName "PUBLIC-SCI-1"
               :Version "V1"
               :ScienceKeywords [test-science-keyword]})
             {:format :umm-json
              :validate-keywords false})

          _ (e/grant-guest (s/context)
                           (e/coll-catalog-item-id "PROV1" (e/coll-id ["Public Science Collection"])))]

      (index/wait-until-indexed)
      (ingest/reindex-collection-permitted-groups transmit-config/mock-echo-system-token)
      (index/wait-until-indexed)
      (index/reindex-suggestions)
      (index/wait-until-indexed)
      (search/clear-caches)

      (testing "Science keyword visible for logged-out users after initial indexing"
        (let [results (search/get-autocomplete-json "q=CLOUDS&type[]=science_keywords")]
          (is (contains? (extract-autocomplete-values results) "CLOUDS"))))

      (let [_ (d/ingest-umm-spec-collection
               "PROV1"
               (data-umm-spec/collection
                {:EntryTitle "Restricted Science Collection"
                 :ShortName "RESTRICTED-SCI-1"
                 :Version "V2"
                 :ScienceKeywords [test-science-keyword]})
               {:format :umm-json
                :validate-keywords false})]

        (index/wait-until-indexed)
        (ingest/reindex-collection-permitted-groups transmit-config/mock-echo-system-token)
        (index/wait-until-indexed)
        (index/reindex-suggestions)
        (index/wait-until-indexed)
        (search/clear-caches)

        (testing "Science keyword remains visible after reindexing with private collection"
          (let [results (search/get-autocomplete-json "q=CLOUDS&type[]=science_keywords")]
            (is (contains? (extract-autocomplete-values results) "CLOUDS"))))))))
