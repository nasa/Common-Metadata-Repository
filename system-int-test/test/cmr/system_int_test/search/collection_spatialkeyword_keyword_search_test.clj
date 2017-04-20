(ns cmr.system-int-test.search.collection-spatialkeyword-keyword-search-test
  "This is what's left of collection_keyword_search_test that hasn't been converted to umm-spec.
   Please see comment in CMR-3895 which is ECSE-180"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are3]]
    [cmr.search.data.keywords-to-elastic :as k2e]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm-spec.models.umm-collection-models :as um]
    [cmr.umm-spec.models.umm-common-models :as cm]
    [cmr.umm-spec.test.expected-conversion :as exp-conv]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3" "provguid4" "PROV4" "provguid5" "PROV5"}))

(deftest search-by-keywords
  (let [short-name-boost (k2e/get-boost nil :short-name)
        entry-id-boost (k2e/get-boost nil :entry-id)
        project-boost (k2e/get-boost nil :project)
        platform-boost (k2e/get-boost nil :platform)
        instrument-boost (k2e/get-boost nil :instrument)
        concept-id-boost (k2e/get-boost nil :concept-id)
        provider-boost (k2e/get-boost nil :provider)
        entry-title-boost (k2e/get-boost nil :entry-title)
        two-d-boost (k2e/get-boost nil :two-d-coord-name)
        processing-level-boost (k2e/get-boost nil :processing-level-id)
        science-keywords-boost (k2e/get-boost nil :science-keywords)
        data-center-boost (k2e/get-boost nil :data-center)
        psa1 (dc/psa {:name "alpha" :data-type :string :value "ab"})
        psa2 (dc/psa {:name "bravo" :data-type :string :value "bf"})
        psa3 (dc/psa {:name "charlie" :data-type :string :value "foo"})
        psa4 (dc/psa {:name "case" :data-type :string :value "up"})
        psa5 (dc/psa {:name "novalue" :data-type :string :description "description"})
        p1 (dc/platform
            {:short-name "platform_SnB"
             :long-name "platform_Ln B"
             :instruments
             [(dc/instrument {:short-name "isnA" :long-name "ilnA" :technique "itechniqueA"
                              :sensors [(dc/sensor {:short-name "ssnA" :long-name "slnA"})
                                        (dc/sensor {:short-name "ssnD" :long-name "slnD"
                                                    :technique "techniqueD"})]})]})
        p2 (dc/platform
            {:short-name "platform_SnA spoonA"
             :long-name "platform_LnA"
             :characteristics 
               [(dc/characteristic {:name "char1" :description "char1desc"})
                (dc/characteristic {:name "char2" :description "char2desc"})]
             :instruments
             [(dc/instrument {:short-name "isnB" :long-name "ilnB" :technique "itechniqueB"
                              :sensors [(dc/sensor {:short-name "ssnB" :long-name "slnB"
                                                    :technique "techniqueB"})
                                        (dc/sensor {:short-name "ssnC" :long-name "slnC"
                                                    :technique "techniqueC"})]})]}) 
        p3 (dc/platform {:short-name "spoonA"})
        p4 (dc/platform {:short-name "SMAP"
                         :instruments [(dc/instrument {:short-name "SMAP L-BAND RADIOMETER"})]})
        p5 (dc/platform {:short-name "fo&nA"})
        p6 (dc/platform {:short-name "spo~nA"})
        p7 (dc/platform {:short-name "platform7"
                         :instruments [(dc/instrument {:short-name "INST7"})]})
        pboost (dc/platform {:short-name "boost"})
        pr1 (dc/projects "project-short-name")
        pr2 (dc/projects "Proj-2")
        sk1 (dc/science-keyword {:category "Cat1"
                                 :topic "Topic1"
                                 :term "Term1"
                                 :variable-level-1 "Level1-1"
                                 :variable-level-2 "Level1-2"
                                 :variable-level-3 "Level1-3"
                                 :detailed-variable "SUPER DETAILED!"})
        sk2 (dc/science-keyword {:category "Hurricane"
                                 :topic "Laser spoonA"
                                 :term "Extreme"
                                 :variable-level-1 "Level2-1"
                                 :variable-level-2 "Level2-2"
                                 :variable-level-3 "Level2-3"})
        sk3 (dc/science-keyword {:category "Cat2"
                                 :topic "Topic1"
                                 :term "Term1"
                                 :variable-level-1 "Level3-1"
                                 :variable-level-2 "Level3-2"
                                 :variable-level-3 "Level3-3"
                                 :detailed-variable "S@PER"})
        skboost (dc/science-keyword {:category "boost"
                                     :topic "boost"
                                     :term "boost"
                                     :variable-level-1 "boost"
                                     :variable-level-2 "boost"
                                     :variable-level-3 "boost"
                                     :detailed-variable "boost"})
        personnel1 (dc/personnel "Bob" "Hope" "bob.hope@nasa.gov" "TECHNICAL CONTACT")
        personnel2 (dc/personnel "Victor" "Fries" "victor.fries@nsidc.gov" "TECHNICAL CONTACT")
        personnel3 (dc/personnel "Otto" "Octavious" "otto.octavious@noaa.gov")
        tdcs1 (dc/two-d "XYZ")
        tdcs2 (dc/two-d "twoduniq")
        org (dc/org :archive-center "Some&Place")
        url1 (dc/related-url {:url "http://cmr.earthdata.nasa.gov"
                              :description "Earthdata"})
        url2 (dc/related-url {:url "http://nsidc.org/"
                              :description "Home page of National Snow and Ice Data Center"})
        coll1 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll1" :version-description "VersionDescription"}))
        coll2 (d/ingest "PROV1" (dc/collection
                                 {:entry-title "coll2" :short-name "ABC!XYZ" :version-id "V001"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3" :collection-data-type "OTHER"}))
        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "coll4" :collection-data-type "OTHER"}))
        coll5 (d/ingest "PROV2" (dc/collection {:entry-title "coll5" :short-name "Space!Laser"}))
        coll6 (d/ingest "PROV2" (dc/collection {:entry-title "coll6"
                                                :organizations [org]
                                                :projects pr2
                                                :platforms [p7]}))
        coll7 (d/ingest "PROV2" (dc/collection {:entry-title "coll7" :version-id "Laser"}))
        coll8 (d/ingest "PROV2" (dc/collection {:entry-title "coll8" :processing-level-id "PDQ123"}))

        coll9 (d/ingest "PROV2" (dc/collection {:entry-title "coll09" :science-keywords [sk1 sk2]}))


        coll10 (d/ingest "PROV2" (dc/collection {:entry-title "coll10"
                                                 :spatial-keywords ["in out"]
                                                 :science-keywords [sk3]}))
        coll11 (d/ingest "PROV2" (dc/collection {:entry-title "coll11" :platforms [p2 p3 p5 p6]
                                                 :product-specific-attributes [psa5]}))
        coll12 (d/ingest "PROV2" (dc/collection {:entry-title "coll12" :product-specific-attributes [psa1 psa2 psa3 psa4]}))
        coll13 (d/ingest "PROV2" (dc/collection {:entry-title "coll13" :two-d-coordinate-systems [tdcs1 tdcs2]}))
        coll14 (d/ingest "PROV2" (dc/collection {:entry-title "coll14" :short-name "spoonA laser"}))
        coll15 (d/ingest "PROV2" (dc/collection {:entry-title "coll15" :processing-level-id "plid1"
                                                 :collection-data-type "SCIENCE_QUALITY" :platforms [p1]
                                                 :summary "summary" :temporal-keywords ["tk1" "tk2"]}))
        coll16 (d/ingest "PROV2" (dc/collection-dif {:short-name "entryid4"}) {:format :dif})
        coll17 (d/ingest "PROV2" (dc/collection {:associated-difs ["DIF-1" "DIF-2"]}))
        coll18 (d/ingest "PROV2" (dc/collection {:short-name "SNFoobar"}))
        coll20 (d/ingest "PROV2" (dc/collection {:projects pr1 :entry-title "Mixed"}))
        coll21 (d/ingest "PROV2" (dc/collection {:entry-title "coll21" :short-name "Laser"}))
        coll22 (d/ingest "PROV2" (dc/collection {:collection-data-type "NEAR_REAL_TIME" :short-name "Mixed"}))
        coll23 (d/ingest "PROV1" (dc/collection {:entry-title "coll23" :short-name "\"Quoted\" collection"}))
        coll24 (d/ingest "PROV2" (dc/collection {:entry-title "coll24" :short-name "coll24" :platforms [p4]}))
        ;; Adding personnel here to test keyword search using DataCenter contacts
        coll25 (d/ingest "PROV2" (dc/collection {:entry-title "coll25" :related-urls [url1 url2] :personnel [personnel3]}))
        coll-boost (d/ingest "PROV2" (dc/collection {:entry-title "boost"
                                                     :short-name "boost"
                                                     :platforms [pboost]
                                                     :science-keywords [skboost]}))

        coll26 (d/ingest "PROV4" (dc/collection-dif10 {:entry-title "coll26" :personnel [personnel1]}) {:format :dif10})
        coll27 (d/ingest "PROV5" (dc/collection-dif10 {:entry-title "coll27" :personnel [personnel2]}) {:format :dif10})]

    (index/wait-until-indexed)

    (testing "search by keywords."
      (are [keyword-str items]
        (let [parameter-refs (search/find-refs :collection {:keyword keyword-str})
              json-refs (search/find-refs-with-json-query :collection {} {:keyword keyword-str})
              parameter-matches? (d/refs-match? items parameter-refs)
              json-matches? (d/refs-match? items json-refs)]
          (when-not parameter-matches?
            (println "Parameter search failed")
            (println "Expected:" (map :entry-title items))
            (println "Actual:" (map :name (:refs parameter-refs))))
          (when-not json-matches?
            (println "JSON Query search failed")
            (println "Expected:" (map :entry-title items))
            (println "Actual:" (map :name (:refs json-refs))))
          (and parameter-matches? json-matches?))

        ;; spatial keywords
        "in" [coll10]))

    (testing "Default boosts on fields"
      (are3 [params scores] (is (= (map #(/ % 2.0) scores)
                                   (map :score (:refs (search/find-refs :collection params)))))
        "spatial-keyword"
        {:keyword "in out"} [(k2e/get-boost nil :spatial-keyword)]))

   (testing "Combine keyword and param boosts"
     ;; Check that the scores are very close to account for slight difference in
     ;; number coming from elastic.
     (are3 [params score]
        (is (< (Math/abs
                (double (- (/ score 2.0)
                           (:score (first (:refs (search/find-refs :collection params)))))))
               0.0000001))


       "combine keyword, data center, and instrument"
       {:keyword "Proj-2" :data-center "Some&Place" :instrument "INST7"}
       (* project-boost data-center-boost instrument-boost)

       "instrument search and instrument keyword only results in one instrument boost"
       {:keyword "INST7" :instrument "INST7"} instrument-boost)

    (testing "Specified boosts on fields"
      (are3 [params scores] (is (= (map #(/ % 2.0) scores)
                                   (map :score (:refs (search/find-refs :collection params)))))
        "spatial-keyword"
        {:keyword "in out" :boosts {:spatial-keyword 8.0}} [8.0])))))
