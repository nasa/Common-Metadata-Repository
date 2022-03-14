(ns cmr.system-int-test.search.facets.collection-facets-search-test
  "This tests the retrieving facets when searching for collections"
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.atom :as da]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-spec]
   [cmr.system-int-test.data2.umm-spec-common :as umm-spec-common]
   [cmr.system-int-test.search.facets.facet-responses :as fr]
   [cmr.system-int-test.search.facets.facets-util :as fu]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as tc]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                                          {:grant-all-search? false}))

(defn- make-dif-coll
  "Helper for creating and ingesting a DIF collection"
  [n prov & attribs]
  (d/ingest-umm-spec-collection
   prov (data-umm-spec/collection-missing-properties-dif (apply merge {:EntryTitle (str "coll" n)} attribs))
        {:format :dif}))

(defn- grant-permissions
  "Grant permissions to all collections in PROV1 and a subset of collections in PROV2"
  []
  (e/grant-guest (s/context) (e/coll-catalog-item-id "PROV1"))
  (e/grant-guest (s/context)
                 (e/coll-catalog-item-id "PROV2" (e/coll-id ["coll2" "coll3" "coll5"]))))

(defn- get-facet-results
  "Returns the facets returned by a search in both JSON and XML reference formats. Takes the type
  of facets to be returned - either :flat or :hierarchical. The format of the response is:
  {:xml-facets xml-facets
  :json-facets json-results}"
  [type]
  (index/wait-until-indexed)
  (let [search-options {:page-size 0
                        :include-facets true
                        :hierarchical-facets (= :hierarchical type)}]
    {:xml-facets (:facets (search/find-refs :collection search-options))
     :json-facets (get-in (search/find-concepts-json :collection search-options)
                          [:results :facets])}))

(def sk1 (umm-spec-common/science-keyword {:Category "Cat1"
                                           :Topic "Topic1"
                                           :Term "Term1"
                                           :VariableLevel1 "Level1-1"
                                           :VariableLevel2 "Level1-2"
                                           :VariableLevel3 "Level1-3"
                                           :DetailedVariable "Detail1"}))

(def sk2 (umm-spec-common/science-keyword {:Category "Hurricane"
                                           :Topic "Popular"
                                           :Term "Extreme"
                                           :VariableLevel1 "Level2-1"
                                           :VariableLevel2 "Level2-2"
                                           :VariableLevel3 "Level2-3"
                                           :DetailedVariable "UNIVERSAL"}))

(def sk3 (umm-spec-common/science-keyword {:Category "Hurricane"
                                           :Topic "Popular"
                                           :Term "UNIVERSAL"}))

(def sk4 (umm-spec-common/science-keyword {:Category "Hurricane"
                                           :Topic "Cool"
                                           :Term "Term4"
                                           :VariableLevel1 "UNIVERSAL"}))

(def sk5 (umm-spec-common/science-keyword {:Category "Tornado"
                                           :Topic "Popular"
                                           :Term "Extreme"}))

(def sk6 (umm-spec-common/science-keyword {:Category "UPCASE"
                                           :Topic "Popular"
                                           :Term "Mild"}))

(def sk7 (umm-spec-common/science-keyword {:Category "upcase"
                                           :Topic "Cool"
                                           :Term "Mild"}))

(def sk8 (umm-spec-common/science-keyword {:Category "Category"
                                           :Topic "Topic"
                                           :Term "Term"
                                           :VariableLevel1 "V-L1"
                                           :DetailedVariable "Detailed-No-Level2-or-3"}))

(deftest science-keyword-hierarchical-fields-test
  (grant-permissions)
  (let [coll1 (fu/make-coll 1 "PROV1"
                            (fu/science-keywords (umm-spec-common/science-keyword {:Category "Earth Science"
                                                                                   :Topic "ATMOSPHERE"
                                                                                   :Term "ATMOSPHERIC CHEMISTRY"
                                                                                   :VariableLevel1 "CARBON AND HYDROCARBON COMPOUNDS"
                                                                                   :VariableLevel2 "CARBON DIOXIDE"
                                                                                   :DetailedVariable "CO2"})))
        actual-facets (get-facet-results :hierarchical)]
    (:json-facets actual-facets)))

(deftest all-hierarchical-fields-test
  (grant-permissions)
  (let [coll1 (fu/make-coll 1 "PROV1"
                            (fu/science-keywords sk1 sk2 sk3 sk4 sk5 sk6 sk7)
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms fu/FROM_KMS 2 2 1)
                            (fu/twod-coords "MISR")
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                       :ShortName "DOI/USGS/CMG/WHSC"})]
                             :LocationKeywords [(data-umm-spec/location-keyword {:Category "CONTINENT",
                                                                                 :Type "AFRICA",
                                                                                 :Subregion1 "CENTRAL AFRICA",
                                                                                 :Subregion2 "ANGOLA"})]})
        coll2 (fu/make-coll 2 "PROV1"
                            (fu/science-keywords sk1 sk2 sk3 sk4 sk5 sk6 sk7)
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms fu/FROM_KMS 2 2 1)
                            (fu/twod-coords "MISR")
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                       :ShortName "DOI/USGS/CMG/WHSC"})]
                             :LocationKeywords [(data-umm-spec/location-keyword {:Category "CONTINENT",
                                                                                 :Type "AFRICA",
                                                                                 :Subregion1 "CENTRAL AFRICA",
                                                                                 :Subregion2 "ANGOLA"})
                                                (data-umm-spec/location-keyword {:Category "CONTINENT",
                                                                                 :Type "ASIA",
                                                                                 :Subregion "WESTERN ASIA",
                                                                                 :Subregion2 "MIDDLE EAST",
                                                                                 :Subregion3 "GAZA STRIP"})
                                                (data-umm-spec/location-keyword {:Category "OTHER",
                                                                                 :Type "NOT IN KMS"})]})
        actual-facets (get-facet-results :hierarchical)]
    (is (= fr/expected-all-hierarchical-facets (:json-facets actual-facets)))))

;; The purpose of the test is to make sure when the same topic "Popular" is used under two different
;; categories, the flat facets correctly say 2 collections have the "Popular topic and the
;; hierarchical facets report just one collection with "Popular" below each category.
(deftest nested-duplicate-topics
  (grant-permissions)
  (let [coll1 (fu/make-coll 1 "PROV1" (fu/science-keywords sk3))
        coll2 (fu/make-coll 2 "PROV1" (fu/science-keywords sk5))
        expected-hierarchical-facets [{:field "project", :value-counts []}
                                      {:field "sensor", :value-counts []}
                                      {:field "two_d_coordinate_system_name", :value-counts []}
                                      {:field "processing_level_id", :value-counts []}
                                      {:field "detailed_variable", :value-counts []}
                                      {:field "data_centers", :subfields []}
                                      {:field "archive_centers", :subfields []}
                                      {:field "platforms", :subfields []}
                                      {:field "instruments", :subfields []}
                                      {:field "science_keywords",
                                       :subfields ["category"],
                                       :category
                                       [{:value "HURRICANE",
                                         :count 1,
                                         :subfields ["topic"],
                                         :topic
                                         [{:value "POPULAR",
                                           :count 1,
                                           :subfields ["term"],
                                           :term [{:value "UNIVERSAL", :count 1}]}]}
                                        {:value "TORNADO",
                                         :count 1,
                                         :subfields ["topic"],
                                         :topic
                                         [{:value "POPULAR",
                                           :count 1,
                                           :subfields ["term"],
                                           :term [{:value "EXTREME", :count 1}]}]}]}
                                      {:field "location_keywords", :subfields []}]
        expected-flat-facets [{:field "data_center", :value-counts []}
                              {:field "archive_center", :value-counts []}
                              {:field "project", :value-counts []}
                              {:field "platform", :value-counts []}
                              {:field "instrument", :value-counts []}
                              {:field "sensor", :value-counts []}
                              {:field "two_d_coordinate_system_name", :value-counts []}
                              {:field "processing_level_id", :value-counts []}
                              {:field "category",
                               :value-counts [["HURRICANE" 1] ["TORNADO" 1]]}
                              {:field "topic", :value-counts [["POPULAR" 2]]}
                              {:field "term",
                               :value-counts [["EXTREME" 1] ["UNIVERSAL" 1]]}
                              {:field "variable_level_1", :value-counts []}
                              {:field "variable_level_2", :value-counts []}
                              {:field "variable_level_3", :value-counts []}
                              {:field "detailed_variable", :value-counts []}]
        actual-hierarchical-facets (get-facet-results :hierarchical)
        actual-flat-facets (get-facet-results :flat)]
    (is (= expected-hierarchical-facets (:xml-facets actual-hierarchical-facets)))
    (is (= expected-hierarchical-facets (:json-facets actual-hierarchical-facets)))
    (is (= expected-flat-facets (:xml-facets actual-flat-facets)))
    (is (= expected-flat-facets (:json-facets actual-flat-facets)))))

(deftest empty-hierarchical-facets-test
  (let [expected-facets [{:field "project", :value-counts []}
                         {:field "sensor", :value-counts []}
                         {:field "two_d_coordinate_system_name", :value-counts []}
                         {:field "processing_level_id", :value-counts []}
                         {:field "detailed_variable", :value-counts []}
                         {:field "data_centers", :subfields []}
                         {:field "archive_centers", :subfields []}
                         {:field "platforms", :subfields []}
                         {:field "instruments", :subfields []}
                         {:field "science_keywords", :subfields []}
                         {:field "location_keywords", :subfields []}]
        actual-facets (get-facet-results :hierarchical)]
    (is (= expected-facets (:xml-facets actual-facets)))
    (is (= expected-facets (:json-facets actual-facets)))))

(deftest detailed-variable-test
  (grant-permissions)
  (let [coll1 (fu/make-coll 1 "PROV1" (fu/science-keywords sk8))
        expected-hierarchical-facets [{:field "project", :value-counts []}
                                      {:field "sensor", :value-counts []}
                                      {:field "two_d_coordinate_system_name", :value-counts []}
                                      {:field "processing_level_id", :value-counts []}
                                      {:field "detailed_variable",
                                       :value-counts [["DETAILED-NO-LEVEL2-OR-3" 1]]}
                                      {:field "data_centers", :subfields []}
                                      {:field "archive_centers", :subfields []}
                                      {:field "platforms", :subfields []}
                                      {:field "instruments", :subfields []}
                                      {:field "science_keywords",
                                       :subfields ["category"],
                                       :category
                                       [{:value "CATEGORY",
                                         :count 1,
                                         :subfields ["topic"],
                                         :topic
                                         [{:value "TOPIC",
                                           :count 1,
                                           :subfields ["term"],
                                           :term
                                           [{:value "TERM",
                                             :count 1,
                                             :subfields ["variable_level_1"],
                                             :variable_level_1 [{:value "V-L1", :count 1}]}]}]}]}
                                      {:field "location_keywords", :subfields []}]
        expected-flat-facets [{:field "data_center", :value-counts []}
                              {:field "archive_center", :value-counts []}
                              {:field "project", :value-counts []}
                              {:field "platform", :value-counts []}
                              {:field "instrument", :value-counts []}
                              {:field "sensor", :value-counts []}
                              {:field "two_d_coordinate_system_name", :value-counts []}
                              {:field "processing_level_id", :value-counts []}
                              {:field "category", :value-counts [["CATEGORY" 1]]}
                              {:field "topic", :value-counts [["TOPIC" 1]]}
                              {:field "term", :value-counts [["TERM" 1]]}
                              {:field "variable_level_1", :value-counts [["V-L1" 1]]}
                              {:field "variable_level_2", :value-counts []}
                              {:field "variable_level_3", :value-counts []}
                              {:field "detailed_variable",
                               :value-counts [["DETAILED-NO-LEVEL2-OR-3" 1]]}]
        actual-hierarchical-facets (get-facet-results :hierarchical)
        actual-flat-facets (get-facet-results :flat)]
    (is (= expected-hierarchical-facets (:xml-facets actual-hierarchical-facets)))
    (is (= expected-hierarchical-facets (:json-facets actual-hierarchical-facets)))
    (is (= expected-flat-facets (:xml-facets actual-flat-facets)))
    (is (= expected-flat-facets (:json-facets actual-flat-facets)))))

(deftest flat-facets-test
  (let [coll1 (fu/make-coll 1 "PROV1"
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms "A" 2 2 1)
                            (fu/twod-coords "MISR")
                            (fu/science-keywords sk1 sk4 sk5)
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER" "PROCESSOR"]
                                                                       :ShortName "Larc"})]})
        coll2 (fu/make-coll 2 "PROV2"
                            (fu/projects "proj3" "PROJ2")
                            (fu/platforms "B" 2 2 1)
                            (fu/science-keywords sk1 sk2 sk3)
                            (fu/processing-level-id "pl1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                       :ShortName "GSFC"})
                                           (data-umm-spec/data-center {:Roles ["PROCESSOR"]
                                                                       :ShortName "Proc"})]})
        coll3 (fu/make-coll 3 "PROV2"
                            (fu/platforms "A" 1 1 1)
                            (fu/twod-coords "MISR" "CALIPSO")
                            (fu/science-keywords sk5 sk6 sk7)
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                       :ShortName "Larc"})]})
        coll4 (fu/make-coll 4 "PROV1"
                            (fu/science-keywords sk3)
                            (fu/processing-level-id "PL2")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                       :ShortName "Larc"})]})

        coll5 (fu/make-coll 5 "PROV2"
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                       :ShortName "Not provided"})]})

        ;; Guests do not have permission to this collection so it will not appear in results
        coll6 (fu/make-coll 6 "PROV2"
                            (fu/projects "proj1")
                            (fu/platforms "A" 1 1 1)
                            (fu/twod-coords "MISR")
                            (fu/science-keywords sk1)
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                       :ShortName "Not provided"})]})

        ;; Need a dif collection because echo10 does not have a way to specify distribution centers
        coll7 (make-dif-coll 7 "PROV1"
                             (fu/science-keywords sk1)
                             {:DataCenters [(data-umm-spec/data-center {:Roles ["DISTRIBUTOR"]
                                                                        :ShortName "Dist"})]})

        all-colls [coll1 coll2 coll3 coll4 coll5 coll6 coll7]]

    (index/wait-until-indexed)
    (grant-permissions)
    (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
    (index/wait-until-indexed)

   (testing "invalid include-facets"
     (is (= {:errors ["Collection parameter include_facets must take value of true, false, or v2, but was [foo]"] :status 400}
            (search/find-refs :collection {:include-facets "foo"}))))

   (testing "retreving all facets in different formats"
     (let [expected-facets [{:field "data_center"
                             :value-counts [["Larc" 3] ["Dist" 1] ["GSFC" 1] ["Proc" 1]]}
                            {:field "archive_center"
                             :value-counts [["Larc" 3] ["Dist" 1] ["GSFC" 1]]}
                            {:field "project"
                             :value-counts [["PROJ2" 2] ["proj1" 1] ["proj3" 1]]}
                            {:field "platform"
                             :value-counts [["A-p0" 2] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                            {:field "instrument" ; Instruments now include sensors as child instruments
                             :value-counts [["A-p0-i0" 2]
                                            ["A-p0-i0-s0" 2]
                                            ["A-p0-i1" 1]
                                            ["A-p0-i1-s0" 1]
                                            ["A-p1-i0" 1]
                                            ["A-p1-i0-s0" 1]
                                            ["A-p1-i1" 1]
                                            ["A-p1-i1-s0" 1]
                                            ["B-p0-i0" 1]
                                            ["B-p0-i0-s0" 1]
                                            ["B-p0-i1" 1]
                                            ["B-p0-i1-s0" 1]
                                            ["B-p1-i0" 1]
                                            ["B-p1-i0-s0" 1]
                                            ["B-p1-i1" 1]
                                            ["B-p1-i1-s0" 1]]}
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
                             :value-counts [["MISR" 2] ["CALIPSO" 1]]}
                            {:field "processing_level_id"
                             :value-counts [["PL1" 2] ["PL2" 1] ["pl1" 1]]}
                            {:field "category"
                             :value-counts [["CAT1" 3]
                                            ["HURRICANE" 3]
                                            ["TORNADO" 2]
                                            ["UPCASE" 1]]}
                            {:field "topic"
                             :value-counts [["POPULAR" 4] ["TOPIC1" 3] ["COOL" 2]]}
                            {:field "term"
                             :value-counts [["EXTREME" 3]
                                            ["TERM1" 3]
                                            ["UNIVERSAL" 2]
                                            ["MILD" 1]
                                            ["TERM4" 1]]}
                            {:field "variable_level_1"
                             :value-counts [["LEVEL1-1" 3] ["LEVEL2-1" 1] ["UNIVERSAL" 1]]}
                            {:field "variable_level_2"
                             :value-counts [["LEVEL1-2" 3] ["LEVEL2-2" 1]]}
                            {:field "variable_level_3"
                             :value-counts [["LEVEL1-3" 3] ["LEVEL2-3" 1]]}
                            {:field "detailed_variable"
                             :value-counts [["DETAIL1" 3] ["UNIVERSAL" 1]]}]]
       (testing "refs"
         (is (= expected-facets
                (:facets (search/find-refs :collection {:include-facets true})))))

       (testing "refs echo-compatible true"
         (is (= (remove #(= "data_center" (:field %)) expected-facets)
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
         (is (= (sort-by :field (remove #(= "data_center" (:field %)) expected-facets))
                (sort-by :field (search/find-concepts-json :collection {:include-facets true
                                                                        :echo-compatible true})))))))

   (testing "Search conditions narrow reduce facet values found"
     (testing "search finding two documents"
       (let [expected-facets [{:field "data_center"
                               :value-counts [["GSFC" 1] ["Larc" 1] ["Proc" 1]]}
                              {:field "archive_center"
                               :value-counts [["GSFC" 1] ["Larc" 1]]}
                              {:field "project"
                               :value-counts [["PROJ2" 2] ["proj1" 1] ["proj3" 1]]}
                              {:field "platform"
                               :value-counts [["A-p0" 1] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                              {:field "instrument" ; Instruments now include sensors as child instruments
                               :value-counts [["A-p0-i0" 1]
                                              ["A-p0-i0-s0" 1]
                                              ["A-p0-i1" 1]
                                              ["A-p0-i1-s0" 1]
                                              ["A-p1-i0" 1]
                                              ["A-p1-i0-s0" 1]
                                              ["A-p1-i1" 1]
                                              ["A-p1-i1-s0" 1]
                                              ["B-p0-i0" 1]
                                              ["B-p0-i0-s0" 1]
                                              ["B-p0-i1" 1]
                                              ["B-p0-i1-s0" 1]
                                              ["B-p1-i0" 1]
                                              ["B-p1-i0-s0" 1]
                                              ["B-p1-i1" 1]
                                              ["B-p1-i1-s0" 1]]}
                              {:field "sensor"
                               :value-counts [["A-p0-i0-s0" 1]
                                              ["A-p0-i1-s0" 1]
                                              ["A-p1-i0-s0" 1]
                                              ["A-p1-i1-s0" 1]
                                              ["B-p0-i0-s0" 1]
                                              ["B-p0-i1-s0" 1]
                                              ["B-p1-i0-s0" 1]
                                              ["B-p1-i1-s0" 1]]}
                              {:field "two_d_coordinate_system_name" :value-counts [["MISR" 1]]}
                              {:field "processing_level_id"
                               :value-counts [["PL1" 1] ["pl1" 1]]}
                              {:field "category"
                               :value-counts [["CAT1" 2] ["HURRICANE" 2] ["TORNADO" 1]]}
                              {:field "topic"
                               :value-counts [["POPULAR" 2] ["TOPIC1" 2] ["COOL" 1]]}
                              {:field "term"
                               :value-counts [["EXTREME" 2] ["TERM1" 2] ["TERM4" 1] ["UNIVERSAL" 1]]}
                              {:field "variable_level_1"
                               :value-counts [["LEVEL1-1" 2] ["LEVEL2-1" 1] ["UNIVERSAL" 1]]}
                              {:field "variable_level_2"
                               :value-counts [["LEVEL1-2" 2] ["LEVEL2-2" 1]]}
                              {:field "variable_level_3"
                               :value-counts [["LEVEL1-3" 2] ["LEVEL2-3" 1]]}
                              {:field "detailed_variable"
                               :value-counts [["DETAIL1" 2] ["UNIVERSAL" 1]]}]]
         (is (= expected-facets
                (:facets (search/find-refs :collection {:include-facets true
                                                        :project "PROJ2"}))))))

     (testing "AND conditions narrow facets via AND not OR"
       (let [expected-facets [{:field "data_center"
                               :value-counts [["GSFC" 1] ["Proc" 1]]}
                              {:field "archive_center"
                               :value-counts [["GSFC" 1]]}
                              {:field "project" :value-counts [["PROJ2" 1] ["proj3" 1]]}
                              {:field "platform" :value-counts [["B-p0" 1] ["B-p1" 1]]}
                              {:field "instrument" ; Instruments now include sensors as child instruments
                               :value-counts [["B-p0-i0" 1]
                                              ["B-p0-i0-s0" 1]
                                              ["B-p0-i1" 1]
                                              ["B-p0-i1-s0" 1]
                                              ["B-p1-i0" 1]
                                              ["B-p1-i0-s0" 1]
                                              ["B-p1-i1" 1]
                                              ["B-p1-i1-s0" 1]]}
                              {:field "sensor"
                               :value-counts [["B-p0-i0-s0" 1]
                                              ["B-p0-i1-s0" 1]
                                              ["B-p1-i0-s0" 1]
                                              ["B-p1-i1-s0" 1]]}
                              {:field "two_d_coordinate_system_name" :value-counts []}
                              {:field "processing_level_id" :value-counts [["pl1" 1]]}
                              {:field "category" :value-counts [["CAT1" 1] ["HURRICANE" 1]]}
                              {:field "topic" :value-counts [["POPULAR" 1] ["TOPIC1" 1]]}
                              {:field "term" :value-counts [["EXTREME" 1]
                                                            ["TERM1" 1]
                                                            ["UNIVERSAL" 1]]}
                              {:field "variable_level_1":value-counts [["LEVEL1-1" 1]
                                                                       ["LEVEL2-1" 1]]}
                              {:field "variable_level_2" :value-counts [["LEVEL1-2" 1]
                                                                        ["LEVEL2-2" 1]]}
                              {:field "variable_level_3" :value-counts [["LEVEL1-3" 1]
                                                                        ["LEVEL2-3" 1]]}
                              {:field "detailed_variable" :value-counts [["DETAIL1" 1]
                                                                         ["UNIVERSAL" 1]]}]]


         (is (= expected-facets
                (:facets (search/find-refs :collection {:include-facets true
                                                        :project ["PROJ2" "proj3"]
                                                        "options[project][and]" true}))))))

     (testing "search finding one document"
       (let [expected-facets [{:field "data_center" :value-counts [["Larc" 1]]}
                              {:field "archive_center" :value-counts [["Larc" 1]]}
                              {:field "project" :value-counts []}
                              {:field "platform" :value-counts []}
                              {:field "instrument" :value-counts []}
                              {:field "sensor" :value-counts []}
                              {:field "two_d_coordinate_system_name" :value-counts []}
                              {:field "processing_level_id" :value-counts [["PL2" 1]]}
                              {:field "category" :value-counts [["HURRICANE" 1]]}
                              {:field "topic" :value-counts [["POPULAR" 1]]}
                              {:field "term" :value-counts [["UNIVERSAL" 1]]}
                              {:field "variable_level_1" :value-counts []}
                              {:field "variable_level_2" :value-counts []}
                              {:field "variable_level_3" :value-counts []}
                              {:field "detailed_variable" :value-counts []}]]
         (is (= expected-facets
                (:facets (search/find-refs :collection {:include-facets true
                                                        :processing-level-id "PL2"}))))))

     (let [empty-facets [{:field "data_center" :value-counts []}
                         {:field "archive_center" :value-counts []}
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

(deftest large-hierarchy-science-keyword-test
  (grant-permissions)
  (let [science-keywords (for [n (range 25)]
                           (fu/generate-science-keywords n))
        _ (fu/make-coll 1 "PROV1" {:ScienceKeywords science-keywords})
        categories (->> (get-facet-results :hierarchical)
                        :json-facets
                        (filter #(= "science_keywords" (:field %)))
                        first
                        :category
                        (map :value))]
    ;; Make sure that all 25 individual categories are returned in the facets
    (is (= (set (map #(str/upper-case (:Category %)) science-keywords))
           (set categories)))))

(deftest platform-missing-fields-test
  (grant-permissions)
  ;; Test that if the platforms do not exist in KMS, they will still be returned, but with a value
  ;; of "Not Provided" for all of the values in the hierarchy other than short name.
  (fu/make-coll 1 "PROV1" (fu/platforms "Platform" 2 2 1))
  ;; Test that even with a nil sub-category the platform will still be returned, but with a
  ;; value of "Not Provided" for the sub-category
  (fu/make-coll 2 "PROV1" {:Platforms [(data-umm-spec/platform {:ShortName "A340-600"})]})
  (let [expected-platforms [{:basis [{:category [{:value "Jet", :count 1}],
                                      :value "Air-based Platforms",
                                      :subfields ["category"],
                                      :count 1}],
                             :field "platforms",
                             :subfields ["basis"]}]
        actual-platforms (->> (get-facet-results :hierarchical)
                              :json-facets
                              (filter #(= "platforms" (:field %))))]
    (is (= expected-platforms actual-platforms))))

(deftest instrument-missing-fields-test
  (grant-permissions)
  ;; Test that if the instruments do not exist in KMS, they will still be returned, but with a value
  ;; of "Not Provided" for all of the values in the hierarchy other than short name.
  (fu/make-coll 1 "PROV1" (fu/platforms "instrument-test" 2 2 1))
  ;; Test that even with a nil type and sub-type the instrument will still be returned, but with a
  ;; value of "Not Provided" for those fields.
  (fu/make-coll 2 "PROV1" {:Platforms [(data-umm-spec/platform
                                         {:Instruments [(data-umm-spec/instrument {:ShortName "ADS"})]})]})
  (let [expected-instruments [{:field "instruments",
                               :subfields ["category"],
                               :category
                               [{:value "Not Provided",
                                 :count 1,
                                 :subfields ["class"],
                                 :class
                                 [{:value "Not Provided",
                                   :count 1,
                                   :subfields ["type"],
                                   :type
                                   [{:value "Not Provided",
                                     :count 1,
                                     :subfields ["subtype"],
                                     :subtype
                                     [{:value "Not Provided", ; Instruments now include sensors as child instruments
                                       :count 1,
                                       :subfields ["short_name"],
                                       :short_name
                                       [{:value "instrument-test-p0-i0",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p0-i0-s0",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p0-i1",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p0-i1-s0",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p1-i0",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p1-i0-s0",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p1-i1",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name
                                         [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p1-i1-s0",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}]}]}]}]}
                                {:value "In Situ/Laboratory Instruments",
                                 :count 1,
                                 :subfields ["class"],
                                 :class
                                 [{:value "Chemical Meters/Analyzers",
                                   :count 1,
                                   :subfields ["type"],
                                   :type
                                   [{:value "Not Provided",
                                     :count 1,
                                     :subfields ["subtype"],
                                     :subtype
                                     [{:value "Not Provided",
                                       :count 1,
                                       :subfields ["short_name"],
                                       :short_name
                                       [{:value "ADS",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name
                                         [{:value "Automated DNA Sequencer",
                                           :count 1}]}]}]}]}]}]}]
        actual-instruments (->> (get-facet-results :hierarchical)
                                :json-facets
                                (filter #(= "instruments" (:field %))))]
    (is (= expected-instruments actual-instruments))))

(deftest archive-center-missing-fields-test
  (grant-permissions)
  ;; Test that if the archive-centers do not exist in KMS, they will still be returned, but with a
  ;; value of "Not Provided" for all of the values in the hierarchy other than short name.
  (fu/make-coll 1 "PROV1" {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                     :ShortName "Larc"})]})
  ;; Test that even with a nil Level-1, Level-2, and Level-3 the archive-center will still be
  ;; returned, but with a value of "Not Provided" for each nil field
  (fu/make-coll 2 "PROV1" {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                     :ShortName "ESA/ED"})]})
  (let [expected-archive-centers [{:field "archive_centers",
                                   :subfields ["level_0"],
                                   :level_0
                                   [{:value "CONSORTIA/INSTITUTIONS",
                                     :count 1,
                                     :subfields ["level_1"],
                                     :level_1
                                     [{:value "Not Provided",
                                       :count 1,
                                       :subfields ["level_2"],
                                       :level_2
                                       [{:value "Not Provided",
                                         :count 1,
                                         :subfields ["level_3"],
                                         :level_3
                                         [{:value "Not Provided",
                                           :count 1,
                                           :subfields ["short_name"],
                                           :short_name
                                           [{:value "ESA/ED",
                                             :count 1,
                                             :subfields ["long_name"],
                                             :long_name
                                             [{:value
                                               "Educational Office, Ecological Society of America",
                                               :count 1}]}]}]}]}]}
                                    {:value "Not Provided",
                                     :count 1,
                                     :subfields ["level_1"],
                                     :level_1
                                     [{:value "Not Provided",
                                       :count 1,
                                       :subfields ["level_2"],
                                       :level_2
                                       [{:value "Not Provided",
                                         :count 1,
                                         :subfields ["level_3"],
                                         :level_3
                                         [{:value "Not Provided",
                                           :count 1,
                                           :subfields ["short_name"],
                                           :short_name
                                           [{:value "Larc",
                                             :count 1,
                                             :subfields ["long_name"],
                                             :long_name
                                             [{:value "Not Provided", :count 1}]}]}]}]}]}]}]
        actual-archive-centers (->> (get-facet-results :hierarchical)
                                    :json-facets
                                    (filter #(= "archive_centers" (:field %))))]
    (is (= expected-archive-centers actual-archive-centers))))

(deftest leading-and-trailing-whitespace-facets-test
  (let [sk1-leading-ws (umm-spec-common/science-keyword {:Category "  Cat1"
                                                         :Topic " Topic1"
                                                         :Term " Term1"
                                                         :VariableLevel1 "   Level1-1"
                                                         :VariableLevel2 " Level1-2"
                                                         :VariableLevel3 " Level1-3"
                                                         :DetailedVariable " Detail1"})

        sk1-trailing-ws (umm-spec-common/science-keyword {:Category "Cat1   "
                                                          :Topic "Topic1 "
                                                          :Term "Term1 "
                                                          :VariableLevel1 "Level1-1     "
                                                          :VariableLevel2 "Level1-2 "
                                                          :VariableLevel3 "Level1-3 "
                                                          :DetailedVariable "Detail1 "})

        sk1-leading-and-trailing-ws (umm-spec-common/science-keyword {:Category "    Cat1 "
                                                                      :Topic " Topic1 "
                                                                      :Term " Term1 "
                                                                      :VariableLevel1 " Level1-1 "
                                                                      :VariableLevel2 " Level1-2 "
                                                                      :VariableLevel3 " Level1-3    "
                                                                      :DetailedVariable "  Detail1 "})

        coll1 (fu/make-coll 1 "PROV1"
                            (fu/projects "proj1" "PROJ2")
                            (fu/platforms "A" 2 2 1)
                            (fu/twod-coords "MISR")
                            (fu/science-keywords sk1 sk4 sk5)
                            (fu/processing-level-id "PL1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER" "PROCESSOR"]
                                                                       :ShortName "Larc"})]})
        coll2 (fu/make-coll 2 "PROV2"
                            (fu/projects "proj3" "PROJ2")
                            (fu/platforms "B" 2 2 1)
                            (fu/science-keywords sk1 sk2 sk3)
                            (fu/processing-level-id "pl1")
                            {:DataCenters [(data-umm-spec/data-center {:Roles ["ARCHIVER"]
                                                                       :ShortName "GSFC"})
                                           (data-umm-spec/data-center {:Roles ["PROCESSOR"]
                                                                       :ShortName "Proc"})]})
        coll3 (fu/make-coll 3 "PROV2"
                            (fu/science-keywords sk1-leading-ws))

        coll4 (fu/make-coll 4 "PROV2"
                            (fu/science-keywords sk1-trailing-ws))

        coll5 (fu/make-coll 5 "PROV2"
                            (fu/platforms " A" 1)
                            (fu/projects "proj3 " " PROJ2")
                            (fu/science-keywords sk1-leading-and-trailing-ws))]

    (index/wait-until-indexed)
    (grant-permissions)
    (ingest/reindex-collection-permitted-groups (tc/echo-system-token))
    (index/wait-until-indexed)

    (testing "retreving all facets in refs formats"
      (let [expected-facets [{:field "data_center",
                              :value-counts [["GSFC" 1] ["Larc" 1] ["Proc" 1]]}
                             {:field "archive_center",
                              :value-counts [["GSFC" 1] ["Larc" 1]]}
                             {:field "project",
                              :value-counts [["PROJ2" 3] ["proj3" 2] ["proj1" 1]]}
                             {:field "platform", :value-counts
                              [["A-p0" 2] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                             {:field "instrument", ; Instruments now include sensors as child instruments
                              :value-counts
                              [["A-p0-i0" 1]
                               ["A-p0-i0-s0" 1]
                               ["A-p0-i1" 1]
                               ["A-p0-i1-s0" 1]
                               ["A-p1-i0" 1]
                               ["A-p1-i0-s0" 1]
                               ["A-p1-i1" 1]
                               ["A-p1-i1-s0" 1]
                               ["B-p0-i0" 1]
                               ["B-p0-i0-s0" 1]
                               ["B-p0-i1" 1]
                               ["B-p0-i1-s0" 1]
                               ["B-p1-i0" 1]
                               ["B-p1-i0-s0" 1]
                               ["B-p1-i1" 1]
                               ["B-p1-i1-s0" 1]]}
                             {:field "sensor",
                              :value-counts
                              [["A-p0-i0-s0" 1]
                               ["A-p0-i1-s0" 1]
                               ["A-p1-i0-s0" 1]
                               ["A-p1-i1-s0" 1]
                               ["B-p0-i0-s0" 1]
                               ["B-p0-i1-s0" 1]
                               ["B-p1-i0-s0" 1]
                               ["B-p1-i1-s0" 1]]}
                             {:field "two_d_coordinate_system_name",
                              :value-counts [["MISR" 1]]}
                             {:field "processing_level_id",
                              :value-counts [["PL1" 1] ["pl1" 1]]}
                             {:field "category",
                              :value-counts
                              [["CAT1" 4]
                               ["HURRICANE" 2]
                               ["TORNADO" 1]]}
                             {:field "topic",
                              :value-counts
                              [["TOPIC1" 4]
                               ["POPULAR" 2]
                               ["COOL" 1]]}
                             {:field "term",
                              :value-counts
                              [["TERM1" 4]
                               ["EXTREME" 2]
                               ["TERM4" 1]
                               ["UNIVERSAL" 1]]}
                             {:field "variable_level_1",
                              :value-counts
                              [["LEVEL1-1" 4]
                               ["LEVEL2-1" 1]
                               ["UNIVERSAL" 1]]}
                             {:field "variable_level_2",
                              :value-counts
                              [["LEVEL1-2" 4]
                               ["LEVEL2-2" 1]]}
                             {:field "variable_level_3",
                              :value-counts
                              [["LEVEL1-3" 4]
                               ["LEVEL2-3" 1]]}
                             {:field "detailed_variable",
                              :value-counts
                              [["DETAIL1" 4]
                               ["UNIVERSAL" 1]]}]]

        (is (= expected-facets
               (:facets (search/find-refs :collection {:include-facets true}))))))))

(deftest flat-facet-case-insensitivity-test
  (grant-permissions)
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                                :platforms [(dc/platform
                                                              {:short-name "smap"
                                                               :instruments [(dc/instrument {:short-name "atm"})]})]
                                                :organizations [(dc/org :archive-center "OR-STATE/eoarc")]}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"
                                                :platforms [(dc/platform
                                                              {:short-name "sMaP"
                                                               :instruments [(dc/instrument {:short-name "aTM"})]})]
                                                :organizations [(dc/org :archive-center "or-state/EOARC")]}))
        _ (index/wait-until-indexed)
        facet-results (:facets (search/find-refs :collection {:include-facets true}))]
    (are [value-counts field]
      (= value-counts (:value-counts (first (filter #(= field (:field %)) facet-results))))
      [["SMAP" 2]] "platform"
      [["ATM" 2]]  "instrument"
      [["OR-STATE/EOARC" 2]] "archive_center")))
