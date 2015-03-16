(ns cmr.system-int-test.search.collection-facets-search-test
  "This tests the retrieving facets when searching for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.echo-util :as e]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"} false))

(comment

  (dev-sys-util/reset)
  (ingest/create-provider "provguid1" "PROV1")
  (ingest/create-provider "provguid2" "PROV2")
  )


(defn make-coll
  "Helper for creating and ingesting a collection"
  [n prov & attribs]
  (d/ingest prov (dc/collection (apply merge {:entry-title (str "coll" n)} attribs))))

;; Attrib functions - These are helpers for creating maps with collection attributes
(defn projects
  [& project-names]
  {:projects (apply dc/projects project-names)})

(defn platforms
  "Creates a specified number of platforms each with a certain number of instruments and sensors"
  ([prefix num-platforms]
   (platforms prefix num-platforms 0 0))
  ([prefix num-platforms num-instruments]
   (platforms prefix num-platforms num-instruments 0))
  ([prefix num-platforms num-instruments num-sensors]
   {:platforms
    (for [pn (range 0 num-platforms)
          :let [platform-name (str prefix "-p" pn)]]
      (apply dc/platform platform-name platform-name nil
             (for [in (range 0 num-instruments)
                   :let [instrument-name (str platform-name "-i" in)]]
               (apply dc/instrument instrument-name nil nil
                      (for [sn (range 0 num-sensors)
                            :let [sensor-name (str instrument-name "-s" sn)]]
                        (dc/sensor sensor-name))))))}))

(defn twod-coords
  [& names]
  {:two-d-coordinate-systems (map dc/two-d names)})


(def sk1 (dc/science-keyword {:category "Cat1"
                              :topic "Topic1"
                              :term "Term1"
                              :variable-level-1 "Level1-1"
                              :variable-level-2 "Level1-2"
                              :variable-level-3 "Level1-3"
                              :detailed-variable "Detail1"}))


(def sk2 (dc/science-keyword {:category "Hurricane"
                              :topic "Popular"
                              :term "Extreme"
                              :variable-level-1 "Level2-1"
                              :variable-level-2 "Level2-2"
                              :variable-level-3 "Level2-3"
                              :detailed-variable "UNIVERSAL"}))

(def sk3 (dc/science-keyword {:category "Hurricane"
                              :topic "Popular"
                              :term "UNIVERSAL"}))

(def sk4 (dc/science-keyword {:category "Hurricane"
                              :topic "Cool"
                              :term "Term4"
                              :variable-level-1 "UNIVERSAL"}))

(def sk5 (dc/science-keyword {:category "Tornado"
                              :topic "Popular"
                              :term "Extreme"}))

(def sk6 (dc/science-keyword {:category "UPCASE"
                              :topic "Popular"
                              :term "Mild"}))

(def sk7 (dc/science-keyword {:category "upcase"
                              :topic "Cool"
                              :term "Mild"}))

(defn science-keywords
  [& sks]
  {:science-keywords sks})

(defn processing-level-id
  [id]
  {:processing-level-id id})


(deftest retrieve-collection-facets-test
  ;; Grant guests permission to provider 1
  (e/grant-guest (e/coll-catalog-item-id "provguid1"))
  ;; Grant guests permission to specific collections in provider 2
  (e/grant-guest (e/coll-catalog-item-id "provguid2" (e/coll-id ["coll2" "coll3" "coll5"])))

  (let [coll1 (make-coll 1 "PROV1"
                         (projects "proj1" "PROJ2")
                         (platforms "A" 2 2 1)
                         (twod-coords "Alpha")
                         (science-keywords sk1 sk4 sk5)
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "Larc")]})
        coll2 (make-coll 2 "PROV2"
                         (projects "proj3" "PROJ2")
                         (platforms "B" 2 2 1)
                         (science-keywords sk1 sk2 sk3)
                         (processing-level-id "pl1")
                         {:organizations [(dc/org :archive-center "GSFC")]})
        coll3 (make-coll 3 "PROV2"
                         (platforms "A" 1 1 1)
                         (twod-coords "Alpha" "Bravo")
                         (science-keywords sk5 sk6 sk7)
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "Larc")]})
        coll4 (make-coll 4 "PROV1"
                         (twod-coords "alpha")
                         (science-keywords sk3)
                         (processing-level-id "PL2")
                         {:organizations [(dc/org :archive-center "Larc")]})
        coll5 (make-coll 5 "PROV2")

        ;; Guests do not have permission to this collection so it will not appear in results
        coll6 (make-coll 6 "PROV2"
                         (projects "proj1")
                         (platforms "A" 1 1 1)
                         (twod-coords "Alpha")
                         (science-keywords sk1)
                         (processing-level-id "PL1"))
        all-colls [coll1 coll2 coll3 coll4 coll5 coll6]]

    (index/wait-until-indexed)

    (testing "invalid include-facets"
      (is (= {:errors ["Parameter include_facets must take value of true, false, or unset, but was foo"] :status 400}
             (search/find-refs :collection {:include-facets "foo"})))
      (is (= {:errors ["Parameter [include_facets] was not recognized."] :status 400}
             (search/find-refs :granule {:include-facets true}))))

    (testing "retreving all facets in different formats"
      (let [expected-facets [{:field "archive_center"
                              :value-counts [["Larc" 3] ["GSFC" 1]]}
                             {:field "project"
                              :value-counts [["PROJ2" 2] ["proj1" 1] ["proj3" 1]]}
                             {:field "platform"
                              :value-counts [["A-p0" 2] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                             {:field "instrument"
                              :value-counts [["A-p0-i0" 2]
                                             ["A-p0-i1" 1]
                                             ["A-p1-i0" 1]
                                             ["A-p1-i1" 1]
                                             ["B-p0-i0" 1]
                                             ["B-p0-i1" 1]
                                             ["B-p1-i0" 1]
                                             ["B-p1-i1" 1]]}
                             {:field "sensor"
                              :value-counts [["A-p0-i0-s0" 2]
                                             ["A-p0-i1-s0" 1]
                                             ["A-p1-i0-s0" 1]
                                             ["A-p1-i1-s0" 1]
                                             ["B-p0-i0-s0" 1]
                                             ["B-p0-i1-s0" 1]
                                             ["B-p1-i0-s0" 1]
                                             ["B-p1-i1-s0" 1]]}
                             {:field "two_d_coordinate_system_name"
                              :value-counts [["Alpha" 2] ["Bravo" 1] ["alpha" 1]]}
                             {:field "processing_level_id"
                              :value-counts [["PL1" 2] ["PL2" 1] ["pl1" 1]]}
                             {:field "category"
                              :value-counts [["Hurricane" 3]
                                             ["Cat1" 2]
                                             ["Tornado" 2]
                                             ["UPCASE" 1]
                                             ["upcase" 1]]}
                             {:field "topic"
                              :value-counts [["Popular" 4] ["Cool" 2] ["Topic1" 2]]}
                             {:field "term"
                              :value-counts [["Extreme" 3]
                                             ["Term1" 2]
                                             ["UNIVERSAL" 2]
                                             ["Mild" 1]
                                             ["Term4" 1]]}
                             {:field "variable_level_1"
                              :value-counts [["Level1-1" 2] ["Level2-1" 1] ["UNIVERSAL" 1]]}
                             {:field "variable_level_2"
                              :value-counts [["Level1-2" 2] ["Level2-2" 1]]}
                             {:field "variable_level_3"
                              :value-counts [["Level1-3" 2] ["Level2-3" 1]]}
                             {:field "detailed_variable"
                              :value-counts [["Detail1" 2] ["UNIVERSAL" 1]]}]]
        (testing "refs"
          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true})))))
        (testing "refs echo-compatible true"
          (is (= expected-facets
                 (search/find-refs :collection {:include-facets true
                                                :echo-compatible true}))))
        (testing "metadata items and direct transformer"
          (is (= expected-facets
                 (:facets (search/find-metadata :collection
                                                :echo10
                                                {:include-facets true
                                                 :concept-id (map :concept-id all-colls)})))))
        (testing "atom"
          (is (= expected-facets
                 (:facets (:results (search/find-concepts-atom :collection {:include-facets true}))))))
        (testing "json"
          (is (= expected-facets
                 (:facets (:results (search/find-concepts-json :collection {:include-facets true}))))))
        (testing "json echo-compatible true"
          (is (= (sort-by :field expected-facets)
                 (sort-by :field (search/find-concepts-json :collection {:include-facets true
                                                                         :echo-compatible true})))))))

    (testing "Search conditions narrow reduce facet values found"
      (testing "search finding two documents"
        (let [expected-facets [{:field "archive_center"
                                :value-counts [["GSFC" 1] ["Larc" 1]]}
                               {:field "project"
                                :value-counts [["PROJ2" 2] ["proj1" 1] ["proj3" 1]]}
                               {:field "platform"
                                :value-counts [["A-p0" 1] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                               {:field "instrument"
                                :value-counts [["A-p0-i0" 1]
                                               ["A-p0-i1" 1]
                                               ["A-p1-i0" 1]
                                               ["A-p1-i1" 1]
                                               ["B-p0-i0" 1]
                                               ["B-p0-i1" 1]
                                               ["B-p1-i0" 1]
                                               ["B-p1-i1" 1]]}
                               {:field "sensor"
                                :value-counts [["A-p0-i0-s0" 1]
                                               ["A-p0-i1-s0" 1]
                                               ["A-p1-i0-s0" 1]
                                               ["A-p1-i1-s0" 1]
                                               ["B-p0-i0-s0" 1]
                                               ["B-p0-i1-s0" 1]
                                               ["B-p1-i0-s0" 1]
                                               ["B-p1-i1-s0" 1]]}
                               {:field "two_d_coordinate_system_name" :value-counts [["Alpha" 1]]}
                               {:field "processing_level_id"
                                :value-counts [["PL1" 1] ["pl1" 1]]}
                               {:field "category"
                                :value-counts [["Cat1" 2] ["Hurricane" 2] ["Tornado" 1]]}
                               {:field "topic"
                                :value-counts [["Popular" 2] ["Topic1" 2] ["Cool" 1]]}
                               {:field "term"
                                :value-counts [["Extreme" 2] ["Term1" 2] ["Term4" 1] ["UNIVERSAL" 1]]}
                               {:field "variable_level_1"
                                :value-counts [["Level1-1" 2] ["Level2-1" 1] ["UNIVERSAL" 1]]}
                               {:field "variable_level_2"
                                :value-counts [["Level1-2" 2] ["Level2-2" 1]]}
                               {:field "variable_level_3"
                                :value-counts [["Level1-3" 2] ["Level2-3" 1]]}
                               {:field "detailed_variable"
                                :value-counts [["Detail1" 2] ["UNIVERSAL" 1]]}]]
          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true
                                                         :project "PROJ2"}))))))

      (testing "AND conditions narrow facets via AND not OR"
        (let [expected-facets [{:field "archive_center"
                                :value-counts [["GSFC" 1]]}
                               {:field "project" :value-counts [["PROJ2" 1] ["proj3" 1]]}
                               {:field "platform" :value-counts [["B-p0" 1] ["B-p1" 1]]}
                               {:field "instrument"
                                :value-counts [["B-p0-i0" 1]
                                               ["B-p0-i1" 1]
                                               ["B-p1-i0" 1]
                                               ["B-p1-i1" 1]]}
                               {:field "sensor"
                                :value-counts [["B-p0-i0-s0" 1]
                                               ["B-p0-i1-s0" 1]
                                               ["B-p1-i0-s0" 1]
                                               ["B-p1-i1-s0" 1]]}
                               {:field "two_d_coordinate_system_name" :value-counts []}
                               {:field "processing_level_id" :value-counts [["pl1" 1]]}
                               {:field "category" :value-counts [["Cat1" 1] ["Hurricane" 1]]}
                               {:field "topic" :value-counts [["Popular" 1] ["Topic1" 1]]}
                               {:field "term" :value-counts [["Extreme" 1]
                                                             ["Term1" 1]
                                                             ["UNIVERSAL" 1]]}
                               {:field "variable_level_1":value-counts [["Level1-1" 1]
                                                                        ["Level2-1" 1]]}
                               {:field "variable_level_2" :value-counts [["Level1-2" 1]
                                                                         ["Level2-2" 1]]}
                               {:field "variable_level_3" :value-counts [["Level1-3" 1]
                                                                         ["Level2-3" 1]]}
                               {:field "detailed_variable" :value-counts [["Detail1" 1]
                                                                          ["UNIVERSAL" 1]]}]]


          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true
                                                         :project ["PROJ2" "proj3"]
                                                         "options[project][and]" true}))))))

      (testing "search finding one document"
        (let [expected-facets [{:field "archive_center" :value-counts [["Larc" 1]]}
                               {:field "project" :value-counts []}
                               {:field "platform" :value-counts []}
                               {:field "instrument" :value-counts []}
                               {:field "sensor" :value-counts []}
                               {:field "two_d_coordinate_system_name" :value-counts [["alpha" 1]]}
                               {:field "processing_level_id" :value-counts [["PL2" 1]]}
                               {:field "category" :value-counts [["Hurricane" 1]]}
                               {:field "topic" :value-counts [["Popular" 1]]}
                               {:field "term" :value-counts [["UNIVERSAL" 1]]}
                               {:field "variable_level_1" :value-counts []}
                               {:field "variable_level_2" :value-counts []}
                               {:field "variable_level_3" :value-counts []}
                               {:field "detailed_variable" :value-counts []}]]
          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true
                                                         :processing-level-id "PL2"}))))))

      (let [empty-facets [{:field "archive_center" :value-counts []}
                          {:field "project" :value-counts []}
                          {:field "platform" :value-counts []}
                          {:field "instrument" :value-counts []}
                          {:field "sensor" :value-counts []}
                          {:field "two_d_coordinate_system_name" :value-counts []}
                          {:field "processing_level_id" :value-counts []}
                          {:field "category" :value-counts []}
                          {:field "topic" :value-counts []}
                          {:field "term" :value-counts []}
                          {:field "variable_level_1" :value-counts []}
                          {:field "variable_level_2" :value-counts []}
                          {:field "variable_level_3" :value-counts []}
                          {:field "detailed_variable" :value-counts []}]]
        (testing "search finding one document with no faceted fields"
          (is (= empty-facets
                 (:facets (search/find-refs :collection {:include-facets true :entry-title "coll5"})))))

        (testing "search finding no documents"
          (is (= empty-facets
                 (:facets (search/find-refs :collection {:include-facets true :entry-title "foo"})))))))))
