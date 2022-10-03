(ns cmr.system-int-test.search.search-after-search-test
  "Tests for using search-after header to retrieve search results"
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.api.routes :as routes]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.data2.granule :as data2-granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- scroll-all-umm-json-with-search-after
  "Scroll all results in umm-json with search-after and return
   number of scrolls and all concepts harvested."
  [concept-type params initial-scroll]
  (loop [current-scroll initial-scroll
         all-concepts []
         num-scrolls 0]
    (let [current-concepts (get-in current-scroll [:results :items])
          search-after (:search-after current-scroll)]
      (if-not (empty? current-concepts)
        (recur (search/find-concepts-umm-json
                concept-type
                params
                {:headers {routes/SEARCH_AFTER_HEADER search-after}})
               (concat all-concepts current-concepts)
               (inc num-scrolls))
        [num-scrolls all-concepts]))))

(deftest search-after-general-test
  (let [colls (doall
               (for [idx (range 5)]
                 (data2-core/ingest-umm-spec-collection
                  "PROV1"
                  (data-umm-c/collection idx {}))))
        grans (doall
               (for [coll colls]
                 (data2-core/ingest
                  "PROV1"
                  (data2-granule/granule-with-umm-spec-collection
                   coll
                   (:concept-id coll)))))]
    (index/wait-until-indexed)

    (testing "UMM-JSON search-after"
      (are3 [concept-type accept extension all-refs]
        (let [params {:page-size 1
                      :provider "PROV1"}
              response (search/find-concepts-umm-json
                        concept-type
                        params
                        {:url-extension extension
                         :accept accept})
              [num-scrolls all-concepts] (scroll-all-umm-json-with-search-after
                                          concept-type params response)]
          (is (not (nil? (:search-after response))))
          (is (= (count all-refs) num-scrolls))
          (is (= (set (map :concept-id all-refs))
                 (set (map #(get-in % [:meta :concept-id]) all-concepts)))))

        "Collection UMM-JSON via extension"
        :collection nil "umm_json" colls

        "Collection UMM-JSON via accept"
        :collection mime-types/umm-json nil colls

        "Granule UMM-JSON via extension"
        :granule nil "umm_json" grans

        "Granule UMM-JSON via accept"
        :granule mime-types/umm-json nil grans))

    (testing "search after with JSON query"
      (let [[coll1 coll2 coll3 coll4 coll5] colls
            query {:provider "PROV1"}
            {:keys [hits search-after] :as result} (search/find-refs-with-json-query
                                                    :collection {:page-size 2} query)]
        (testing "First call returns search-after and hits count with page-size results"
          (is (= (count colls) hits))
          (is (not (nil? search-after)))
          (is (data2-core/refs-match? [coll1 coll2] result)))

        (testing "Subsequent searches gets the next page of results and search-after"
          (let [result (search/find-refs-with-json-query
                        :collection
                        {:page-size 2}
                        query
                        {:headers {routes/SEARCH_AFTER_HEADER search-after}})
                search-after-1 (:search-after result)]
            (is (= (count colls) hits))
            (is (not= search-after search-after-1))
            (is (data2-core/refs-match? [coll3 coll4] result))

            (testing "Remaining results returned on last search and search-after"
              (let [result (search/find-refs-with-json-query
                            :collection
                            {:page-size 2}
                            query
                            {:headers {routes/SEARCH_AFTER_HEADER search-after-1}})
                    search-after-2 (:search-after result)]
                (is (= (count colls) (:hits result)))
                (is (not= search-after search-after-1 search-after-2))
                (is (data2-core/refs-match? [coll5] result))

                (testing "Search beyond total hits returns empty list and no search-after"
                  (let [result (search/find-refs-with-json-query
                                :collection
                                {:page-size 2}
                                query
                                {:headers {routes/SEARCH_AFTER_HEADER search-after-2}})
                        search-after-3 (:search-after result)]
                    (is (= (count colls) (:hits result)))
                    (is (nil? search-after-3))
                    (is (data2-core/refs-match? [] result))))))))))))

(deftest granule-search-after
  (let [admin-read-group-concept-id (e/get-or-create-group (s/context) "admin-read-group")
        admin-read-token (e/login (s/context) "admin" [admin-read-group-concept-id])
        _ (e/grant-group-admin (s/context) admin-read-group-concept-id :read)
        coll1 (data2-core/ingest-umm-spec-collection "PROV1"
                                                     (data-umm-c/collection {:EntryTitle "E1"
                                                                             :ShortName "S1"
                                                                             :Version "V1"}))
        coll2 (data2-core/ingest-umm-spec-collection "PROV1"
                                                     (data-umm-c/collection {:EntryTitle "E2"
                                                                             :ShortName "S2"
                                                                             :Version "V2"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1 (data2-core/ingest "PROV1"
                                 (data2-granule/granule-with-umm-spec-collection
                                  coll1
                                  coll1-cid
                                  {:granule-ur "Granule1"}))
        gran2 (data2-core/ingest "PROV1"
                                 (data2-granule/granule-with-umm-spec-collection
                                  coll1
                                  coll1-cid
                                  {:granule-ur "Granule2"}))
        gran3 (data2-core/ingest "PROV1"
                                 (data2-granule/granule-with-umm-spec-collection
                                  coll1
                                  coll1-cid
                                  {:granule-ur "Granule3"}))
        gran4 (data2-core/ingest "PROV1"
                                 (data2-granule/granule-with-umm-spec-collection
                                  coll2
                                  coll2-cid
                                  {:granule-ur "Granule4"}))
        gran5 (data2-core/ingest "PROV1"
                                 (data2-granule/granule-with-umm-spec-collection
                                  coll2
                                  coll2-cid
                                  {:granule-ur "Granule5"}))
        all-grans [gran1 gran2 gran3 gran4 gran5]]
    (index/wait-until-indexed)

    (testing "search after with page size"
      (let [params {:provider "PROV1" :page-size 2}
            {:keys [hits search-after] :as result} (search/find-refs :granule params)]
        (testing "First call returns search-after and hits count with page-size results"
          (is (= (count all-grans) hits))
          (is (not (nil? search-after)))
          (is (data2-core/refs-match? [gran1 gran2] result)))

        (testing "search with invalid search-after value returns 400 error"
          (let [{:keys [status errors]} (search/find-refs :granule
                                                          params
                                                          {:headers {routes/SEARCH_AFTER_HEADER "[0, \"xxx\"]"}})]
            (is (= 400 status))
            (is (= ["The search failed with error: [{:type \"illegal_argument_exception\", :reason \"search_after has 2 value(s) but sort has 3.\"}]. Please double check your search_after header."]
                   errors))))

        (testing "Subsequent searches gets the next page of results"
          (let [result (search/find-refs :granule
                                         params
                                         {:headers {routes/SEARCH_AFTER_HEADER search-after}})
                search-after-1 (:search-after result)]
            (is (= (count all-grans) hits))
            (is (not= search-after search-after-1))
            (is (data2-core/refs-match? [gran3 gran4] result))

            (testing "Remaining results returned on last search"
              (let [result (search/find-refs :granule
                                             params
                                             {:headers {routes/SEARCH_AFTER_HEADER search-after-1}})
                    search-after-2 (:search-after result)]
                (is (= (count all-grans) (:hits result)))
                (is (not= search-after search-after-1 search-after-2))
                (is (data2-core/refs-match? [gran5] result))

                (testing "Searches beyond total hits return empty list"
                  (let [result (search/find-refs :granule
                                                 params
                                                 {:headers {routes/SEARCH_AFTER_HEADER search-after-2}})
                        search-after-3 (:search-after result)]
                    (is (= (count all-grans) (:hits result)))
                    (is (nil? search-after-3))
                    (is (data2-core/refs-match? [] result))))))))))))

(deftest search-after-invalid-parameters
  (testing "invalid parameters"
    (are3 [query expected-status err-msg]
      (let [{:keys [status errors]} (search/find-refs :granule
                                                      query
                                                      {:allow-failure? true
                                                       :headers {routes/SEARCH_AFTER_HEADER "[0]"}})]
        (is (= expected-status status))
        (is (= [err-msg] errors)))

      "Search After queries cannot be all-granule queries"
      {}
      400
      (str "The CMR does not allow querying across granules in all collections when using search-after."
           " You should limit your query using conditions that identify one or more collections "
           "such as provider, concept_id, short_name, or entry_title.")

      "page_num is not allowed with search-after"
      {:provider "PROV1" :page-num 2}
      400
      "page_num is not allowed with search-after"

      "offset is not allowed with search-after"
      {:provider "PROV1" :offset 2}
      400
      "offset is not allowed with search-after"

      "scroll is not allowed with search-after"
      {:scroll true}
      400
      "scroll is not allowed with search-after"))

  (testing "invalid search-after header value"
    (are3 [value err-msg]
          (let [{:keys [status errors]} (search/find-refs :granule
                                                          {:provider "PROV1"}
                                                          {:allow-failure? true
                                                           :headers {routes/SEARCH_AFTER_HEADER value}})]
            (is (= 400 status))
            (is (= [err-msg] errors)))

      "invaid search-after value, string"
      12345
      "search-after header value is invalid, must be in the form of a JSON array."

      "invaid search-after value, string with quotes"
      "\"abc \""
      "The search failed with error: [{:type \"parsing_exception\", :reason \"Unknown key for a VALUE_STRING in [search_after].\", :line 1, :col 17}]. Please double check your search_after header."

      "invaid search-after value, missing comma"
      "[0 \"abc\"]"
      "search-after header value is invalid, must be in the form of a JSON array.")))