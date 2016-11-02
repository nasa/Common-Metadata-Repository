(ns cmr.search.test.services.humanizers.humanizer-report-service
  "Testing functions used for verifying the humanizer report"
  (:require [clojure.test :refer :all]
            [cmr.common-app.humanizer :as h]
            [cmr.common-app.test.sample-humanizer :as sh]
            [cmr.search.services.humanizers.humanizer-report-service :as hrs]))

(def sample-humanizers
  (:humanizers sh/sample-humanizers))

(deftest humanized-collection-report-science-keywords
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :ShortName "short-name"
                    :Version "V15"
                    :ScienceKeywords [{:Category "Bioosphere"
                                        :Topic "Topic1"
                                        :Term "Term1"}
                                      {:Category "Bio sphere"
                                        :Topic "Topic2"
                                        :Term "Term2"}]}
        humanized-collection (h/umm-collection->umm-collection+humanizers
                               collection sample-humanizers)]
    (testing "Science keywords humanizer report"
      (is (= (hrs/humanized-collection->reported-rows humanized-collection)
             [["PROV1" "C1200000004-PROV1" "short-name" "V15" "Bioosphere" "Biosphere"]])))))

(deftest humanized-collection-report-platforms
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :ShortName "short-name"
                    :Version "V15"
                    :Platforms [{:ShortName "AM-1"}
                                {:ShortName "TERRA"}]}
        humanized-collection (h/umm-collection->umm-collection+humanizers
                               collection sample-humanizers)]
    (testing "Platforms humanizer report"
      (is (= (hrs/humanized-collection->reported-rows humanized-collection)
             [["PROV1" "C1200000004-PROV1" "short-name" "V15" "AM-1" "Terra"]])))))

(deftest humanized-collection-report-instruments
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :ShortName "short-name"
                    :Version "V15"
                    :Platforms [{:ShortName "Terra"
                                 :Instruments [{:ShortName "GPS"}
                                               {:ShortName "GPS RECEIVERS"}
                                               {:ShortName "  LIDAR   "}]}]}
        humanized-collection (h/umm-collection->umm-collection+humanizers
                               collection sample-humanizers)]
    (testing "Instruments humanizer report"
      (is (= (hrs/humanized-collection->reported-rows humanized-collection)
             [["PROV1" "C1200000004-PROV1" "short-name" "V15" "GPS" "GPS Receivers"]
              ["PROV1" "C1200000004-PROV1" "short-name" "V15" "GPS RECEIVERS" "GPS Receivers"]])))))

(deftest humanized-collection-report-projects
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :ShortName "short-name"
                    :Version "V15"
                    :Projects [{:ShortName "USGS_SOFIA"}
                               {:ShortName " OPENDAP  "}]}
        humanized-collection (h/umm-collection->umm-collection+humanizers
                               collection sample-humanizers)]
    (testing "Projects humanizer report"
      (is (= (hrs/humanized-collection->reported-rows humanized-collection)
             [["PROV1" "C1200000004-PROV1" "short-name" "V15" "USGS_SOFIA" "USGS SOFIA"]])))))

(deftest humanized-collection-report-processing-levels
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :ShortName "short-name"
                    :Version "V15"}]
    (testing "Processing level humanizer report L1T reportable"
      (let [collection (assoc-in collection [:ProcessingLevel :Id] "L1T")
            humanized-collection (h/umm-collection->umm-collection+humanizers
                                   collection sample-humanizers)]
        (is (= (hrs/humanized-collection->reported-rows humanized-collection)
               [["PROV1" "C1200000004-PROV1" "short-name" "V15" "L1T" "1T"]]))))
    (testing "Processing level humanizer report Level 1 reportable"
      (let [collection (assoc-in collection [:ProcessingLevel :Id] "Level 1")
            humanized-collection (h/umm-collection->umm-collection+humanizers
                                   collection sample-humanizers)]
        (is (= (hrs/humanized-collection->reported-rows humanized-collection)
               [["PROV1" "C1200000004-PROV1" "short-name" "V15" "Level 1" "1"]]))))
    (testing "Processing level humanizer report Level 2 reportable"
      (let [collection (assoc-in collection [:ProcessingLevel :Id] "Level 2")
            humanized-collection (h/umm-collection->umm-collection+humanizers
                                   collection sample-humanizers)]
        (is (= (hrs/humanized-collection->reported-rows humanized-collection)
               [["PROV1" "C1200000004-PROV1" "short-name" "V15" "Level 2" "2"]]))))
    (testing "Processing level humanizer report Level 3 reportable"
      (let [collection (assoc-in collection [:ProcessingLevel :Id] "Level 3")
            humanized-collection (h/umm-collection->umm-collection+humanizers
                                   collection sample-humanizers)]
        (is (= (hrs/humanized-collection->reported-rows humanized-collection)
               [["PROV1" "C1200000004-PROV1" "short-name" "V15" "Level 3" "3"]]))))
    (testing "Processing level humanizer report trim whitespace not reportable"
      (let [collection (assoc-in collection [:ProcessingLevel :Id] " 1 ")
            humanized-collection (h/umm-collection->umm-collection+humanizers
                                   collection sample-humanizers)]
        (is (= (hrs/humanized-collection->reported-rows humanized-collection)
               []))))))

(deftest humanized-collection-report-organizations
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :ShortName "short-name"
                    :Version "V15"
                    :DataCenters [{:ShortName "NASA/NSIDC_DAAC"}
                                  {:ShortName " LPDAAC "}]}
        humanized-collection (h/umm-collection->umm-collection+humanizers
                               collection sample-humanizers)]
    (testing "Organizations humanizer report humanized but not reportable"
      (is (empty? (hrs/humanized-collection->reported-rows humanized-collection))))))

(deftest humanized-collection-reportable
  (let [collection {:provider-id "PROV1"
                    :concept-id "C1200000004-PROV1"
                    :ProcessingLevel {:Id "L1T"}
                    :ShortName "short-name"
                    :Version "V15"

                    :ScienceKeywords [{:Category "Bioosphere"
                                        :Topic "Topic1"
                                        :Term "Term1"}
                                      {:Category "Bio sphere"
                                        :Topic "Topic2"
                                        :Term "Term2"}]
                    :Platforms [{:ShortName "AM-1"
                                 :Instruments [{:ShortName "GPS"}
                                               {:ShortName "  LIDAR   "}]}]}
        humanized-collection (h/umm-collection->umm-collection+humanizers
                               collection sample-humanizers)]
    (testing "Science keywords humanizer report"
      (is (= (hrs/humanized-collection->reported-rows humanized-collection)
             [["PROV1" "C1200000004-PROV1" "short-name" "V15" "AM-1" "Terra"]
              ["PROV1" "C1200000004-PROV1" "short-name" "V15" "GPS" "GPS Receivers"]
              ["PROV1" "C1200000004-PROV1" "short-name" "V15" "Bioosphere" "Biosphere"]
              ["PROV1" "C1200000004-PROV1" "short-name" "V15" "L1T" "1T"]])))))
