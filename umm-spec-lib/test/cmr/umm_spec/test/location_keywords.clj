(ns cmr.umm-spec.test.location-keywords
  "Unit tests for GCMD Spatial Keywords -> LocationKeywords translation"
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.location-keywords :as lk]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.cache :as cache]
            [cmr.common.cache.in-memory-cache :as imc]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]))

(deftest test-location-keyword-lookup
  (testing "Looking up a root keyword returns the top heirarchy result."
    (let [keyword "CONTINENT"
          expected {:category "CONTINENT", :uuid "0a672f19-dad5-4114-819a-2eb55bdbb56a"}
          actual (lk/find-spatial-keyword lkt/sample-keyword-map keyword)]
      (is (= expected actual))))

  (testing "Looking up a uuid returns a valid result"
    (let [uuid "a028edce-a3d9-4a16-a8c7-d2cb12d3a318"
          expected {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "CAMEROON", :uuid "a028edce-a3d9-4a16-a8c7-d2cb12d3a318"}
          actual (lk/find-spatial-keyword lkt/sample-keyword-map uuid)]
      (is (= expected actual))))

  (testing "Searching for a duplicate keyword retrieves the correct result"
    (let [keyword "SPACE"
          expected "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
          actual (:uuid (lk/find-spatial-keyword lkt/sample-keyword-map keyword))]
          (is (= expected actual))))

  (testing "Passing in a list of keywords returns a list of Location Keyword maps"
    (let [keywords ["SPACE" "ANGOLA" "CENTRAL AFRICA"]
          expected [{:category "SPACE", :type "EARTH MAGNETIC FIELD", :subregion-1 "SPACE", :uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}
                    {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :subregion-2 "ANGOLA", :uuid "9b0a194d-d617-4fed-9625-df176319892d"}
                    {:category "CONTINENT", :type "AFRICA", :subregion-1 "CENTRAL AFRICA", :uuid "f2ffbe58-8792-413b-805b-3e1c8de1c6ff"}]
          actual (lk/spatial-keywords->location-keywords lkt/sample-keyword-map keywords)]
      (is (= expected actual)))))
