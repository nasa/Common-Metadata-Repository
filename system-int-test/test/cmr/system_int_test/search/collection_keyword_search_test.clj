(ns cmr.system-int-test.search.collection-keyword-search-test
  "Integration test for CMR collection search by keyword terms"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.search.data.keywords-to-elastic :as k2e]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}))

(deftest search-by-keywords
  (let [psa1 (dc/psa "alpha" :string "ab")
        psa2 (dc/psa "bravo" :string "bf")
        psa3 (dc/psa "charlie" :string "foo")
        psa4 (dc/psa "case" :string "up")
        p1 (dc/platform "platform_SnB" "platform_Ln B" nil
                        (dc/instrument "isnA" "ilnA" "itechniqueA"
                                       (dc/sensor "ssnA" "slnA")
                                       (dc/sensor "ssnD" "slnD" "techniqueD")))
        p2 (dc/platform "platform_SnA spoonA" "platform_LnA"
                        [(dc/characteristic "char1" "char1desc")
                         (dc/characteristic "char2" "char2desc")]
                        (dc/instrument "isnB" "ilnB" "itechniqueB"
                                       (dc/sensor "ssnB" "slnB" "techniqueB")
                                       (dc/sensor "ssnC" "slnC" "techniqueC")))
        p3 (dc/platform "spoonA")
        pr1 (dc/projects "project-short-name")
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
        tdcs1 (dc/two-d "XYZ")
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1" }))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2" :short-name "ABC!XYZ" :version-id "V001"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3" :collection-data-type "Foo"}))
        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "coll4" :collection-data-type "Bar"}))
        coll5 (d/ingest "PROV2" (dc/collection {:entry-title "coll5" :long-name "ABC" :short-name "Space!Laser"}))
        coll6 (d/ingest "PROV2" (dc/collection {:entry-title "coll6" :organizations [(dc/org :archive-center "Some&Place")]}))
        coll7 (d/ingest "PROV2" (dc/collection {:entry-title "coll7" :version-id "Laser"}))
        coll8 (d/ingest "PROV2" (dc/collection {:entry-title "coll8" :processing-level-id "PDQ123"}))
        coll9 (d/ingest "PROV2" (dc/collection {:entry-title "coll9" :science-keywords [sk1 sk2]}))
        coll10 (d/ingest "PROV2" (dc/collection {:entry-title "coll10" :spatial-keywords ["in out"]}))
        coll11 (d/ingest "PROV2" (dc/collection {:entry-title "coll11" :platforms [p2 p3]}))
        coll12 (d/ingest "PROV2" (dc/collection {:entry-title "coll12" :product-specific-attributes [psa1 psa2 psa3 psa4]}))
        coll13 (d/ingest "PROV2" (dc/collection {:entry-title "coll13" :two-d-coordinate-systems [tdcs1]}))
        coll14 (d/ingest "PROV2" (dc/collection {:entry-title "coll14" :long-name "spoonA laser"}))
        coll15 (d/ingest "PROV2" (dc/collection {:entry-title "coll15" :processing-level-id "plid1"
                                                 :collection-data-type "cldt" :platforms [p1]
                                                 :summary "summary" :temporal-keywords ["tk1" "tk2"]}))
        coll16 (d/ingest "PROV2" (dc/collection {:entry-id "entryid4"}) :dif)
        coll17 (d/ingest "PROV2" (dc/collection {:associated-difs ["DIF-1" "DIF-2"]}))
        coll18 (d/ingest "PROV2" (dc/collection {:short-name "SNFoobar"}))
        coll19 (d/ingest "PROV2" (dc/collection {:long-name "LNFoobar"}))
        coll20 (d/ingest "PROV2" (dc/collection {:projects pr1}))
        coll21 (d/ingest "PROV2" (dc/collection {:entry-title "coll21" :long-name "ABC!"}))
        coll22 (d/ingest "PROV2" (dc/collection {:collection-data-type "NEAR_REAL_TIME"}))
        coll23 (d/ingest "PROV1" (dc/collection {:entry-title "coll23" :long-name "\"Quoted\" collection" }))]

    (index/refresh-elastic-index)

    (testing "search by keywords."
      (are [keyword-str items]
           (let [refs (search/find-refs :collection {:keyword keyword-str})
                 matches? (d/refs-match? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "ABC" [coll2 coll5 coll21]
           "place" [coll6]
           "Laser" [coll5 coll7 coll9 coll14]
           "ABC space" [coll5]
           "BLAH" []
           "abc!" [coll21]

           ;; Checking specific fields

           ;; provider
           "PROV1" [coll1 coll2 coll3 coll23]

           ;; entry title
           "coll1" [coll1]

           ;; entry id
           "entryid4" [coll16]

           ;; short name
           "XYZ" [coll2 coll13]

           ;; long name
           "ABC" [coll5 coll2 coll21]

           ;; version id
           "V001" [coll2]

           ;; processing level id
           "plid1" [coll15]

           ;; collection data type
           "cldt" [coll15]

           ;; collection data type aliases for NEAR_REAL_TIME
           "NEAR_REAL_TIME" [coll22]
           "NRT" [coll22]
           "near_real_time" [coll22]
           "nrt" [coll22]
           "near-real-time" [coll22]
           "near real-time" [coll22]
           "near-real time" [coll22]

           ;; summary
           "summary" [coll15]

           ;; temporal keywords
           "tk1" [coll15]

           ;; spatial keywords
           "in" [coll10]

           ;; associated difs
           "dif-1" [coll17]

           ;; two d coord
           "xyz" [coll2 coll13]

           ;; archive center
           "some" [coll6]

           ;; attributes
           ;; - name
           "charlie" [coll12]
           ;; - description
           "Generated" [coll12]

           ;; Platforms
           ;; - short name
           "platform_SnA" [coll11]
           ;; - long name
           "platform_ln" [coll15]
           ;; - characteristic name
           "char1" [coll11]
           "char2" [coll11]
           "char1 char2" [coll11]
           ;; - chracteristic description
           "char1desc" [coll11]
           "char2desc" [coll11]
           "char1desc char2desc" [coll11]

           ;; Instruments
           ;; - short name
           "isnA" [coll15]
           ;; - long name
           "ilnB" [coll11]
           ;; - technique
           "itechniqueB" [coll11]
           "itechniqueA" [coll15]

           ;; Sensors
           ;; - short name
           "ssnA" [coll15]
           ;; - long name
           "slnB" [coll11]
           ;; - technique
           "techniqueB" [coll11]
           "techniqueD" [coll15]
           "techniqueB techniqueC" [coll11]

           ;; Science keywords
           ;; - category
           "Cat1" [coll9]
           ;; - topic
           "Topic1" [coll9]
           ;; - term
           "Term1" [coll9]
           ;; - variable-levels
           "Level2-1" [coll9]
           "Level2-2" [coll9]
           "Level2-3" [coll9]
           ;; - detailed-variable
           "SUPER" [coll9]))

    (testing "Boost on fields"
      (are [keyword-str scores] (= (map #(/ % 2.0) scores)
                                   (map :score (:refs (search/find-refs :collection
                                                                        {:keyword keyword-str}))))
           ;; short-name
           "SNFoobar" [k2e/short-name-long-name-boost]
           ;; long-name
           "LNFoobar" [k2e/short-name-long-name-boost]

           ;; project short-name
           (:short-name (first pr1)) [k2e/project-boost]
           ;; project long-name
           (:long-name (first pr1)) [k2e/project-boost]

           ;; platform short-name
           (:short-name p1) [k2e/platform-boost]
           ;; platform long-name
           (:long-name p1) [k2e/platform-boost]

           ;; instrument short-name
           (:short-name (first (:instruments p1))) [k2e/instrument-boost]
           ;; instrument long-name
           (:long-name (first (:instruments p1))) [k2e/instrument-boost]

           ;; sensor short-name
           (:short-name (first (:sensors (first (:instruments p1))))) [k2e/sensor-boost]
           ;; sensor long-name
           (:long-name (first (:sensors (first (:instruments p1))))) [k2e/sensor-boost]

           ;; temporal-keywords
           "tk1" [k2e/temporal-keyword-boost]

           ;; spatial-keyword
           "in out" [k2e/spatial-keyword-boost]

           ;; science-keywords
           (:category sk1) [k2e/science-keywords-boost]))

    (testing "search by keywords using wildcard *."
      (are [keyword-str items]
           (let [refs (search/find-refs :collection {:keyword keyword-str})
                 matches? (d/refs-match? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "A*C" [coll2 coll5 coll21]
           "XY*" [coll2 coll13]
           "*aser" [coll5 coll7 coll9 coll14]
           "p*ce" [coll6]
           "NEA*REA*IME" [coll22]
           "nea*rea*ime" [coll22]
           "\"Quoted*" [coll23]))
    (testing "search by keywords using wildcard ?."
      (are [keyword-str items]
           (let [refs (search/find-refs :collection {:keyword keyword-str})
                 matches? (d/refs-match? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "A?C" [coll2 coll5 coll21]
           "XY?" [coll2 coll13]
           "?aser" [coll5 coll7 coll9 coll14]
           "p*ace" [coll6]
           "NEAR?REAL?TIME" [coll22]
           "near?real?time" [coll22]))
    (testing "sorted search by keywords."
      (are [keyword-str items]
           (let [refs (search/find-refs :collection {:keyword keyword-str})
                 matches? (d/refs-match-order? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "Laser spoonA" [coll14 coll9]
           "La?er spoonA" [coll14 coll9]
           "L*er spo*A" [coll14 coll9]
           "L?s* s?o*A" [coll14 coll9]))
    (testing "sorted search by keywords with sort keys."
      (are [keyword-str sort-key items]
           (let [refs (search/find-refs :collection {:keyword keyword-str :sort-key sort-key})
                 matches? (d/refs-match-order? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "Laser" "-entry-title" [coll9 coll7 coll5 coll14]
           "Laser" "score" [coll14 coll5 coll7 coll9]
           "Laser" "+score" [coll5 coll7 coll9 coll14]
           "Laser" "-score" [coll14 coll5 coll7 coll9]))
    (testing "search by multiple keywords returns an error."
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :keyword ["Laser" "spoon"]})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (= "Parameter [keyword] must have a single value." (first errors)))))))

(deftest search-by-keywords-with-special-chars
  ;; needed for special charatcter tests
  (let [coll-data [["coll00" "dummy"]
                   ["coll01" "begin!end"]
                   ["coll02" "begin@end"]
                   ["coll03" "begin#end"]
                   ["coll04" "begin$end"]
                   ["coll05" "begin%end"]
                   ["coll06" "begin^end"]
                   ["coll07" "begin&end"]
                   ["coll08" "begin(end"]
                   ["coll09" "begin)end"]
                   ["coll10" "begin-end"]
                   ["coll11" "begin=end"]
                   ["coll12" "begin_end"]
                   ["coll13" "begin+end"]
                   ["coll14" "begin{end"]
                   ["coll15" "begin}end"]
                   ["coll16" "begin[end"]
                   ["coll17" "begin]end"]
                   ["coll18" "begin|end"]
                   ["coll19" "begin\\end"]
                   ["coll20" "begin;end"]
                   ["coll21" "begin'end"]
                   ["coll22" "begin.end"]
                   ["coll23" "begin,end"]
                   ["coll24" "begin/end"]
                   ["coll25" "begin:end"]
                   ["coll26" "begin\"end"]
                   ["coll27" "begin<end"]
                   ["coll28" "begin>end"]
                   ["coll29" "begin?end"]
                   ["coll30" "begin`end"]
                   ["coll31" "begin~end"]
                   ["coll32" "modis foobar"]
                   ["coll33" "bleep blop blorp"]
                   ["coll34" "abcdefghijklmnop"]
                   ["coll35" "foo modis bar"]
                   ["coll36" "akdi modis/terra dke"]
                   ["coll37" "akdi modis-terra dke"]
                   ["coll38" "Dataset with foobar"]
                   ["coll39" "foo54"]
                   ["coll40" "foo67"]
                   ["coll41" "moding"]
                   ["coll42" "outmoded"]
                   ["coll43" "outmodising"]
                   ["coll44" "out-modis-ed"]
                   ["coll45" "carbon*oxygen"]
                   ["coll46" "Dataset no withword"]
                   ["coll47" "begin&&end"]
                   ["coll48" "choco and taco"]
                   ["coll49" "choco or taco"]
                   ["coll50" "begin*end"]]
        colls (doall (for [[coll summary] coll-data]
                       (d/ingest "PROV3" (dc/collection {:entry-title coll :summary summary}))))]
    (index/refresh-elastic-index)
    (are [keyword-str indexes]
         (let [refs (search/find-refs :collection {:keyword keyword-str :page_size 51})
               items (map (partial nth colls) indexes)
               matches? (d/refs-match? items refs)]
           (when-not matches?
             (println "Expected:" (map :entry-title items))
             (println "Actual:" (map :name (:refs refs))))
           matches?)
         "begin!end" [1]
         "begin\\end" [19]
         "begin<end" [27]
         "begin\\?end" [29]
         "begin~end" [31]
         "begin\\*end" [50]
         "begin" [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 47 50]
         )))

