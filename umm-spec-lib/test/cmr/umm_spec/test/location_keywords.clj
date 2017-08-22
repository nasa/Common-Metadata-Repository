(ns cmr.umm-spec.test.location-keywords
  "Unit tests for GCMD Spatial Keywords -> LocationKeywords translation"
  (:require
    [clojure.test :refer :all]
    [cmr.common-app.services.kms-fetcher :as kf]
    [cmr.common.cache :as cache]
    [cmr.common.cache.in-memory-cache :as imc]
    [cmr.transmit.config :as transmit-config]
    [cmr.umm-spec.location-keywords :as lk]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.test.location-keywords-helper :as lkt]))

(def find-spatial-keyword
  "Allow testing of private function."
  #'lk/find-spatial-keyword)

(deftest test-location-keyword-lookup
  (testing "Looking up a root keyword returns the top hierarchy result."
    (let [keyword "CONTINENT"
          expected {:category "CONTINENT", :uuid "0a672f19-dad5-4114-819a-2eb55bdbb56a"}
          actual (find-spatial-keyword lkt/sample-kms-index keyword)]
      (is (= expected actual))))

  (testing "Searching for a duplicate keyword retrieves the correct result"
    (let [keyword "SPACE"
          expected "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
          actual (:uuid (find-spatial-keyword lkt/sample-kms-index keyword))]
      (is (= expected actual))))

  (testing "Passing in a list of keywords returns a list of Location Keyword maps"
    (let [keywords ["SPACE" "ANGOLA" "CENTRAL AFRICA"]
          expected [{:Category "SPACE", :Type "EARTH MAGNETIC FIELD", :Subregion1 "SPACE"}
                    {:Category "CONTINENT", :Type "AFRICA", :Subregion1 "CENTRAL AFRICA", :Subregion2 "ANGOLA"}
                    {:Category "CONTINENT", :Type "AFRICA", :Subregion1 "CENTRAL AFRICA"}]
          actual (lk/spatial-keywords->location-keywords lkt/sample-kms-index keywords)]
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
          actual (lk/location-keywords->spatial-keywords-for-indexing location-keyword-list)
          expected ["SPACE" "EARTH MAGNETIC FIELD" "CONTINENT" "AFRICA" "CENTRAL AFRICA" "ANGOLA"]]
      (is (= expected actual)))))
