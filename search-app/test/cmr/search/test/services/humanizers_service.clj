(ns cmr.search.test.services.humanizers-service
  "Testing functions used for verifying the humanizer report"
  (:require [clojure.test :refer :all]
            [cmr.common-app.humanizer :as humanizer]
            [cmr.search.services.humanizers-service :as hs]))

(deftest humanized-collection-report-science-keywords
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :product {:short-name "short-name"
                              :version-id "V15"}
                    :science-keywords [{:category "Bioosphere"
                                        :topic "Topic1"
                                        :term "Term1"}
                                       {:category "Bio sphere"
                                        :topic "Topic2"
                                        :term "Term2"}]}
        humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
   (testing "Science keywords humanizer report"
     (is (= (hs/humanized-collection->reported-rows humanized-collection)
            [["PROV1" "C1200000004-PROV1" "short-name" "V15" "Bioosphere" "Biosphere"]])))))

(deftest humanized-collection-report-platforms
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :product {:short-name "short-name"
                              :version-id "V15"}
                    :platforms [{:short-name "AM-1"}
                                {:short-name "TERRA"}]}
        humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
   (testing "Platforms humanizer report"
     (is (= (hs/humanized-collection->reported-rows humanized-collection)
            [["PROV1" "C1200000004-PROV1" "short-name" "V15" "AM-1" "Terra"]])))))

(deftest humanized-collection-report-instruments
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :product {:short-name "short-name"
                              :version-id "V15"}
                    :platforms [{:short-name "Terra"
                                 :instruments [{:short-name "GPS"}
                                               {:short-name "GPS RECEIVERS"}
                                               {:short-name "  LIDAR   "}]}]}
        humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
   (testing "Instruments humanizer report"
     (is (= (hs/humanized-collection->reported-rows humanized-collection)
            [["PROV1" "C1200000004-PROV1" "short-name" "V15" "GPS" "GPS Receivers"]
             ["PROV1" "C1200000004-PROV1" "short-name" "V15" "GPS RECEIVERS" "GPS Receivers"]])))))

(deftest humanized-collection-report-projects
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :product {:short-name "short-name"
                              :version-id "V15"}
                    :projects [{:short-name "USGS_SOFIA"}
                               {:short-name " OPENDAP  "}]}
        humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
   (testing "Projects humanizer report"
     (is (= (hs/humanized-collection->reported-rows humanized-collection)
            [["PROV1" "C1200000004-PROV1" "short-name" "V15" "USGS_SOFIA" "USGS SOFIA"]])))))

(deftest humanized-collection-report-processing-levels
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :product {:short-name "short-name"
                              :version-id "V15"}}]
    (testing "Processing level humanizer report L1T reportable"
      (let [collection (assoc-in collection [:product :processing-level-id] "L1T")
            humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
        (is (= (hs/humanized-collection->reported-rows humanized-collection)
               [["PROV1" "C1200000004-PROV1" "short-name" "V15" "L1T" "1T"]]))))
    (testing "Processing level humanizer report Level 1 reportable"
      (let [collection (assoc-in collection [:product :processing-level-id] "Level 1")
            humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
        (is (= (hs/humanized-collection->reported-rows humanized-collection)
               [["PROV1" "C1200000004-PROV1" "short-name" "V15" "Level 1" "1"]]))))
    (testing "Processing level humanizer report Level 2 reportable"
      (let [collection (assoc-in collection [:product :processing-level-id] "Level 2")
            humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
        (is (= (hs/humanized-collection->reported-rows humanized-collection)
               [["PROV1" "C1200000004-PROV1" "short-name" "V15" "Level 2" "2"]]))))
    (testing "Processing level humanizer report Level 3 reportable"
      (let [collection (assoc-in collection [:product :processing-level-id] "Level 3")
            humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
        (is (= (hs/humanized-collection->reported-rows humanized-collection)
               [["PROV1" "C1200000004-PROV1" "short-name" "V15" "Level 3" "3"]]))))
    (testing "Processing level humanizer report trim whitespace not reportable"
      (let [collection (assoc-in collection [:product :processing-level-id] " 1 ")
            humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
        (is (= (hs/humanized-collection->reported-rows humanized-collection)
               []))))))

(deftest humanized-collection-report-organizations
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :product {:short-name "short-name"
                              :version-id "V15"}
                    :organizations [{:org-name "NASA/NSIDC_DAAC"}
                                    {:org-name " LPDAAC "}]}
        humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
   (testing "Organizations humanizer report humanized but not reportable"
     (println humanized-collection)
     (is (= (hs/humanized-collection->reported-rows humanized-collection)
            [])))))


(deftest humanized-collection-reportable
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :product {:short-name "short-name"
                              :version-id "V15"
                              :processing-level-id "L1T"}
                    :science-keywords [{:category "Bioosphere"
                                        :topic "Topic1"
                                        :term "Term1"}
                                       {:category "Bio sphere"
                                        :topic "Topic2"
                                        :term "Term2"}]
                    :platforms [{:short-name "AM-1"
                                 :instruments [{:short-name "GPS"}
                                               {:short-name "  LIDAR   "}]}]}
        humanized-collection (humanizer/umm-collection->umm-collection+humanizers collection)]
   (testing "Science keywords humanizer report"
     (is (= (hs/humanized-collection->reported-rows humanized-collection)
            [["PROV1" "C1200000004-PROV1" "short-name" "V15" "AM-1" "Terra"]
             ["PROV1" "C1200000004-PROV1" "short-name" "V15" "GPS" "GPS Receivers"]
             ["PROV1" "C1200000004-PROV1" "short-name" "V15" "Bioosphere" "Biosphere"]
             ["PROV1" "C1200000004-PROV1" "short-name" "V15" "L1T" "1T"]])))))
