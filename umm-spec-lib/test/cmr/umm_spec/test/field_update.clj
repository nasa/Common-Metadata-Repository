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
              (field-update/apply-update update-type umm [:ScienceKeywords] update-value find-value)))

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

       "Find and update"
       :find-and-update
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       {:Category "EARTH SCIENCE SERVICES"}
       {:EntryTitle "Test"
        :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                          {:Category "EARTH SCIENCE SERVICES"
                           :Topic "DATA ANALYSIS AND VISUALIZATION"
                           :Term "GEOGRAPHIC INFORMATION SYSTEMS"
                           :VariableLevel1 "var 1",
                           :VariableLevel2 "var 2",
                           :VariableLevel3 "var 3",
                           :DetailedVariable "detailed"}]}

       "Find and update, not found"
       :find-and-update
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
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
              (field-update/apply-update update-type umm [:ScienceKeywords] update-value find-value)))

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

       "Find and update"
       :find-and-update
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       {:Category "EARTH SCIENCE SERVICES"}
       {:EntryTitle "Test"}

       "Find and replace"
       :find-and-replace
       {:Category "EARTH SCIENCE SERVICES"
        :Topic "DATA ANALYSIS AND VISUALIZATION"
        :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
       {:Category "EARTH SCIENCE SERVICES"}
       {:EntryTitle "Test"}))))

(deftest location-keyword-updates-test
  (let [umm {:LocationKeywords [{:Category "CONTINENT"
                                 :Type "ASIA"
                                 :Subregion1 "WESTERN ASIA"
                                 :Subregion2 "MIDDLE EAST"
                                 :Subregion3 "GAZA STRIP"}]}]
     (are3 [update-type update-value find-value result]
       (is (= result
              (field-update/apply-update update-type umm [:LocationKeywords] update-value find-value)))

       "Add to existing"
       :add-to-existing
       {:Category "CONTINENT"
        :Type "EUROPE"}
       nil
       {:LocationKeywords [{:Category "CONTINENT"
                            :Type "ASIA"
                            :Subregion1 "WESTERN ASIA"
                            :Subregion2 "MIDDLE EAST"
                            :Subregion3 "GAZA STRIP"}
                           {:Category "CONTINENT"
                            :Type "EUROPE"}]}

       "Clear all and replace"
       :clear-all-and-replace
       {:Category "CONTINENT"
        :Type "EUROPE"}
       nil
       {:LocationKeywords [{:Category "CONTINENT"
                            :Type "EUROPE"}]}

       "Find and update"
       :find-and-update
       {:Subregion1 "EASTERN ASIA"}
       {:Subregion1 "WESTERN ASIA"}
       {:LocationKeywords [{:Category "CONTINENT"
                            :Type "ASIA"
                            :Subregion1 "EASTERN ASIA"
                            :Subregion2 "MIDDLE EAST"
                            :Subregion3 "GAZA STRIP"}]}
       "Find and replace"
       :find-and-replace
       {:Subregion1 "EASTERN ASIA"}
       {:Subregion1 "WESTERN ASIA"}
       {:LocationKeywords [{:Subregion1 "EASTERN ASIA"}]})))

(deftest platform-instrument-name-updates
 (testing "Platform name updates"
   (let [umm {:Platforms [{:ShortName "Platform 1"
                           :LongName "Example Platform Long Name 1"
                           :Type "Aircraft"
                           :Instruments [{:ShortName "An Instrument"
                                          :LongName "The Full Name of An Instrument v123.4"
                                          :Technique "Two cans and a string"
                                          :NumberOfInstruments 0}]}]}]
      (are3 [update-type update-value find-value result]
        (is (= result
               (field-update/apply-update update-type umm [:Platforms] update-value find-value)))

        "Find and update short name"
        :find-and-update
        {:ShortName "A340-600"}
        {:ShortName "Platform 1"}
        {:Platforms [{:ShortName "A340-600"
                      :LongName "Example Platform Long Name 1"
                      :Type "Aircraft"
                      :Instruments [{:ShortName "An Instrument"
                                     :LongName "The Full Name of An Instrument v123.4"
                                     :Technique "Two cans and a string"
                                     :NumberOfInstruments 0}]}]}

        "Find and update long and short names"
        :find-and-update
        {:ShortName "A340-600"
         :LongName "Airbus A340-600"}
        {:ShortName "Platform 1"}
        {:Platforms [{:ShortName "A340-600"
                      :LongName "Airbus A340-600"
                      :Type "Aircraft"
                      :Instruments [{:ShortName "An Instrument"
                                     :LongName "The Full Name of An Instrument v123.4"
                                     :Technique "Two cans and a string"
                                     :NumberOfInstruments 0}]}]}

        "Find and replace short name"
        :find-and-replace
        {:ShortName "A340-600"}
        {:ShortName "Platform 1"}
        {:Platforms [{:ShortName "A340-600"}]}

        "Find and replace long and short names"
        :find-and-replace
        {:ShortName "A340-600"
         :LongName "Airbus A340-600"}
        {:ShortName "Platform 1"}
        {:Platforms [{:ShortName "A340-600"
                      :LongName "Airbus A340-600"}]}))

  (testing "Instrument updates"
    (let [umm {:Platforms [{:ShortName "Platform 1"
                            :LongName "Example Platform Long Name 1"
                            :Type "Aircraft"
                            :Instruments [{:ShortName "Inst 1"
                                           :LongName "Instrument 1"}
                                          {:ShortName "Inst 2"
                                           :LongName "Instrument 2"}]}
                           {:ShortName "Platform 2"
                            :LongName "Example Platform Long Name 2"
                            :Type "Aircraft"
                            :Instruments [{:ShortName "Inst 1"
                                           :LongName "Instrument 1"}
                                          {:ShortName "Inst 3"
                                           :LongName "Instrument 3"}]}]}]
       (are3 [update-type update-value find-value result]
         (is (= result
                (field-update/apply-update update-type umm [:Instruments] update-value find-value)))

         "Add instrument to existing"
         :add-to-existing
         {:ShortName "Inst X"}
         nil
         {:Platforms [{:ShortName "Platform 1"
                       :LongName "Example Platform Long Name 1"
                       :Type "Aircraft"
                       :Instruments [{:ShortName "Inst 1"
                                      :LongName "Instrument 1"}
                                     {:ShortName "Inst 2"
                                      :LongName "Instrument 2"}
                                     {:ShortName "Inst X"}]}
                      {:ShortName "Platform 2"
                       :LongName "Example Platform Long Name 2"
                       :Type "Aircraft"
                       :Instruments [{:ShortName "Inst 1"
                                      :LongName "Instrument 1"}
                                     {:ShortName "Inst 3"
                                      :LongName "Instrument 3"}
                                     {:ShortName "Inst X"}]}]}

        "Clear all and replace"
        :clear-all-and-replace
        {:ShortName "Inst X"}
        nil
        {:Platforms [{:ShortName "Platform 1"
                      :LongName "Example Platform Long Name 1"
                      :Type "Aircraft"
                      :Instruments [{:ShortName "Inst X"}]}
                     {:ShortName "Platform 2"
                      :LongName "Example Platform Long Name 2"
                      :Type "Aircraft"
                      :Instruments [{:ShortName "Inst X"}]}]}

        "Find and update - multiple instances"
        :find-and-update
        {:ShortName "Inst X"}
        {:ShortName "Inst 1"}
        {:Platforms [{:ShortName "Platform 1"
                      :LongName "Example Platform Long Name 1"
                      :Type "Aircraft"
                      :Instruments [{:ShortName "Inst X"
                                     :LongName "Instrument 1"}
                                    {:ShortName "Inst 2"
                                     :LongName "Instrument 2"}]}
                     {:ShortName "Platform 2"
                      :LongName "Example Platform Long Name 2"
                      :Type "Aircraft"
                      :Instruments [{:ShortName "Inst X"
                                     :LongName "Instrument 1"}
                                    {:ShortName "Inst 3"
                                     :LongName "Instrument 3"}]}]}

        "Find and update - multiple instances"
        :find-and-update
        {:ShortName "Inst X"}
        {:ShortName "Inst 2"}
        {:Platforms [{:ShortName "Platform 1"
                      :LongName "Example Platform Long Name 1"
                      :Type "Aircraft"
                      :Instruments [{:ShortName "Inst 1"
                                     :LongName "Instrument 1"}
                                    {:ShortName "Inst X"
                                     :LongName "Instrument 2"}]}
                     {:ShortName "Platform 2"
                      :LongName "Example Platform Long Name 2"
                      :Type "Aircraft"
                      :Instruments [{:ShortName "Inst 1"
                                     :LongName "Instrument 1"}
                                    {:ShortName "Inst 3"
                                     :LongName "Instrument 3"}]}]})

      "Find and replace - multiple instances"
      :find-and-replace
      {:ShortName "Inst X"}
      {:ShortName "Inst 1"}
      {:Platforms [{:ShortName "Platform 1"
                    :LongName "Example Platform Long Name 1"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "Inst 1"}
                                  {:ShortName "Inst 2"
                                   :LongName "Instrument 2"}]}
                   {:ShortName "Platform 2"
                    :LongName "Example Platform Long Name 2"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "Inst 1"}
                                  {:ShortName "Inst 3"
                                   :LongName "Instrument 3"}]}]}

      "Find and replace - multiple instances"
      :find-and-replace
      {:ShortName "Inst X"}
      {:ShortName "Inst 2"}
      {:Platforms [{:ShortName "Platform 1"
                    :LongName "Example Platform Long Name 1"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "Inst 1"
                                   :LongName "Instrument 1"}
                                  {:ShortName "Inst 2"}]}
                   {:ShortName "Platform 2"
                    :LongName "Example Platform Long Name 2"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "Inst 1"
                                   :LongName "Instrument 1"}
                                  {:ShortName "Inst 3"
                                   :LongName "Instrument 3"}]}]}

      "Find and remove"
      :find-and-remove
      nil
      {:ShortName "Inst 1"}
      {:Platforms [{:ShortName "Platform 1"
                    :LongName "Example Platform Long Name 1"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "Inst 1"
                                   :LongName "Instrument 1"}]}
                   {:ShortName "Platform 2"
                    :LongName "Example Platform Long Name 2"
                    :Type "Aircraft"
                    :Instruments [{:ShortName "Inst 1"
                                   :LongName "Instrument 1"}]}]}))))
