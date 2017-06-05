(ns cmr.system-int-test.ingest.collection-keyword-validation-test
  "CMR Ingest keyword validation integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are2 are3]]
    [cmr.ingest.services.messages :as msg]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.ingest-util :as ingest]))

(defn assert-invalid
  ([coll-attributes field-path errors]
   (assert-invalid coll-attributes field-path errors nil))
  ([coll-attributes field-path errors options]
   (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection coll-attributes)
                            (merge {:allow-failure? true} options))]
     (is (= {:status 422
             :errors [{:path field-path
                       :errors errors}]}
            (select-keys response [:status :errors]))))))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (data-umm-c/collection coll-attributes) :native-id (:native-id coll-attributes))
         provider-id (get coll-attributes :provider-id "PROV1")
         response (d/ingest-umm-spec-collection provider-id collection options)]
     (is (#{{:status 200} {:status 201}} (select-keys response [:status :errors]))))))

(defn assert-invalid-keywords
  [coll-attributes field-path errors]
  (assert-invalid coll-attributes field-path errors {:validate-keywords true
                                                     :format :umm-json
                                                     :accept-format :json}))

(defn assert-valid-keywords
  [coll-attributes]
  (assert-valid coll-attributes {:validate-keywords true
                                 :format :umm-json
                                 :accept-format :json}))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest collection-keyword-validation-test
  ;; For a list of the valid keywords during testing see dev-system/resources/kms_examples
  (testing "Keyword validation using validation endpoint"
    (let [concept (data-umm-c/collection-concept
                   {:Platforms [(data-umm-c/platform {:ShortName "foo"
                                                      :LongName "Airbus A340-600"})]
                    :DataCenters [(data-umm-c/data-center {:Roles ["ARCHIVER"] :ShortName "SomeCenter"})]})
          response (ingest/validate-concept concept {:validate-keywords true})]
      (is (= {:status 422
              :errors [{:path ["Platforms" 0]
                        :errors [(str "Platform short name [foo] and long name [Airbus A340-600] "
                                      "was not a valid keyword combination.")]}
                       {:path ["DataCenters" 0]
                        :errors [(str "Data center short name [SomeCenter] and long name [null] "
                                      "was not a valid keyword combination.")]}]}
             response))))

  (testing "Project keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:Projects [(assoc (data-umm-c/project short-name "") :LongName long-name)]}
            ["Projects" 0]
            [(format (str "Project short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])

          "Invalid short name"
          "foo" "European Digital Archive of Soil Maps"

          "Invalid with nil long name"
          "foo" nil

          "Invalid long name"
          "EUDASM" "foo"

          "Long name was nil in KMS"
          "EUCREX-94" "foo"

          "Invalid combination"
          "SEDAC/GISS CROP-CLIM DBQ" "European Digital Archive of Soil Maps")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:Projects [(assoc (data-umm-c/project short-name "") :LongName long-name)]})
          "Exact match"
          "EUDASM" "European Digital Archive of Soil Maps"

          "Nil long name in project and in KMS"
          "EUCREX-94" nil

          "Case Insensitive"
          "EUDaSM" "European DIgItal ArchIve of SoIl MAps"))

  (testing "Platform keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:Platforms [(data-umm-c/platform {:ShortName short-name :LongName long-name})]}
            ["Platforms" 0]
            [(format (str "Platform short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])

          "Invalid short name"
          "foo" "Airbus A340-600"

          "Long name is nil in KMS"
          "AIRCRAFT" "Airbus A340-600"

          "Invalid long name"
          "DMSP 5B/F3" "foo"

          "Invalid combination"
          "DMSP 5B/F3" "Airbus A340-600")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:Platforms [(data-umm-c/platform {:ShortName short-name :LongName long-name})]})
          "Exact match"
          "A340-600" "Airbus A340-600"

          "Case Insensitive"
          "a340-600" "aiRBus A340-600"))

  (testing "DataCenter keyword validation"
    (are3 [attribs]
          (let [dc (data-umm-c/data-center attribs)]
            (assert-invalid-keywords
              {:DataCenters [dc]}
              ["DataCenters" 0]
              [(msg/datacenter-not-matches-kms-keywords dc)]))

          "Invalid short name"
          {:Roles ["ARCHIVER"]
           :ShortName "AARHUS-HYDRO-Invalid"
           :LongName "Hydrogeophysics Group, Aarhus University "}

          "Invalid long name"
          {:Roles ["ARCHIVER"]
           :ShortName "AARHUS-HYDRO"
           :LongName "Hydrogeophysics Group, Aarhus University Invalid"}

          "Invalid combination"
          {:Roles ["ARCHIVER"]
           :ShortName "OR-STATE/EOARC"
           :LongName "Hydrogeophysics Group, Aarhus University "})

    (are3 [attribs]
          (let [dc (data-umm-c/data-center attribs)]
            (assert-valid-keywords {:DataCenters [dc]}))

          "Valid Case Sensitive"
          {:Roles ["ARCHIVER"]
           :ShortName "AARHUS-HYDRO"
           :LongName "Hydrogeophysics Group, Aarhus University "}

          "Valid Case Insensitive"
          {:Roles ["ARCHIVER"]
           :ShortName "aArHUS-HYDRO"
           :LongName "hYdrogeophysics Group, Aarhus University "}))

  (testing "DirectoryName keyword validation"
    (are3 [attribs]
          (let [dn (data-umm-c/directory-name attribs)]
            (assert-invalid-keywords
              {:DirectoryNames [dn]}
              ["DirectoryNames" 0]
              [(msg/directoryname-not-matches-kms-keywords dn)]))

          "Invalid short name"
          {:ShortName "SN Invalid"
           :LongName "LN NOT VALIDATED"})

    (are3 [attribs]
          (let [dn (data-umm-c/directory-name attribs)]
            (assert-valid-keywords {:DirectoryNames [dn]}))

          "Valid Case Sensitive"
          {:ShortName "GOSIC/GTOS"
           :LongName "LN NOT VALIDATED "}

          "Valid Case Insensitive"
          {:ShortName "gOSIC/GtOS"
           :LongName "LN NOT VALIDATED"}))

  (testing "ISOTopicCategories keyword validation"
    (are3 [itc]
          (assert-invalid-keywords
            {:ISOTopicCategories [itc]}
            ["IsoTopicCategories" 0]
            [(msg/iso-topic-category-not-matches-kms-keywords itc)])

          "Invalid ISOTopicCategory"
          "Invalid ISOTopicCategory")

    (are3 [itc]
          (assert-valid-keywords {:ISOTopicCategories [itc]})

          "Valid Case Sensitive"
          "BIOTA"

          "Valid Case Insensitive"
          "bIoTa"))

  (testing "Instrument keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:Platforms
             [(data-umm-c/platform
                {:ShortName "A340-600"
                 :LongName "Airbus A340-600"
                 :Instruments [(data-umm-c/instrument {:ShortName short-name
                                                       :LongName long-name})]})]}
            ["Platforms" 0 "Instruments" 0]
            [(format (str "Instrument short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])
          "Invalid short name"
          "foo" "Airborne Topographic Mapper"

          "Long name is nil in KMS"
          "ACOUSTIC SOUNDERS" "foo"

          "Invalid long name"
          "ATM" "foo"

          "Invalid combination"
          "ATM" "Land, Vegetation, and Ice Sensor")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:Platforms
             [(data-umm-c/platform
                {:ShortName "A340-600"
                 :LongName "Airbus A340-600"
                 :Instruments [(data-umm-c/instrument {:ShortName short-name
                                                       :LongName long-name})]})]})
          "Exact match"
          "ATM" "Airborne Topographic Mapper"

          "Nil long name in project and in KMS"
          "ACOUSTIC SOUNDERS" nil

          "Case Insensitive"
          "Atm" "aIRBORNE Topographic Mapper"))

  (testing "Science Keyword validation"
    (are [attribs]
         (let [sk (data-umm-c/science-keyword attribs)]
           (assert-invalid-keywords
             {:ScienceKeywords [sk]}
             ["ScienceKeywords" 0]
             [(msg/science-keyword-not-matches-kms-keywords attribs)]))

         {:Category "foo"
          :Topic "DATA ANALYSIS AND VISUALIZATION"
          :Term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:Category "EARTH SCIENCE SERVICES"
          :Topic "foo"
          :Term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:Category "EARTH SCIENCE SERVICES"
          :Topic "DATA ANALYSIS AND VISUALIZATION"
          :Term "foo"}

         {:Category "EARTH SCIENCE SERVICES"
          :Topic "DATA ANALYSIS AND VISUALIZATION"
          :Term "GEOGRAPHIC INFORMATION SYSTEMS"
          :VariableLevel1 "foo"}

         {:Category "EARTH SCIENCE"
          :Topic "ATMOSPHERE"
          :Term "AEROSOLS"
          :VariableLevel1 "AEROSOL OPTICAL DEPTH/THICKNESS"
          :VariableLevel2 "foo"}

         {:Category "EARTH SCIENCE"
          :Topic "ATMOSPHERE"
          :Term "ATMOSPHERIC TEMPERATURE"
          :VariableLevel1 "SURFACE TEMPERATURE"
          :VariableLevel2 "MAXIMUM/MINIMUM TEMPERATURE"
          :VariableLevel3 "foo"}

         ;; Invalid combination. Topic is valid but not with these other Terms
         {:Category "EARTH SCIENCE SERVICES"
          :Topic "ATMOSPHERE"
          :Term "GEOGRAPHIC INFORMATION SYSTEMS"})

    (are [attribs]
         (assert-valid-keywords {:ScienceKeywords [(data-umm-c/science-keyword attribs)]})

         {:Category "EARTH SCIENCE SERVICES"
          :Topic "DATA ANALYSIS AND VISUALIZATION"
          :Term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:Category "EARTH SCIENCE SERVICES"
          :Topic "DATA ANALYSIS AND VISUALIZATION"
          :Term "GEOGRAPHIC INFORMATION SYSTEMS"
          :VariableLevel1 "MOBILE GEOGRAPHIC INFORMATION SYSTEMS"}

         {:Category "EARTH SCIENCE"
          :Topic "ATMOSPHERE"
          :Term "AEROSOLS"
          :VariableLevel1 "AEROSOL OPTICAL DEPTH/THICKNESS"
          :VariableLevel2 "ANGSTROM EXPONENT"}

         {:Category "EARTH SCIENCE"
          :Topic "ATMOSPHERE"
          :Term "ATMOSPHERIC TEMPERATURE"
          :VariableLevel1 "SURFACE TEMPERATURE"
          :VariableLevel2 "MAXIMUM/MINIMUM TEMPERATURE"
          :VariableLevel3 "24 HOUR MAXIMUM TEMPERATURE"
          :DetailedVariable "This is ignored"}

         {:Category "EARTH SCiENCE"
          :Topic "ATMOsPHERE"
          :Term "ATMOSpHERIC TEMPERATURE"
          :VariableLevel1 "SuRFACE TEMPERATURE"
          :VariableLevel2 "MAXiMUM/MiNiMUM TEMPERATURE"
          :VariableLevel3 "24 HOUR MAXiMUM TEMPERATURE"}))

  (testing "Location Keyword validation"
    (are3 [attribs]
          (let [lk (data-umm-c/location-keyword attribs)]
            (assert-valid-keywords {:LocationKeywords [lk]}))

          "Valid location keyword"
          {:Category "CONTINENT"
           :Type "AFRICA"
           :Subregion1 "CENTRAL AFRICA"}

          "Valid full Location Keyword"
          {:Category "CONTINENT"
           :Type "ASIA"
           :Subregion1 "WESTERN ASIA"
           :Subregion2 "MIDDLE EAST"
           :Subregion3 "GAZA STRIP"
           :DetailedLocation "Testing Detailed Location"})

    (are3 [attribs]
          (let [lk (data-umm-c/location-keyword attribs)]
            (assert-invalid-keywords
              {:LocationKeywords [lk]}
              ["LocationKeywords" 0]
              [(msg/location-keyword-not-matches-kms-keywords attribs)]))

          "Invalid Type"
          {:Category "CONTINENT"
           :Type "GAZA"
           :Subregion1 "WESTERN ASIA"
           :Subregion2 "MIDDLE EAST"
           :Subregion3 "GAZA STRIP"
           :DetailedLocation "Testing Detailed Location"}

          "Invalid Category"
          {:Category "XYZ"}

          "Invalid Subregion"
          {:Category "CONTINENT"
           :Type "AFRICA"
           :Subregion1 "WESTERN ASIA"})))
