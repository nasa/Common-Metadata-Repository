(ns cmr.umm-spec.test.field-update
 "Unit tests for UMM field update functionality"
 (:require
  [clojure.test :refer :all]
  [cmr.common.util :refer [are3]]
  [cmr.umm-spec.field-update :as field-update]))

(deftest science-keyword-field-updates
  (testing "Existing science keywords"
    (let [umm {:EntryTitle "Test"
               :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                                 {:Category "EARTH SCIENCE SERVICES" :Topic "topic" :Term "term"
                                  :VariableLevel1 "var 1" :VariableLevel2 "var 2"
                                  :VariableLevel3 "var 3" :DetailedVariable "detailed"}]}]
     (are3 [update-type update-value find-value result]
       (is (= result
              (field-update/apply-umm-update update-type umm :ScienceKeywords update-value find-value)))

       "Clear and replace"
       :clear-all-and-replace
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       nil
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE SERVICES"
                           :Topic "DATA ANALYSIS AND VISUALIZATION"
                           :Term "GEOGRAPHIC INFORMATION SYSTEMS"}]}

       "Add to existing"
       :add-to-existing
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       nil
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                          {:Category "EARTH SCIENCE SERVICES" :Topic "topic" :Term "term"
                           :VariableLevel1 "var 1" :VariableLevel2 "var 2"
                           :VariableLevel3 "var 3" :DetailedVariable "detailed"}
                          {:Category "EARTH SCIENCE SERVICES"
                           :Topic "DATA ANALYSIS AND VISUALIZATION"
                           :Term "GEOGRAPHIC INFORMATION SYSTEMS"}]}

       "Find and remove"
       :find-and-remove
       nil
       {:Category "EARTH SCIENCE SERVICES"}
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}]}

       "Find and remove, not found"
       :find-and-remove
       nil
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "Topic"}
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                          {:Category "EARTH SCIENCE SERVICES" :Topic "topic" :Term "term"
                           :VariableLevel1 "var 1" :VariableLevel2 "var 2"
                           :VariableLevel3 "var 3" :DetailedVariable "detailed"}]}

       "Find and replace"
       :find-and-replace
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       {:Category "EARTH SCIENCE SERVICES"}
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                          {:Category "EARTH SCIENCE SERVICES"
                           :Topic "DATA ANALYSIS AND VISUALIZATION"
                           :Term "GEOGRAPHIC INFORMATION SYSTEMS"}]}

       "Find and replace, not found"
       :find-and-replace
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "Topic"}
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                          {:Category "EARTH SCIENCE SERVICES" :Topic "topic" :Term "term"
                           :VariableLevel1 "var 1" :VariableLevel2 "var 2"
                           :VariableLevel3 "var 3" :DetailedVariable "detailed"}]})))

  (testing "No science keywords"
    (let [umm {:EntryTitle "Test"}]
     (are3 [update-type update-value find-value result]
       (is (= result
              (field-update/apply-umm-update update-type umm :ScienceKeywords update-value find-value)))

       "Clear and replace"
       :clear-all-and-replace
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       nil
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE SERVICES"
                           :Topic "DATA ANALYSIS AND VISUALIZATION"
                           :Term "GEOGRAPHIC INFORMATION SYSTEMS"}]}

       "Add to existing"
       :add-to-existing
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       nil
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE SERVICES"
                           :Topic "DATA ANALYSIS AND VISUALIZATION"
                           :Term "GEOGRAPHIC INFORMATION SYSTEMS"}]}

       "Find and remove"
       :find-and-remove
       nil
       {:Category "EARTH SCIENCE SERVICES"}
       {:EntryTitle "Test"}

       "Find and replace"
       :find-and-replace
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       {:Category "EARTH SCIENCE SERVICES"}
       {:EntryTitle "Test"}))))
