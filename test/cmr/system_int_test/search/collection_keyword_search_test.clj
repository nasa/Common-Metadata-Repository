(ns cmr.system-int-test.search.collection-keyword-search-test
  "Integration test for CMR collection search by keyword terms"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-keywords
  (let [psa1 (dc/psa "alpha" :string "ab")
        psa2 (dc/psa "bravo" :string "bf")
        psa3 (dc/psa "charlie" :string "foo")
        psa4 (dc/psa "case" :string "up")
        p1 (dc/platform "platform_Sn B" "platform_Ln B"
                        (dc/instrument "isnA" "ilnA"
                                       (dc/sensor "ssnA" "slnA")))
        p2 (dc/platform "platform_SnA spoonA" "platform_LnA"
                        (dc/instrument "isnB" "ilnB"
                                       (dc/sensor "ssnB" "slnB")))
        p3 (dc/platform "spoonA")
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
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "coll1" }))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "coll2" :short-name "ABC!XYZ" :version-id "V001"}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "coll3" :collection-data-type "Foo"}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll4" :collection-data-type "Bar"}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll5" :long-name "ABC" :short-name "Space!Laser"}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll6" :organizations [(dc/org :archive-center "Some&Place")]}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll7" :version-id "Laser"}))
        coll8 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll8" :processing-level-id "PDQ123"}))
        coll9 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll9" :science-keywords [sk1 sk2]}))
        coll10 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll10" :spatial-keywords ["in out"]}))
        coll11 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll11" :platforms [p2 p3]}))
        coll12 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll12" :product-specific-attributes [psa1 psa2 psa3 psa4]}))
        coll13 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll13" :two-d-coordinate-systems [tdcs1]}))
        coll14 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll14" :long-name "spoonA laser"}))
        coll15 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "coll15" :processing-level-id "plid1"
                                                     :collection-data-type "cldt" :platforms [p1]
                                                     :summary "summary"}))
        coll16 (d/ingest "CMR_PROV2" (dc/collection {:entry-id "entryid4"}) :dif)]

    (index/refresh-elastic-index)

    (testing "search by keywords."
      (are [keyword-str items]
           (let [refs (search/find-refs :collection {:keyword keyword-str})
                 matches? (d/refs-match? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "ABC" [coll2 coll5]
           "XYZ" [coll2 coll13]
           "place" [coll6]
           "Laser" [coll5 coll7 coll9 coll14]
           "ABC place Hurricane" [coll2 coll5 coll6 coll9]
           "BLAH" []

           ;; Checking specific fields

           ;; entry title
           "coll1" [coll1]

           ;; entry id
           "entryid4" [coll16]

           ;; short name
           "XYZ" [coll2 coll13]

           ;; long name
           "ABC" [coll5 coll2]

           ;; version id
           "V001" [coll2]

           ;; processing level id
           "plid1" [coll15]

           ;; collection data type
           "cldt" [coll15]

           ;; summary
           "summary" [coll15]

           ;; TODO
           ;; spatial keywords
           ;; temporal keywords
           ;; associated dif
           ;; two d coord
           ;; archive center
           ;; attributes

           ;; Platforms
           ;; - short name
           "platform_SnA" [coll11]
           ;; - long name
           "platform_ln" [coll15]

           ;; Instruments
           ;; - short name
           "isnA" [coll15]
           ;; - long name
           "ilnB" [coll11]

           ;; Sensors
           ;; - short name
           "ssnA" [coll15]
           ;; - long name
           "slnB" [coll11]

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
           "SUPER" [coll9]



           ))

    (testing "search by keywords using wildcard *."
      (are [keyword-str items]
           (let [refs (search/find-refs :collection {:keyword keyword-str})
                 matches? (d/refs-match? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "A*C" [coll2 coll5]
           "XY*" [coll2 coll13]
           "*aser" [coll5 coll7 coll9 coll14]
           "ABC p*ce Hurricane" [coll2 coll5 coll6 coll9]))
    (testing "search by keywords using wildcard ?."
      (are [keyword-str items]
           (let [refs (search/find-refs :collection {:keyword keyword-str})
                 matches? (d/refs-match? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "A?C" [coll2 coll5]
           "XY?" [coll2 coll13]
           "?aser" [coll5 coll7 coll9 coll14]
           "ABC ?lace Hurricane" [coll2 coll5 coll6 coll9]))
    (testing "sorted search by keywords."
      (are [keyword-str items]
           (let [refs (search/find-refs :collection {:keyword keyword-str})
                 matches? (d/refs-match-order? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "Laser spoonA" [coll14 coll11 coll9 coll5 coll7]
           "La?er spoonA" [coll14 coll11 coll9 coll5 coll7]
           "L*er spo*A" [coll14 coll11 coll9 coll5 coll7]
           "L?s* s?o*A" [coll14 coll11 coll9 coll5 coll7]))
    (testing "sorted search by keywords with sort keys."
      (are [keyword-str sort-key items]
           (let [refs (search/find-refs :collection {:keyword keyword-str :sort-key sort-key})
                 matches? (d/refs-match-order? items refs)]
             (when-not matches?
               (println "Expected:" (map :entry-title items))
               (println "Actual:" (map :name (:refs refs))))
             matches?)
           "Laser spoonA" "-entry-title" [coll9 coll7 coll5 coll14 coll11]
           "La?er spoonA" "score" [coll14 coll11 coll9 coll5 coll7]
           "Laser spo*A" "+score" [coll5 coll7 coll9 coll11 coll14]
           "L?s* s?o*A" "-score" [coll14 coll11 coll9 coll5 coll7]))))

