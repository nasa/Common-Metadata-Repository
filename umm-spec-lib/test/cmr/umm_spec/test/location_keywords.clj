(ns cmr.umm-spec.test.location-keywords
  "Unit tests for GCMD Spatial Keywords -> LocationKeywords translation"
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.location-keywords :as lk]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.cache :as cache]
            [cmr.common.cache.in-memory-cache :as imc]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]
            [cmr.umm-spec.models.collection :as umm-c]))

(deftest test-location-keyword-lookup
  (testing "Looking up a root keyword returns the top heirarchy result."
    (let [keyword "CONTINENT"
          context lkt/setup-context-for-test
          expected {:category "CONTINENT", :uuid "0a672f19-dad5-4114-819a-2eb55bdbb56a"}
          actual (lk/find-spatial-keyword (:spatial-keywords lkt/sample-keyword-map) keyword)]
      (is (= expected actual))))

  (testing "Looking up a uuid returns a valid result"
    (let [uuid "a028edce-a3d9-4a16-a8c7-d2cb12d3a318"
          context lkt/setup-context-for-test
          expected {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CAMEROON", :uuid "a028edce-a3d9-4a16-a8c7-d2cb12d3a318"}
          actual (lk/find-spatial-keyword (:spatial-keywords lkt/sample-keyword-map) uuid)]
      (is (= expected actual))))

  (testing "Searching for a duplicate keyword retrieves the correct result"
    (let [keyword "SPACE"
          expected "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
          context lkt/setup-context-for-test
          actual (:uuid (lk/find-spatial-keyword (:spatial-keywords lkt/sample-keyword-map) keyword))]
          (is (= expected actual))))

  (testing "Passing in a list of keywords returns a list of Location Keyword maps"
    (let [keywords ["SPACE" "ANGOLA" "CENTRAL AFRICA"]
          context lkt/setup-context-for-test
          expected [{:Category "SPACE", :Type "EARTH MAGNETIC FIELD", :Subregion1 "SPACE"}
                    {:Category "CONTINENT", :Type "AFRICA", :Subregion1 "CENTRAL AFRICA", :Subregion2 "ANGOLA"}
                    {:Category "CONTINENT", :Type "AFRICA", :Subregion1 "CENTRAL AFRICA"}]
          actual (lk/spatial-keywords->location-keywords (:spatial-keywords lkt/sample-keyword-map) keywords)]
      (is (= expected actual))))

  (testing "Converting a list of LocationKeyword maps returns a list of SpatialKeywords"
    (let [location-keyword-maps [{:Category "SPACE",
                                  :Type "EARTH MAGNETIC FIELD",
                                  :Subregion1 "SPACE"}
                                 {:Category "CONTINENT",
                                  :Type "AFRICA",
                                  :Subregion1 "CENTRAL AFRICA",
                                  :Subregion2 "ANGOLA"}
                                 {:Category "CONTINENT",
                                  :Type "AFRICA",
                                  :Subregion1 "CENTRAL AFRICA"}]
          location-keyword-list (map #(umm-c/map->LocationKeywordType %) location-keyword-maps)
          actual (lk/location-keywords->spatial-keywords location-keyword-list)
          expected ["SPACE" "ANGOLA" "CENTRAL AFRICA"]]
          (is (= expected actual)))))
