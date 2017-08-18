(ns cmr.system-int-test.search.collection-spatial-keyword-search-test
  "Integration test for CMR collection search by spatial keyword terms"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-spatial-keywords
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV1" (dc/collection {:spatial-keywords []}))
        coll3 (d/ingest "PROV1" (dc/collection {:spatial-keywords ["DC"]}))

        coll4 (d/ingest "PROV2" (dc/collection {:spatial-keywords ["DC" "LA"]}))
        coll5 (d/ingest "PROV2" (dc/collection {:spatial-keywords ["Detroit"]}))
        coll6 (d/ingest "PROV2" (dc/collection {:spatial-keywords ["LL"]}))
        coll7 (d/ingest "PROV2" (dc/collection {:spatial-keywords ["detroit"]}))
        coll-angola (d/ingest "PROV2" (dc/collection {:spatial-keywords ["ANGOLA"]}))
        coll-c-africa (d/ingest "PROV2" (dc/collection {:spatial-keywords ["central africa"]}))
        coll-gaza (d/ingest "PROV2" (dc/collection {:spatial-keywords ["Gaza Strip"]}))
        coll-iso-sample (d/ingest-concept-with-metadata-file "iso-samples/cmr-4192-iso-collection.xml"
                                                             {:provider-id "PROV2"
                                                              :concept-type :collection
                                                              :format-key :iso19115})]

    (index/wait-until-indexed)

    (testing "search by spatial keywords."
      (are [spatial-keyword items] (d/refs-match? items (search/find-refs :collection {:spatial-keyword spatial-keyword}))
           "DC" [coll3 coll4]
           "LL" [coll6]
           "LA" [coll4]
           ["Detroit"] [coll5 coll7]
           ["LL" "Detroit"] [coll5 coll6 coll7]
           "Gaza Strip" [coll-gaza]
           "Tuolumne River Basin" [coll-iso-sample]
           "North America" [coll-iso-sample]
           "Continent" [coll-iso-sample coll-angola coll-c-africa coll-gaza]))
    (testing "search by spatial keywords using wildcard * for cmr-4192 collection"
      (is (d/refs-match? [coll-iso-sample]
                         (search/find-refs :collection
                                           {:spatial-keyword "*river*"
                                            "options[spatial-keyword][pattern]" "true"})))
      (is (d/refs-match? [coll-iso-sample]
                         (search/find-refs :collection
                                           {:spatial-keyword "nOrTh aMEricA"}))))
    (testing "search by spatial keywords using wildcard *."
      (is (d/refs-match? [coll3 coll4 coll5 coll7]
                         (search/find-refs :collection
                                           {:spatial-keyword "D*"
                                            "options[spatial-keyword][pattern]" "true"}))))
    (testing "search by spatial keywords using wildcard ?."
      (is (d/refs-match? [coll4 coll6]
                         (search/find-refs :collection
                                           {:spatial-keyword "L?"
                                            "options[spatial-keyword][pattern]" "true"}))))
    (testing "search by spatial keywords case default is ignore case true."
      (is (d/refs-match? [coll5 coll7]
                         (search/find-refs :collection
                                           {:spatial-keyword "detroit"}))))
    (testing "search by spatial keywords ignore case false."
      (is (d/refs-match? [coll7]
                         (search/find-refs :collection
                                           {:spatial-keyword "detroit"
                                            "options[spatial-keyword][ignore-case]" "false"}))))
    (testing "search by spatial keywords ignore case true."
      (is (d/refs-match? [coll5 coll7]
                         (search/find-refs :collection
                                           {:spatial-keyword "detroit"
                                            "options[spatial-keyword][ignore-case]" "true"}))))
    (testing "search by spatial keywords, options :and."
      (is (d/refs-match? [coll4]
                         (search/find-refs :collection {"spatial-keyword[]" ["DC" "LA"]
                                                        "options[spatial-keyword][and]" "true"}))))

    (testing "search collections by spatial keywords with AQL."
      (are [items keywords options]
           (let [condition (merge {:spatialKeywords keywords} options)]
             (d/refs-match? items
                            (search/find-refs-with-aql :collection [condition])))

           [coll3 coll4] "DC" {}
           [coll6] "LL" {}
           [coll4] "LA" {}
           [coll5] ["Detroit"] {}
           [coll5 coll6] ["LL" "Detroit"] {}
           [] "BLAH" {}
           ;; pattern
           [coll3 coll4 coll5] "D%" {:pattern true}
           [coll4 coll6] "L_" {:pattern true}
           ;;ignore case
           [coll7] "detroit" {}
           [coll5 coll7] "detroit" {:ignore-case true}
           [coll7] "detroit" {:ignore-case false}))

    (testing "Search collections by spatial keywords using JSON Query."
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll3 coll4] {:spatial_keyword "DC"}
           [coll6] {:spatial_keyword "LL"}
           [coll4] {:spatial_keyword "LA"}
           [coll5 coll7] {:and [{:spatial_keyword "Detroit"}]}
           [coll5 coll6 coll7] {:or [{:spatial_keyword "LL"} {:spatial_keyword "Detroit"}]}
           [] {:and [{:spatial_keyword "LL"} {:spatial_keyword "Detroit"}]}
           [coll1 coll2 coll5 coll6 coll7 coll-angola coll-c-africa coll-gaza coll-iso-sample] {:not {:spatial_keyword "DC"}}
           [] {:spatial_keyword "BLAH"}

           ;; pattern
           [coll3 coll4 coll5 coll7] {:spatial_keyword {:value "D*" :pattern true}}
           [coll4 coll6] {:spatial_keyword {:value "L?" :pattern true}}
           ;;ignore case
           [coll5 coll7] {:spatial_keyword {:value "detroit"}}
           [coll5 coll7] {:spatial_keyword {:value "detroit" :ignore_case true}}
           [coll7] {:spatial_keyword {:value "detroit" :ignore_case false}}))

    (testing "Search collections by location keywords using JSON Query."
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll-angola coll-c-africa] {:location_keyword {:subregion_1 "CeNTRAL aFRiCA"}}
           [coll-gaza coll-angola coll-c-africa coll-iso-sample] {:location_keyword {:any "Cont*" :pattern true}}
           [coll-gaza] {:location_keyword {:category "CONTINENT"
                                           :type "ASIA"
                                           :subregion_1 "WESTERN ASIA"
                                           :subregion_2 "MIDDLE EAST"
                                           :subregion_3 "GAZA STRIP"
                                           :uuid "302ab5f2-5fa2-482d-9d22-8a7a1546a62d"}}))))
