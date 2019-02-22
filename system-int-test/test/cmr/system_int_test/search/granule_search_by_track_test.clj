(ns cmr.system-int-test.search.granule-search-by-track-test
  "Search CMR granules by track info, i.e. cycle, pass, tile."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as util :refer [are3]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- ingest-granule-with-track
  "ingest a generated granule on the given provider, parent collection and track info"
  [provider-id coll track]
  (d/ingest provider-id (dg/granule-with-umm-spec-collection
                         coll (:concept-id coll)
                         {:spatial-coverage (dg/spatial-with-track track)})
            {:format :umm-json}))

(deftest search-by-track
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection
                        {:SpatialExtent (data-umm-cmn/spatial {:gsr "GEODETIC"})
                         :EntryTitle "E1"
                         :ShortName "S1"}))
        coll2 (d/ingest-umm-spec-collection
               "PROV2" (data-umm-c/collection
                        {:SpatialExtent (data-umm-cmn/spatial {:gsr "GEODETIC"})
                         :EntryTitle "E2"
                         :ShortName "S2"}))
        coll1-concept-id (:concept-id coll1)
        coll2-concept-id (:concept-id coll2)
        gran1 (ingest-granule-with-track "PROV1" coll1
                                         {:cycle 1
                                          :passes [{:pass 1}]})
        gran2 (ingest-granule-with-track "PROV1" coll1
                                         {:cycle 2
                                          :passes [{:pass 1}]})
        gran3 (ingest-granule-with-track "PROV1" coll1
                                         {:cycle 1
                                          :passes [{:pass 2}]})
        ;; granules for testing multiple passes and tiles
        gran4 (ingest-granule-with-track "PROV2" coll2
                                         {:cycle 3
                                          :passes [{:pass 1 :tiles ["1L"]}]})
        gran5 (ingest-granule-with-track "PROV2" coll2
                                         {:cycle 3
                                          :passes [{:pass 1 :tiles ["1R"]}
                                                   {:pass 2 :tiles ["1L"]}]})
        gran6 (ingest-granule-with-track "PROV2" coll2
                                         {:cycle 4
                                          :passes [{:pass 1 :tiles ["1L"]}]})
        ;; granules for testing Full track tiles and multiple tiles
        gran7 (ingest-granule-with-track "PROV2" coll2
                                         {:cycle 3
                                          :passes [{:pass 3 :tiles ["1L"]}
                                                   {:pass 4 :tiles ["1L"]}]})
        gran8 (ingest-granule-with-track "PROV2" coll2
                                         {:cycle 3
                                          :passes [{:pass 3 :tiles ["1R"]}
                                                   {:pass 4 :tiles ["2L"]}]})
        gran9 (ingest-granule-with-track "PROV2" coll2
                                         {:cycle 3
                                          :passes [{:pass 3 :tiles ["1F"]}
                                                   {:pass 4 :tiles ["2L"]}]})

        gran10 (ingest-granule-with-track "PROV2" coll2
                                          {:cycle 3
                                           :passes [{:pass 3 :tiles ["5F" "6R"]}
                                                    {:pass 4 :tiles ["5L" "6R"]}]})
        gran11 (ingest-granule-with-track "PROV2" coll2
                                          {:cycle 3
                                           :passes [{:pass 3 :tiles ["5R" "6L"]}
                                                    {:pass 4 :tiles ["5L" "6R" "8F"]}]})]
    (index/wait-until-indexed)

    (testing "search by cycles"
      (are3 [items params]
        (is (d/refs-match? items (search/find-refs :granule params)))

        "search with single cycle"
        [gran1 gran3] {:cycle 1}

        "search with list of cycle with a single value"
        [gran1 gran3] {:cycle [1]}

        "search with multiple cycles"
        [gran1 gran2 gran3] {:cycle [1 2]}

        "search with cycle, no match"
        [] {:cycle [12]}))

    (testing "search by cycle and passes"
      (are3 [items params options]
        (is (d/refs-match? items (search/find-refs :granule (merge params options))))

        "search with cycle and pass"
        [gran1]
        {:cycle 1
         :passes {:0 {:pass 1}}}
        {}

        "search with cycle and multiple passes, default is OR"
        [gran4 gran5]
        {:cycle 3
         :passes {:0 {:pass 1}
                  :1 {:pass 2}}}
        {}

        "search with cycle and multiple passes, options AND"
        [gran5]
        {:cycle 3
         :passes {:0 {:pass 1}
                  :1 {:pass 2}}}
        {"options[passes][AND]" "true"}

        "search with cycle and multiple passes, options AND false"
        [gran4 gran5]
        {:cycle 3
         :passes {:0 {:pass 1}
                  :1 {:pass 2}}}
        {"options[passes][AND]" "false"}))

    (testing "search by cycle, passes and tiles"
      (are3 [items params options]
        (is (d/refs-match? items (search/find-refs :granule (merge params options))))

        "search with cycle, pass and tile"
        [gran4]
        {:cycle 3
         :passes {:0 {:pass 1
                      :tiles "1L"}}}
        {}

        "search with cycle, multiple passes and tiles, default OR"
        [gran4]
        {:cycle 3
         :passes {:0 {:pass 1
                      :tiles "1L"}
                  :1 {:pass 1
                      :tiles "99R"}}}
        {}

        "search with cycle, multiple passes and tiles, options AND, not found"
        []
        {:cycle 3
         :passes {:0 {:pass 1
                      :tiles "1L"}
                  :1 {:pass 1
                      :tiles "99R"}}}
        {"options[passes][AND]" "true"}

        "search with cycle, multiple passes and tile (with Full track), search with half track"
        [gran7 gran9]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles "1L"}}}
        {}

        "search with cycle, multiple passes and tile (with Full track), search with Full track"
        [gran9]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles "1F"}}}
        {}

        "search with cycle, multiple passes and tile (with Full track), default OR"
        [gran7 gran9]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles "1L"}
                  :1 {:pass 4
                      :tiles "1L"}}}
        {}

        "search with cycle, multiple passes and tile (with Full track), options AND"
        [gran7]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles "1L"}
                  :1 {:pass 4
                      :tiles "1L"}}}
        {"options[passes][AND]" "true"}

        "search with cycle, multiple passes and multiple tiles, default OR"
        [gran10 gran11]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles ["5R" "6L"]}}}
        {}

        ;; This shows how to AND multiple tiles within a single pass together
        "search with cycle, multiple passes and multiple tiles AND together"
        [gran11]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles ["5R"]}
                  :1 {:pass 3
                      :tiles ["6L"]}}}
        {"options[passes][AND]" "true"}

        "search with cycle, multiple passes and tiles with comma separated values"
        [gran10 gran11]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles ["5R,6L"]}}}
        {}

        ;; Note: option AND this way will not acutally ANDed the tiles,
        ;; You will have to separate the tiles into separate passes then use option AND
        ;; See tests with multiple tiles AND together for examples.
        "search with cycle, multiple passes and tiles with comma separated values, options AND"
        [gran10 gran11]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles ["5R,6L"]}}}
        {"options[passes][AND]" "true"}

        "mixed tile formats"
        [gran10 gran11]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles ["5R"]}
                  :1 {:pass 4
                      :tiles ["4F,5L" "8F"]}
                  :2 {:pass 4
                      :tiles ["8R"]}}}
        {}

        "complicated example, default OR"
        [gran10 gran11]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles ["5R"]}
                  :1 {:pass 4
                      :tiles ["4F,5L" "8F"]}
                  :2 {:pass 4
                      :tiles ["8R"]}}}
        {}

        "complicated example, options AND"
        [gran11]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles ["5R"]}
                  :1 {:pass 4
                      :tiles ["4F,5L" "8F"]}
                  :2 {:pass 4
                      :tiles ["8R"]}}}
        {"options[passes][AND]" "true"}

        "complicated example, options AND, with simplified tiles value"
        [gran11]
        {:cycle 3
         :passes {:0 {:pass 3
                      :tiles ["5R"]}
                  :1 {:pass 4
                      :tiles ["5L"]}
                  :2 {:pass 4
                      :tiles ["8R"]}}}
        {"options[passes][AND]" "true"}))))
