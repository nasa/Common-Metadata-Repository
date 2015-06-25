(ns cmr.system-int-test.search.collection-science-keyword-search-test
  "Integration test for CMR collection search by science keyword terms"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-science-keywords
  (let [sk1 (dc/science-keyword {:category "Cat1"
                                 :topic "Topic1"
                                 :term "Term1"
                                 :variable-level-1 "Level1-1"
                                 :variable-level-2 "Level1-2"
                                 :variable-level-3 "Level1-3"
                                 :detailed-variable "Detail1"})
        sk2 (dc/science-keyword {:category "Hurricane"
                                 :topic "Popular"
                                 :term "Extreme"
                                 :variable-level-1 "Level2-1"
                                 :variable-level-2 "Level2-2"
                                 :variable-level-3 "Level2-3"
                                 :detailed-variable "UNIVERSAL"})
        sk3 (dc/science-keyword {:category "Hurricane"
                                 :topic "Popular"
                                 :term "UNIVERSAL"})
        sk4 (dc/science-keyword {:category "Hurricane"
                                 :topic "Cool"
                                 :term "Term4"
                                 :variable-level-1 "UNIVERSAL"})
        sk5 (dc/science-keyword {:category "Tornado"
                                 :topic "Popular"
                                 :term "Extreme"})
        sk6 (dc/science-keyword {:category "UPCASE"
                                 :topic "Popular"
                                 :term "Mild"})
        sk7 (dc/science-keyword {:category "upcase"
                                 :topic "Cool"
                                 :term "Mild"})
        coll1 (d/ingest "PROV1" (dc/collection {:science-keywords [sk1]}))
        coll2 (d/ingest "PROV1" (dc/collection {:science-keywords [sk2]}))
        coll3 (d/ingest "PROV1" (dc/collection {:science-keywords [sk3]}))
        coll4 (d/ingest "PROV1" (dc/collection {:science-keywords [sk4]}))
        coll5 (d/ingest "PROV1" (dc/collection {:science-keywords [sk5]}))
        coll6 (d/ingest "PROV1" (dc/collection {:science-keywords [sk3 sk5]}))
        coll7 (d/ingest "PROV2" (dc/collection {:science-keywords [sk4 sk5]}))
        coll8 (d/ingest "PROV2" (dc/collection {:science-keywords [sk6]}))
        coll9 (d/ingest "PROV2" (dc/collection {:science-keywords [sk7]}))

        coll10 (d/ingest "PROV2" (dc/collection {}))]

    (index/wait-until-indexed)

    (testing "search by science keywords."
      (are [science-keyword value items]
           (d/refs-match? items
                          (search/find-refs
                            :collection
                            {:science-keywords {:0 {science-keyword value}}}))
           :category "Cat1" [coll1]
           :topic "Topic1" [coll1]
           :term "Term1" [coll1]
           :variable-level-1 "Level1-1" [coll1]
           :variable-level-2 "Level1-2" [coll1]
           :variable-level-3 "Level1-3" [coll1]
           :detailed-variable "Detail1" [coll1]
           :category "Tornado" [coll5 coll6 coll7]
           :any "UNIVERSAL" [coll2 coll3 coll4 coll6 coll7]
           :category "BLAH" []))

    (testing "search by science keywords, combined."
      (is (d/refs-match? [coll2]
                         (search/find-refs
                           :collection
                           {:science-keywords {:0 {:category "Hurricane"
                                                   :topic "Popular"
                                                   :term "Extreme"}}}))))

    (testing "search by science keywords, multiple."
      (is (d/refs-match? [coll2 coll6]
                         (search/find-refs
                           :collection
                           {:science-keywords {:0 {:category "Hurricane"
                                                   :topic "Popular"}
                                               :1 {:term "Extreme"}}}))))

    (testing "search by science keywords, multiple values in one field."
      (is (d/refs-match? [coll2 coll5 coll6 coll7]
                         (search/find-refs
                           :collection
                           {:science-keywords {:0 {:category ["Hurricane" "Tornado"]
                                                   :term "Extreme"}}}))))

    (testing "search by science keywords, ignore case"
      (are [items science-keyword value ignore-case]
           (d/refs-match? items
                          (search/find-refs
                            :collection
                            {:science-keywords {:0 {science-keyword value}}
                             "options[science-keywords][ignore-case]" ignore-case}))

           [coll1] :category "Cat1" false
           [] :category "cat1" false
           [coll1] :category "cat1" true))

    (testing "search by science keywords, multiple. options :or false"
      (is (d/refs-match? [coll2 coll6]
                         (search/find-refs
                           :collection
                           {:science-keywords {:0 {:category "Hurricane"
                                                   :topic "Popular"}
                                               :1 {:term "Extreme"}}
                            "options[science-keywords][or]" "false"}))))

    (testing "search by science keywords, multiple. options :or true"
      (is (d/refs-match? [coll2 coll3 coll5 coll6 coll7]
                         (search/find-refs
                           :collection
                           {:science-keywords {:0 {:category "Hurricane"
                                                   :topic "Popular"}
                                               :1 {:term "Extreme"}}
                            "options[science-keywords][or]" "true"}))))

    (testing "search by science keywords, multiple and legacy :or format"
      (is (d/refs-match? [coll2 coll6]
                         (search/find-refs
                           :collection
                           {:science-keywords {:0 {:category "Hurricane"
                                                   :topic "Popular"}
                                               :1 {:term "Extreme"}
                                               :or "false"}})))
      (is (d/refs-match? [coll2 coll3 coll5 coll6 coll7]
                         (search/find-refs
                           :collection
                           {:science-keywords {:0 {:category "Hurricane"
                                                   :topic "Popular"}
                                               :1 {:term "Extreme"}
                                               :or "true"}}))))

    (testing "search by science keywords, default case insensitive."
      (are [science-keyword value items]
           (d/refs-match? items
                          (search/find-refs
                            :collection
                            {:science-keywords {:0 {science-keyword value}}}))
           :category "cat1" [coll1]
           :topic "TOPIC1" [coll1]
           :term "term1" [coll1]
           :variable-level-1 "level1-1" [coll1]
           :variable-level-2 "LEVel1-2" [coll1]
           :variable-level-3 "LevEL1-3" [coll1]
           :detailed-variable "DetAIL1" [coll1]
           :category "TORNADO" [coll5 coll6 coll7]
           :category "Upcase" [coll8 coll9]))

    (testing "search collections by science keywords with aql"
      (are [items science-keywords options]
           (let [condition (merge {:scienceKeywords science-keywords} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1] [{:category "Cat1"}] {}
           [coll1] [{:topic "Topic1"}] {}
           [coll1] [{:term "Term1"}] {}
           [coll1] [{:variable-level-1 "Level1-1"}] {}
           [coll1] [{:variable-level-2 "Level1-2"}] {}
           [coll1] [{:variable-level-3 "Level1-3"}] {}
           [coll1] [{:detailed-variable "Detail1"}] {}
           [coll5 coll6 coll7] [{:category "Tornado"}] {}
           [coll2 coll3 coll4 coll6 coll7] [{:any "UNIVERSAL"}] {}
           [] [{:category "BLAH"}] {}

           [coll2] [{:category "Hurricane"
                     :topic "Popular"
                     :term "Extreme"}] {}
           [coll2 coll6] [{:category "Hurricane"
                           :topic "Popular"}
                          {:term "Extreme"}] {}
           [coll2 coll6] [{:category "Hurricane"
                           :topic "Popular"}
                          {:term "Extreme"}] {:and true}
           [coll2 coll3 coll5 coll6 coll7] [{:category "Hurricane"
                                             :topic "Popular"}
                                            {:term "Extreme"}] {:or true}

           ;; case sensitivity
           [] [{:category "cat1"}] {}
           [] [{:category "cat1" :ignore-case false}] {}
           [coll1] [{:category "Cat1"} {:term "extreme" :ignore-case false}] {:or true}

           ;; pattern
           [coll1] [{:category "C%" :pattern true}] {}
           [] [{:category "C%" :pattern false}] {}
           [] [{:category "C%"}] {}
           [coll1] [{:category "Cat_" :pattern true}] {}))

    (testing "Search collections by science keywords using a JSON Query"
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll1] {:science-keywords {:category "Cat1"}}
           [coll1] {:science-keywords {:topic "Topic1"}}
           [coll1] {:science-keywords {:term "Term1"}}
           [coll1] {:science-keywords {:variable-level-1 "Level1-1"}}
           [coll1] {:science-keywords {:variable-level-2 "Level1-2"}}
           [coll1] {:science-keywords {:variable-level-3 "Level1-3"}}
           [coll1] {:science-keywords {:detailed-variable "Detail1"}}
           [coll5 coll6 coll7] {:science-keywords {:category "Tornado"}}
           [coll2 coll3 coll4 coll6 coll7] {:science-keywords {:any "UNIVERSAL"}}

           [] {:science-keywords {:category "BLAH"}}

           [coll2] {:science-keywords {:category "Hurricane"
                     :topic "Popular"
                     :term "Extreme"}}
           [coll2 coll6] {:and [{:science-keywords {:category "Hurricane"
                                                    :topic "Popular"}}
                                {:science-keywords {:term "Extreme"}}]}
           [coll2 coll3 coll5 coll6 coll7] {:or [{:science-keywords {:category "Hurricane"
                                                                     :topic "Popular"}}
                                                  {:science-keywords {:term "Extreme"}}]}))))

(deftest search-science-keywords-error-scenarios
  (testing "search by invalid format."
    (let [{:keys [status errors]} (search/find-refs :collection {:science-keywords {:0 {:and "true"}}})]
      (is (= 400 status))
      (is (re-find #"parameter \[and\] is not a valid science keyword search term." (first errors))))))
