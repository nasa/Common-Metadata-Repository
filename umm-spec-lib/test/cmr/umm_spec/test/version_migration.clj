(ns cmr.umm-spec.test.version-migration
  (:require [clojure.test :refer :all]
            [cmr.common.mime-types :as mt]
            [cmr.common.test.test-check-ext :as ext :refer [defspec]]
            [cmr.umm-spec.versioning :as v]
            [cmr.umm-spec.version-migration :as vm]
            [cmr.umm-spec.test.umm-generators :as umm-gen]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [cmr.umm-spec.core :as core]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.util :as u]))

(deftest test-version-steps
  (with-redefs [cmr.umm-spec.versioning/versions ["1.0" "1.1" "1.2" "1.3"]]
    (is (= [] (#'vm/version-steps "1.2" "1.2")))
    (is (= [["1.1" "1.2"] ["1.2" "1.3"]] (#'vm/version-steps "1.1" "1.3")))
    (is (= [["1.2" "1.1"] ["1.1" "1.0"]] (#'vm/version-steps "1.2" "1.0")))))

(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record   (gen/no-shrink umm-gen/umm-c-generator)
            dest-version (gen/elements v/versions)]
           (let [dest-media-type (str mt/umm-json "; version=" dest-version)
                 metadata (core/generate-metadata (lkt/setup-context-for-test
                                                   {:spatial-keywords lkt/sample-keyword-map})
                                                  umm-record dest-media-type)]
             (empty? (core/validate-metadata :collection dest-media-type metadata)))))

(deftest migrate-1_0-up-to-1_1
  (is (nil?
        (:TilingIdentificationSystems
          (vm/migrate-umm {} :collection "1.0" "1.1"
                         {:TilingIdentificationSystem nil}))))
  (is (= [{:TilingIdentificationSystemName "foo"}]
         (:TilingIdentificationSystems
           (vm/migrate-umm {} :collection "1.0" "1.1"
                          {:TilingIdentificationSystem {:TilingIdentificationSystemName "foo"}})))))

(deftest migrate-1_1-down-to-1_0
  (is (nil?
        (:TilingIdentificationSystem
          (vm/migrate-umm {} :collection "1.1" "1.0"
                         {:TilingIdentificationSystems nil}))))
  (is (nil?
        (:TilingIdentificationSystem
          (vm/migrate-umm {} :collection "1.1" "1.0"
                         {:TilingIdentificationSystems []}))))
  (is (= {:TilingIdentificationSystemName "foo"}
         (:TilingIdentificationSystem
           (vm/migrate-umm {} :collection "1.1" "1.0"
                          {:TilingIdentificationSystems [{:TilingIdentificationSystemName "foo"}
                                                         {:TilingIdentificationSystemName "bar"}]})))))

(deftest migrate-1_1-up-to-1_2
  (is (empty? (:LocationKeywords
             (vm/migrate-umm (lkt/setup-context-for-test lkt/sample-keyword-map)
                            :collection "1.1" "1.2" {:SpatialKeywords nil}))))
  (is (empty? (:LocationKeywords
             (vm/migrate-umm (lkt/setup-context-for-test lkt/sample-keyword-map)
                            :collection "1.1" "1.2" {:SpatialKeywords []}))))

  (is (= [{:Category "CONTINENT"}]
         (:LocationKeywords
          (vm/migrate-umm
           (lkt/setup-context-for-test lkt/sample-keyword-map)
           :collection "1.1" "1.2" {:SpatialKeywords ["CONTINENT"]}))))
  ;; If not in the hierarchy, convert to CATEGORY OTHER and put the value as Type.
  (is (= [{:Category "OTHER" :Type "Somewhereville"}]
         (:LocationKeywords
          (vm/migrate-umm
           (lkt/setup-context-for-test lkt/sample-keyword-map)
           :collection "1.1" "1.2" {:SpatialKeywords ["Somewhereville"]}))))

  (is (= [{:Category "CONTINENT" :Type "AFRICA" :Subregion1 "CENTRAL AFRICA" :Subregion2 "ANGOLA"}]
         (:LocationKeywords
          (vm/migrate-umm
           (lkt/setup-context-for-test lkt/sample-keyword-map)
           :collection "1.1" "1.2" {:SpatialKeywords ["ANGOLA"]})))))


(deftest migrate_1_2-down-to-1_1
  (is (nil?
       (:LocationKeywords
        (vm/migrate-umm {} :collection "1.2" "1.1"
                       {:LocationKeywords
                        [{:Category "CONTINENT"}]}))))
  (is (= ["CONTINENT"]
          (:SpatialKeywords
           (vm/migrate-umm {} :collection "1.2" "1.1"
                          {:LocationKeywords
                           [{:Category "CONTINENT"}]}))))
  (is (= ["ANGOLA"]
         (:SpatialKeywords
          (vm/migrate-umm {} :collection "1.2" "1.1"
                         {:LocationKeywords [{:Category "CONTINENT"
                                              :Type "AFRICA"
                                              :Subregion1 "CENTRAL AFRICA"
                                              :Subregion2 "ANGOLA"}]}))))
  (is (= ["Somewhereville" "Nowhereville"]
         (:SpatialKeywords
          (vm/migrate-umm {} :collection "1.2" "1.1"
                         {:LocationKeywords [{:Category "OTHER" :Type "Somewhereville"}
                                             {:Category "OTHER" :Type "Nowhereville"}]})))))

(deftest migrate-1_2-up-to-1_3
  (is (nil?
        (:PaleoTemporalCoverages
          (vm/migrate-umm {} :collection "1.2" "1.3"
                         {:PaleoTemporalCoverage nil}))))
  (let [paleo-coverage {:StartDate "5 Ma"
                        :EndDate "2 Ma"
                        :ChronostratigraphicUnits [{:Eon "PHANEROZOIC"
                                                    :Era "CENOZOIC"
                                                    :Period "NEOGENE"
                                                    :Epoch "Pliocene"}]}]
    (is (= [paleo-coverage]
           (:PaleoTemporalCoverages
             (vm/migrate-umm {} :collection "1.2" "1.3" {:PaleoTemporalCoverage paleo-coverage}))))))

(deftest migrate-1_3-down-to-1_2
  (is (nil?
        (:PaleoTemporalCoverage
          (vm/migrate-umm {} :collection "1.3" "1.2"
                         {:PaleoTemporalCoverages nil}))))
  (is (nil?
        (:PaleoTemporalCoverage
          (vm/migrate-umm {} :collection "1.3" "1.2"
                         {:PaleoTemporalCoverages []}))))
  (let [paleo-coverage-1 {:StartDate "5 Ma"
                          :EndDate "2 Ma"
                          :ChronostratigraphicUnits [{:Eon "PHANEROZOIC"
                                                      :Era "CENOZOIC"
                                                      :Period "NEOGENE"
                                                      :Epoch "Pliocene"}]}
        paleo-coverage-2 {:StartDate "2000 ybp"
                          :EndDate "0 ybp"}]
    (is (= paleo-coverage-1
           (:PaleoTemporalCoverage
             (vm/migrate-umm {} :collection "1.3" "1.2"
                            {:PaleoTemporalCoverages [paleo-coverage-1 paleo-coverage-2]}))))))

(deftest migrate-1_3-up-to-1_4
  (let [result (vm/migrate-umm {} :collection "1.3" "1.4"
                         {:Organizations [vm/not-provided-organization]
                          :Personnel "placeholder"})]
    (is (= [u/not-provided-data-center] (:DataCenters result)))
    (is (nil? (:ContactGroups result)))
    (is (nil? (:ContactPersons result)))
    (is (nil? (:Organizations result)))
    (is (nil? (:Personnel result)))))

(deftest migrate-1_4-down-to-1_3
  (let [result (vm/migrate-umm {} :collection "1.4" "1.3"
                         {:DataCenters [u/not-provided-data-center]
                          :ContactGroups "placeholder"
                          :ContactPersons "placeholder"})]
    (is (= [vm/not-provided-organization] (:Organizations result)))
    (is (nil? (:Personnel result)))
    (is (nil? (:DataCenters result)))
    (is (nil? (:ContactGroups result)))
    (is (nil? (:ContactPersons result)))))

(deftest migrate-1_4-up-to-1_5
  (let [result (vm/migrate-umm {} :collection "1.4" "1.5"
                           {:AdditionalAttributes
                             [{:Name "aa1"
                               :Description "aa1"}
                              {:Name "aa2"}]})]
      (is (= "aa1" (:Description (first (:AdditionalAttributes result)))))
      (is (= "Not provided" (:Description (second (:AdditionalAttributes result)))))))

(deftest migrate-1_5-down-to-1_4
    (let [result (vm/migrate-umm {} :collection "1.5" "1.4"
                             {:AdditionalAttributes
                               [{:Name "aa1"
                                 :Description "aa1"}
                                {:Name "aa2"
                                 :Description "Not provided"}]})]
        (is (= "aa1" (:Description (first (:AdditionalAttributes result)))))
        (is (= "Not provided" (:Description (second (:AdditionalAttributes result)))))))
