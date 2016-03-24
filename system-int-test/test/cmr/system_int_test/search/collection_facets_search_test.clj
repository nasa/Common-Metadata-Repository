(ns cmr.system-int-test.search.collection-facets-search-test
  "This tests the retrieving facets when searching for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.core :as d]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
            [clojure.string :as str]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                                          {:grant-all-search? false}))

(defn- make-coll
  "Helper for creating and ingesting an ECHO10 collection"
  [n prov & attribs]
  (d/ingest prov (dc/collection (apply merge {:entry-title (str "coll" n)} attribs))))

(defn- make-dif-coll
  "Helper for creating and ingesting a DIF collection"
  [n prov & attribs]
  (d/ingest prov (dc/collection (apply merge {:entry-title (str "coll" n)} attribs))
            {:format :dif}))

;; Attrib functions - These are helpers for creating maps with collection attributes
(defn- projects
  [& project-names]
  {:projects (apply dc/projects project-names)})

(def platform-short-names
  "List of platform short names that exist in the test KMS hierarchy. Note we are testing case
  insensivity of the short name. DIADEM-1D is the actual short-name value in KMS, but we expect
  diadem-1D to match."
  ["diadem-1D" "DMSP 5B/F3" "A340-600" "SMAP"])

(def instrument-short-names
  "List of instrument short names that exist in the test KMS hierarchy. Note we are testing case
  insensivity of the short name. LVIS is the actual short-name value in KMS, but we expect
  lVIs to match."
  ["ATM" "lVIs" "ADS" "SMAP L-BAND RADIOMETER"])

(def FROM_KMS
  "Constant indicating that the short name for the field should be a short name found in KMS."
  "FROM_KMS")

(defn- platforms
  "Creates a specified number of platforms each with a certain number of instruments and sensors"
  ([prefix num-platforms]
   (platforms prefix num-platforms 0 0))
  ([prefix num-platforms num-instruments]
   (platforms prefix num-platforms num-instruments 0))
  ([prefix num-platforms num-instruments num-sensors]
   {:platforms
    (for [pn (range 0 num-platforms)
          :let [platform-name (str prefix "-p" pn)]]
      (dc/platform
        {:short-name (if (= FROM_KMS prefix)
                       (or (get platform-short-names pn) platform-name)
                       platform-name)
         :long-name platform-name
         :instruments
         (for [in (range 0 num-instruments)
               :let [instrument-name (str platform-name "-i" in)]]
           (dc/instrument
             {:short-name (if (= FROM_KMS prefix)
                            (or (get instrument-short-names in) instrument-name)
                            instrument-name)
              :sensors (for [sn (range 0 num-sensors)
                             :let [sensor-name (str instrument-name "-s" sn)]]
                         (dc/sensor {:short-name sensor-name}))}))}))}))

(defn- twod-coords
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

(def sk8 (dc/science-keyword {:category "Category"
                              :topic "Topic"
                              :term "Term"
                              :variable-level-1 "V-L1"
                              :detailed-variable "Detailed-No-Level2-or-3"}))

(defn- science-keywords
  [& sks]
  {:science-keywords sks})

(defn- processing-level-id
  [id]
  {:processing-level-id id})

(defn- generate-science-keywords
  "Generate science keywords based on a unique number."
  [n]
  (dc/science-keyword {:category (str "Cat-" n)
                       :topic (str "Topic-" n)
                       :term (str "Term-" n)
                       :variable-level-1 "Level1-1"
                       :variable-level-2 "Level1-2"
                       :variable-level-3 "Level1-3"
                       :detailed-variable (str "Detail-" n)}))

(defn- grant-permissions
  "Grant permissions to all collections in PROV1 and a subset of collections in PROV2"
  []
  (e/grant-guest (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-guest (s/context)
                 (e/coll-catalog-item-id "provguid2" (e/coll-id ["coll2" "coll3" "coll5"]))))

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

(def expected-all-hierarchical-facets
  "Expected value for the all-hierarchical-fields-test."
  [{:field "project", :value-counts [["PROJ2" 2] ["proj1" 2]]}
   {:field "sensor",
    :value-counts
    [["FROM_KMS-p0-i0-s0" 2]
     ["FROM_KMS-p0-i1-s0" 2]
     ["FROM_KMS-p1-i0-s0" 2]
     ["FROM_KMS-p1-i1-s0" 2]]}
   {:field "two_d_coordinate_system_name",
    :value-counts [["Alpha" 2]]}
   {:field "processing_level_id", :value-counts [["PL1" 2]]}
   {:field "detailed_variable",
    :value-counts [["DETAIL1" 2] ["UNIVERSAL" 2]]}
   {:field "data_centers",
    :subfields ["level_0"],
    :level_0
    [{:value "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES",
      :count 2,
      :subfields ["level_1"],
      :level_1
      [{:value "DOI",
        :count 2,
        :subfields ["level_2"],
        :level_2
        [{:value "USGS",
          :count 2,
          :subfields ["level_3"],
          :level_3
          [{:value "Added level 3 value",
            :count 2,
            :subfields ["short_name"],
            :short_name
            [{:value "DOI/USGS/CMG/WHSC",
              :count 2,
              :subfields ["long_name"],
              :long_name
              [{:value
                "Woods Hole Science Center, Coastal and Marine Geology, U.S. Geological Survey, U.S. Department of the Interior",
                :count 2}]}]}]}]}]}]}
   {:field "archive_centers",
    :subfields ["level_0"],
    :level_0
    [{:value "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES",
      :count 2,
      :subfields ["level_1"],
      :level_1
      [{:value "DOI",
        :count 2,
        :subfields ["level_2"],
        :level_2
        [{:value "USGS",
          :count 2,
          :subfields ["level_3"],
          :level_3
          [{:value "Added level 3 value",
            :count 2,
            :subfields ["short_name"],
            :short_name
            [{:value "DOI/USGS/CMG/WHSC",
              :count 2,
              :subfields ["long_name"],
              :long_name
              [{:value
                "Woods Hole Science Center, Coastal and Marine Geology, U.S. Geological Survey, U.S. Department of the Interior",
                :count 2}]}]}]}]}]}]}
   {:field "platforms",
    :subfields ["category"],
    :category
    [{:value "Earth Observation Satellites",
      :count 2,
      :subfields ["series_entity"],
      :series_entity
      [{:value "DIADEM",
        :count 2,
        :subfields ["short_name"],
        :short_name
        [{:value "DIADEM-1D",
          :count 2,
          :subfields ["long_name"],
          :long_name [{:value "Not Provided", :count 2}]}]}
       {:value
        "DMSP (Defense Meteorological Satellite Program)",
        :count 2,
        :subfields ["short_name"],
        :short_name
        [{:value "DMSP 5B/F3",
          :count 2,
          :subfields ["long_name"],
          :long_name
          [{:value
            "Defense Meteorological Satellite Program-F3",
            :count 2}]}]}]}]}
   {:field "instruments",
    :subfields ["category"],
    :category
    [{:value "Earth Remote Sensing Instruments",
      :count 2,
      :subfields ["class"],
      :class
      [{:value "Active Remote Sensing",
        :count 2,
        :subfields ["type"],
        :type
        [{:value "Altimeters",
          :count 2,
          :subfields ["subtype"],
          :subtype
          [{:value "Lidar/Laser Altimeters",
            :count 2,
            :subfields ["short_name"],
            :short_name
            [{:value "ATM",
              :count 2,
              :subfields ["long_name"],
              :long_name
              [{:value "Airborne Topographic Mapper",
                :count 2}]}
             {:value "LVIS",
              :count 2,
              :subfields ["long_name"],
              :long_name
              [{:value "Land, Vegetation, and Ice Sensor",
                :count 2}]}]}]}]}]}]}
   {:field "science_keywords",
    :subfields ["category"],
    :category
    [{:value "HURRICANE",
      :count 2,
      :subfields ["topic"],
      :topic
      [{:value "POPULAR",
        :count 2,
        :subfields ["term"],
        :term
        [{:value "EXTREME",
          :count 2,
          :subfields ["variable_level_1"],
          :variable_level_1
          [{:value "LEVEL2-1",
            :count 2,
            :subfields ["variable_level_2"],
            :variable_level_2
            [{:value "LEVEL2-2",
              :count 2,
              :subfields ["variable_level_3"],
              :variable_level_3
              [{:value "LEVEL2-3", :count 2}]}]}]}
         {:value "UNIVERSAL", :count 2}]}
       {:value "COOL",
        :count 2,
        :subfields ["term"],
        :term
        [{:value "TERM4",
          :count 2,
          :subfields ["variable_level_1"],
          :variable_level_1
          [{:value "UNIVERSAL", :count 2}]}]}]}
     {:value "UPCASE",
      :count 2,
      :subfields ["topic"],
      :topic
      [{:value "COOL",
        :count 2,
        :subfields ["term"],
        :term [{:value "MILD", :count 2}]}
       {:value "POPULAR",
        :count 2,
        :subfields ["term"],
        :term [{:value "MILD", :count 2}]}]}
     {:value "CAT1",
      :count 2,
      :subfields ["topic"],
      :topic
      [{:value "TOPIC1",
        :count 2,
        :subfields ["term"],
        :term
        [{:value "TERM1",
          :count 2,
          :subfields ["variable_level_1"],
          :variable_level_1
          [{:value "LEVEL1-1",
            :count 2,
            :subfields ["variable_level_2"],
            :variable_level_2
            [{:value "LEVEL1-2",
              :count 2,
              :subfields ["variable_level_3"],
              :variable_level_3
              [{:value "LEVEL1-3", :count 2}]}]}]}]}]}
     {:value "TORNADO",
      :count 2,
      :subfields ["topic"],
      :topic
      [{:value "POPULAR",
        :count 2,
        :subfields ["term"],
        :term [{:value "EXTREME", :count 2}]}]}]}])

(deftest all-hierarchical-fields-test
  (grant-permissions)
  (let [coll1 (make-coll 1 "PROV1"
                         (science-keywords sk1 sk2 sk3 sk4 sk5 sk6 sk7)
                         (projects "proj1" "PROJ2")
                         (platforms FROM_KMS 2 2 1)
                         (twod-coords "Alpha")
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "DOI/USGS/CMG/WHSC")]})
        coll2 (make-coll 2 "PROV1"
                         (science-keywords sk1 sk2 sk3 sk4 sk5 sk6 sk7)
                         (projects "proj1" "PROJ2")
                         (platforms FROM_KMS 2 2 1)
                         (twod-coords "Alpha")
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "DOI/USGS/CMG/WHSC")]})
        actual-facets (get-facet-results :hierarchical)]
    (is (= expected-all-hierarchical-facets (:xml-facets actual-facets)))
    (is (= expected-all-hierarchical-facets (:json-facets actual-facets)))))

;; The purpose of the test is to make sure when the same topic "Popular" is used under two different
;; categories, the flat facets correctly say 2 collections have the "Popular topic and the
;; hierarchical facets report just one collection with "Popular" below each category.
(deftest nested-duplicate-topics
  (grant-permissions)
  (let [coll1 (make-coll 1 "PROV1" (science-keywords sk3))
        coll2 (make-coll 2 "PROV1" (science-keywords sk5))
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
                                           :term [{:value "EXTREME", :count 1}]}]}]}]
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
                         {:field "science_keywords", :subfields []}]
        actual-facets (get-facet-results :hierarchical)]
    (is (= expected-facets (:xml-facets actual-facets)))
    (is (= expected-facets (:json-facets actual-facets)))))

(deftest detailed-variable-test
  (grant-permissions)
  (let [coll1 (make-coll 1 "PROV1" (science-keywords sk8))
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
                                             :variable_level_1 [{:value "V-L1", :count 1}]}]}]}]}]
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
  (grant-permissions)
  (let [coll1 (make-coll 1 "PROV1"
                         (projects "proj1" "PROJ2")
                         (platforms "A" 2 2 1)
                         (twod-coords "Alpha")
                         (science-keywords sk1 sk4 sk5)
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "Larc")
                                          (dc/org :processing-center "Larc")]})
        coll2 (make-coll 2 "PROV2"
                         (projects "proj3" "PROJ2")
                         (platforms "B" 2 2 1)
                         (science-keywords sk1 sk2 sk3)
                         (processing-level-id "pl1")
                         {:organizations [(dc/org :archive-center "GSFC")
                                          (dc/org :processing-center "Proc")]})
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

        ;; Need a dif collection because echo10 does not have a way to specify distribution centers
        coll7 (make-dif-coll 7 "PROV1"
                             (science-keywords sk1)
                             {:organizations [(dc/org :distribution-center "Dist")]})

        all-colls [coll1 coll2 coll3 coll4 coll5 coll6 coll7]]

    (index/wait-until-indexed)

    (testing "invalid include-facets"
      (is (= {:errors ["Parameter include_facets must take value of true, false, or unset, but was [foo]"] :status 400}
             (search/find-refs :collection {:include-facets "foo"})))
      (is (= {:errors ["Parameter [include_facets] was not recognized."] :status 400}
             (search/find-refs :granule {:include-facets true}))))

    (testing "retreving all facets in different formats"
      (let [expected-facets [{:field "data_center"
                              :value-counts [["Larc" 3] ["Dist" 1] ["GSFC" 1] ["Proc" 1]]}
                             {:field "archive_center"
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
                               {:field "two_d_coordinate_system_name" :value-counts [["alpha" 1]]}
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
                           (generate-science-keywords n))
        _ (make-coll 1 "PROV1" {:science-keywords science-keywords})
        categories (->> (get-facet-results :hierarchical)
                        :json-facets
                        (filter #(= "science_keywords" (:field %)))
                        first
                        :category
                        (map :value))]
    ;; Make sure that all 25 individual categories are returned in the facets
    (is (= (set (map #(str/upper-case (:category %)) science-keywords))
           (set categories)))))

(deftest platform-missing-fields-test
  (grant-permissions)
  ;; Test that if the platforms do not exist in KMS, they will still be returned, but with a value
  ;; of "Not Provided" for all of the values in the hierarchy other than short name.
  (make-coll 1 "PROV1" (platforms "Platform" 2 2 1))
  ;; Test that even with a nil series-entity the platform will still be returned, but with a
  ;; value of "Not Provided" for the series-entity
  (make-coll 2 "PROV1" {:platforms [(dc/platform {:short-name "A340-600"})]})
  (let [expected-platforms [{:subfields ["category"],
                             :field "platforms",
                             :category
                             [{:count 1,
                               :value "Not Provided",
                               :subfields ["series_entity"],
                               :series_entity
                               [{:count 1,
                                 :value "Not Provided",
                                 :subfields ["short_name"],
                                 :short_name
                                 [{:count 1,
                                   :value "Platform-p0",
                                   :subfields ["long_name"],
                                   :long_name [{:count 1, :value "Not Provided"}]}
                                  {:count 1,
                                   :value "Platform-p1",
                                   :subfields ["long_name"],
                                   :long_name [{:count 1, :value "Not Provided"}]}]}]}
                              {:value "Aircraft",
                               :count 1,
                               :subfields ["series_entity"],
                               :series_entity
                               [{:value "Not Provided",
                                 :count 1,
                                 :subfields ["short_name"],
                                 :short_name
                                 [{:value "A340-600",
                                   :count 1,
                                   :subfields ["long_name"],
                                   :long_name
                                   [{:value "Airbus A340-600", :count 1}]}]}]}]}]
        actual-platforms (->> (get-facet-results :hierarchical)
                              :json-facets
                              (filter #(= "platforms" (:field %))))]
    (is (= expected-platforms actual-platforms))))

(deftest instrument-missing-fields-test
  (grant-permissions)
  ;; Test that if the instruments do not exist in KMS, they will still be returned, but with a value
  ;; of "Not Provided" for all of the values in the hierarchy other than short name.
  (make-coll 1 "PROV1" (platforms "instrument-test" 2 2 1))
  ;; Test that even with a nil type and sub-type the instrument will still be returned, but with a
  ;; value of "Not Provided" for those fields.
  (make-coll 2 "PROV1" {:platforms [(dc/platform
                                      {:instruments [(dc/instrument {:short-name "ADS"})]})]})
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
                                     [{:value "Not Provided",
                                       :count 1,
                                       :subfields ["short_name"],
                                       :short_name
                                       [{:value "instrument-test-p0-i0",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p0-i1",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p1-i0",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name [{:value "Not Provided", :count 1}]}
                                        {:value "instrument-test-p1-i1",
                                         :count 1,
                                         :subfields ["long_name"],
                                         :long_name
                                         [{:value "Not Provided", :count 1}]}]}]}]}]}
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
  (make-coll 1 "PROV1" {:organizations [(dc/org :archive-center "Larc")]})
  ;; Test that even with a nil Level-1, Level-2, and Level-3 the archive-center will still be
  ;; returned, but with a value of "Not Provided" for each nil field
  (make-coll 2 "PROV1" {:organizations [(dc/org :archive-center "ESA/ED")]})
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
  (grant-permissions)
  (let [sk1-leading-ws (dc/science-keyword {:category "  Cat1"
                                            :topic " Topic1"
                                            :term " Term1"
                                            :variable-level-1 "   Level1-1"
                                            :variable-level-2 " Level1-2"
                                            :variable-level-3 " Level1-3"
                                            :detailed-variable " Detail1"})

        sk1-trailing-ws (dc/science-keyword {:category "Cat1   "
                                             :topic "Topic1 "
                                             :term "Term1 "
                                             :variable-level-1 "Level1-1     "
                                             :variable-level-2 "Level1-2 "
                                             :variable-level-3 "Level1-3 "
                                             :detailed-variable "Detail1 "})

        sk1-leading-and-trailing-ws (dc/science-keyword {:category "    Cat1 "
                                                         :topic " Topic1 "
                                                         :term " Term1 "
                                                         :variable-level-1 " Level1-1 "
                                                         :variable-level-2 " Level1-2 "
                                                         :variable-level-3 " Level1-3    "
                                                         :detailed-variable "  Detail1 "})

        coll1 (make-coll 1 "PROV1"
                         (projects "proj1" "PROJ2")
                         (platforms "A" 2 2 1)
                         (twod-coords "Alpha")
                         (science-keywords sk1 sk4 sk5)
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "Larc")
                                          (dc/org :processing-center "Larc")]})
        coll2 (make-coll 2 "PROV2"
                         (projects "proj3" "PROJ2")
                         (platforms "B" 2 2 1)
                         (science-keywords sk1 sk2 sk3)
                         (processing-level-id "pl1")
                         {:organizations [(dc/org :archive-center "GSFC")
                                          (dc/org :processing-center "Proc")]})
        coll3 (make-coll 3 "PROV2"
                         (science-keywords sk1-leading-ws))

        coll4 (make-coll 4 "PROV2"
                         (science-keywords sk1-trailing-ws))

        coll5 (make-coll 5 "PROV2"
                         (platforms " A" 1)
                         (projects "proj3 " " PROJ2")
                         (science-keywords sk1-leading-and-trailing-ws))]

    (index/wait-until-indexed)

    (testing "retreving all facets in refs formats"
      (let [expected-facets [{:field "data_center",
                              :value-counts [["GSFC" 1] ["Larc" 1] ["Proc" 1]]}
                             {:field "archive_center",
                              :value-counts [["GSFC" 1] ["Larc" 1]]}
                             {:field "project",
                              :value-counts [["PROJ2" 3] ["proj3" 2] ["proj1" 1]]}
                             {:field "platform",
                              :value-counts
                              [["A-p0" 2] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                             {:field "instrument",
                              :value-counts
                              [["A-p0-i0" 1]
                               ["A-p0-i1" 1]
                               ["A-p1-i0" 1]
                               ["A-p1-i1" 1]
                               ["B-p0-i0" 1]
                               ["B-p0-i1" 1]
                               ["B-p1-i0" 1]
                               ["B-p1-i1" 1]]}
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
                              :value-counts [["Alpha" 1]]}
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
