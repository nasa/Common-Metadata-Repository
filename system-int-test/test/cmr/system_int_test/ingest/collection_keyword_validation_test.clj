(ns cmr.system-int-test.ingest.collection-keyword-validation-test
  "CMR Ingest keyword validation integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are2 are3]]
    [cmr.ingest.services.messages :as msg]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.ingest-util :as ingest]))

(defn assert-invalid
  ([coll-attributes field-path errors]
   (assert-invalid coll-attributes field-path errors nil))
  ([coll-attributes field-path errors options]
   (let [response (d/ingest-umm-spec-collection
                   "PROV1"
                   (data-umm-c/collection coll-attributes)
                   (merge {:allow-failure? true} options))]
     (is (= {:status 422
             :errors [{:path field-path
                       :errors errors}]}
            (select-keys response [:status :errors]))))))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (data-umm-c/collection coll-attributes)
                           :native-id (:native-id coll-attributes))
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
  (testing "Keyword validation errors using validation endpoint"
    (let [concept (data-umm-c/collection-concept
                       {:Platforms [(data-umm-cmn/platform {:ShortName "foo"
                                                            :LongName "Airbus A340-600"
                                                            :Type "Jet"})]
                        :DataCenters [(data-umm-cmn/data-center {:Roles ["ARCHIVER"]
                                                                 :ShortName "SomeCenter"})]})

          response (ingest/validate-concept concept {:validate-keywords true})]
      (is (= {:status 422
              :errors [{:path ["Platforms" 0]
                        :errors [(str "Platform short name [foo], long name [Airbus A340-600], "
                                      "and type [Jet] was not a valid keyword combination.")]}
                       {:path ["DataCenters" 0]
                        :errors [(str "Data center short name [SomeCenter] was not a valid "
                                      "keyword.")]}]}
             response))))

  (testing "Keyword validation warnings using validation endpoint"
    (let [concept (data-umm-c/collection-concept
                       {:Platforms [(data-umm-cmn/platform {:ShortName "foo"
                                                            :LongName "Airbus A340-600"
                                                            :Type "Jet"})]
                        :DataCenters [(data-umm-cmn/data-center {:Roles ["ARCHIVER"]
                                                                 :ShortName "SomeCenter"
                                                                 :ContactInformation {:RelatedUrls [{:URL "http://example.com/file"
                                                                                                     :Description  "description"
                                                                                                     :Type "GET DATA"
                                                                                                     :URLContentType "DistributionURL"
                                                                                                     :Subtype "DIRECT DOWNLOAD"
                                                                                                     :GetData {:Format "RelatedUrls: BadFormat1"
                                                                                                               :MimeType "RelatedUrls: BadMimeType1"
                                                                                                               :Size 0.0
                                                                                                               :Unit "KB"}}]}})]
                        :RelatedUrls [{:URL "http://example.com/file"
                                       :Description  "Bad data, throws warning"
                                       :Type "GET DATA"
                                       :URLContentType "DistributionURL"
                                       :Subtype "DIRECT DOWNLOAD"
                                       :GetData {:Format "RelatedUrls: BadFormat2"
                                                 :MimeType "RelatedUrls: BadMimeType2"
                                                 :Size 0.0
                                                 :Unit "KB"}}
                                      {:URL "http://example.gov/file"
                                       :Description "No Warning: KMS only content type (not in valid-url-content-types-map)"
                                       :URLContentType "PublicationURL"
                                       :Type "VIEW RELATED INFORMATION"
                                       :Subtype "DATA PRODUCT SPECIFICATION"}]}
                   :umm-json)

          response (ingest/validate-concept concept {:validate-keywords false})]
      (is (= {:status 200
              :warnings "After translating item to UMM-C the metadata had the following issue(s): [:DataCenters 0 :ContactInformation :RelatedUrls 0] URLContentType must be DataCenterURL for DataCenter RelatedUrls;; [:RelatedUrls 0 :GetData :MimeType] MimeType [RelatedUrls: BadMimeType2] was not a valid keyword.;; [:Platforms 0] Platform short name [foo], long name [Airbus A340-600], and type [Jet] was not a valid keyword combination.;; [:DataCenters 0] Data center short name [SomeCenter] was not a valid keyword.;; [:DataCenters 0 :ContactInformation :RelatedUrls 0 :GetData :MimeType] MimeType [RelatedUrls: BadMimeType1] was not a valid keyword."}
             response))))

 (testing "ArchiveAndDistributionInformation and RelatedUrls keyword validation"
    (let [format (data-umm-c/collection-concept
                   {:ArchiveAndDistributionInformation
                    {:FileDistributionInformation
                     [(data-umm-c/file-distribution-information
                       {:FormatType "Native"
                        :AverageFileSize 50
                        :AverageFileSizeUnit "MB"
                        :Fees "None currently"
                        :Format "8-track tape"})]
                     :FileArchiveInformation
                     [(data-umm-c/file-archive-information
                       {:FormatType "Native"
                        :AverageFileSize 50
                        :AverageFileSizeUnit "MB"
                        :Format "8-track tape"})]}
                    :RelatedUrls
                    [{:Description "Related url description"
                      :URL "http://www.example.gov"
                      :URLContentType "DistributionURL"
                      :Type "GET DATA"
                      :GetData {:Format "8-track tape"
                                :Size 10.0
                                :Unit "MB"
                                :Fees "fees"}}
                     {:Description "Related url description"
                      :URL "http://www.example.org"
                      :URLContentType "DistributionURL"
                      :Type "GET DATA"}]}
                  :umm-json)
          response (ingest/validate-concept format {:validate-keywords true})]

      (is (= {:status 422
              :errors [{:path ["RelatedUrls" 0 "GetData" "Format"]
                        :errors [(str "Format [8-track tape] was not a valid keyword.")]}
                       {:path ["ArchiveAndDistributionInformation"
                               "FileDistributionInformation"
                               0]
                        :errors [(str "Format [8-track tape] was not a valid keyword.")]}
                       {:path ["ArchiveAndDistributionInformation"
                               "FileArchiveInformation"
                               0]
                        :errors [(str "Format [8-track tape] was not a valid keyword.")]}]}
             response)))

  (are3 [attribs]
        (assert-valid-keywords attribs)

        "Valid Case Sensitive"
        {:ArchiveAndDistributionInformation
         {:FileDistributionInformation
          [{:FormatType "Native"
            :AverageFileSize 50
            :AverageFileSizeUnit "MB"
            :Fees "None currently"
            :Format "HDF5"}]
          :FileArchiveInformation
          [{:FormatType "Native"
            :AverageFileSize 50
            :AverageFileSizeUnit "MB"
            :Format "HDF5"}]}
         :RelatedUrls
         [{:Description "Related url description"
           :URL "http://www.foobarbazquxquux.com"
           :URLContentType "DistributionURL"
           :Type "GET DATA"
           :GetData {:Format "HDF5"
                     :Size 10.0
                     :Unit "MB"
                     :Fees "fees"}}]}

        "Valid Case Insensitive"
        {:ArchiveAndDistributionInformation
         {:FileDistributionInformation
          [{:FormatType "Native"
             :AverageFileSize 50
             :AverageFileSizeUnit "MB"
             :Fees "None currently"
             :Format "hdf5"}]
          :FileArchiveInformation
          [{:FormatType "Native"
             :AverageFileSize 50
             :AverageFileSizeUnit "MB"
             :Format "hdf5"}]}
          :RelatedUrls
          [{:Description "Related url description"
            :URL "http://www.foobarbazquxquux.com"
            :URLContentType "DistributionURL"
            :Type "GET DATA"
            :GetData {:Format "hdf5"
                      :Size 10.0
                      :Unit "MB"
                      :Fees "fees"}}]}

        "Valid Case Sensitive"
        {:ArchiveAndDistributionInformation
         {:FileDistributionInformation
          [{:FormatType "Native"
            :AverageFileSize 50
            :AverageFileSizeUnit "MB"
            :Fees "None currently"
            :Format "JPEG"}]
          :FileArchiveInformation
          [{:FormatType "Native"
            :AverageFileSize 50
            :AverageFileSizeUnit "MB"
            :Format "JPEG"}]}
          :RelatedUrls
          [{:Description "Related url description"
            :URL "http://www.foobarbazquxquux.com"
            :URLContentType "DistributionURL"
            :Type "GET DATA"
            :GetData {:Format "JPEG"
                      :Size 10.0
                      :Unit "MB"
                      :Fees "fees"}}]})

  ;; Test that correct but missmatched enums are not allowed
  (are3 [attribs]
        (assert-invalid-keywords
         attribs
         ["RelatedUrls" 0]
         [(msg/related-url-content-type-type-subtype-not-matching-kms-keywords
           (first (:RelatedUrls attribs)))])

        "- Missmatched ContentType and Type/Subtype pair"
        {:ArchiveAndDistributionInformation
         {:FileDistributionInformation [{:Format "hdf5"}]
          :FileArchiveInformation [{:Format "hdf5"}]}
          :RelatedUrls
          [{:Description "Related url description"
            :URL "http://www.example.gov"
            :URLContentType "DistributionURL" ; wrong enum in this context
            :Type "GET RELATED VISUALIZATION"
            :Subtype "MAP"}]}

        "- Missmatched ContentType/Type pair and Subtype"
        {:ArchiveAndDistributionInformation
         {:FileDistributionInformation [{:Format "HDF5"}]
          :FileArchiveInformation [{:Format "HDF5"}]}
         :RelatedUrls
         [{:Description "Related url description"
           :URL "http://www.example.gov"
           :URLContentType "VisualizationURL"
           :Type "GET RELATED VISUALIZATION"
           :Subtype "HITIDE"}]} ; wrong enum in this context

        "- Missmatched Type from ContentType/Subtype pair"
        {:ArchiveAndDistributionInformation
         {:FileDistributionInformation [{:Format "JPEG"}]
          :FileArchiveInformation [{:Format "JPEG"}]}
          :RelatedUrls
          [{:Description "Related url description"
            :URL "http://www.example.gov"
            :URLContentType "VisualizationURL"
            :Type "DOWNLOAD SOFTWARE" ; wrong enum in this context
            :Subtype "MAP"}]})

  (testing "Project keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:Projects [(assoc (data-umm-cmn/project short-name "") :LongName long-name)]}
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
            {:Projects [(assoc (data-umm-cmn/project short-name "") :LongName long-name)]})
          "Exact match"
          "EUDASM" "European Digital Archive of Soil Maps"

          "Nil long name in project and in KMS"
          "EUCREX-94" nil

          "Case Insensitive"
          "EUDaSM" "European DIgItal ArchIve of SoIl MAps"))

  (testing "Platform keyword validation"
    (are2 [short-name long-name type]
          (assert-invalid-keywords
            {:Platforms [(data-umm-cmn/platform {:ShortName short-name
                                                 :LongName long-name
                                                 :Type type})]}
            ["Platforms" 0]
            [(format (str "Platform short name [%s], long name [%s], and type [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name type)])

          "Invalid short name"
          "foo" "Airbus A340-600" "Jet"

          "Long name is nil in KMS"
          "AIRCRAFT" "Airbus A340-600" "Aircraft"

          "Invalid long name"
          "DMSP 5B/F3" "foo" "Earth Observation Satellites"

          "Invalid long name"
          "DMSP 5B/F3" "Defense Meteorological Satellite Program-F3" "foo"

          "Invalid combination"
          "DMSP 5B/F3" "Airbus A340-600" "Earth Observation Satellites"

          ;;CMR-4400
          "Long name is in Platform and nil in KMS"
          "ALTUS" "foo" "Aircraft")

    (are2 [short-name long-name type]
          (assert-valid-keywords
            {:Platforms [(data-umm-cmn/platform {:ShortName short-name
                                                 :LongName long-name
                                                 :Type type})]})
          "Exact match"
          "A340-600" "Airbus A340-600" "Jet"

          "Case Insensitive"
          "a340-600" "aiRBus A340-600" "jET"

          ;; Next three scenarios are for CMR-4400
          "Long name is in Platform and KMS"
          "B-200" "Beechcraft King Air B-200" "Propeller"

          "Long name is nil in Platform and nil in KMS"
          "CESSNA 188" nil "Propeller"

          "Long name is nil in Platform and not nil in KMS"
          "DHC-3" nil "Propeller"))

  (testing "DataCenter keyword validation"
    (testing "Invalid short name"
      (let [dc (data-umm-cmn/data-center {:Roles ["ARCHIVER"]
                                          :ShortName "AARHUS-HYDRO-Invalid"
                                          :LongName "Hydrogeophysics Group, Aarhus University "})]
        (assert-invalid-keywords
          {:DataCenters [dc]}
          ["DataCenters" 0]
          [(msg/data-center-not-matches-kms-keywords dc)])))

    (are3 [attribs]
          (let [dc (data-umm-cmn/data-center attribs)]
            (assert-valid-keywords {:DataCenters [dc]}))

          "Valid Case Sensitive"
          {:Roles ["ARCHIVER"]
           :ShortName "AARHUS-HYDRO"
           :LongName "Hydrogeophysics Group, Aarhus University "}

          "Valid Case Insensitive"
          {:Roles ["ARCHIVER"]
           :ShortName "aArHUS-HYDRO"
           :LongName "hYdrogeophysics Group, Aarhus University "}

          "Invalid long name is ok"
          {:Roles ["ARCHIVER"]
           :ShortName "AARHUS-HYDRO"
           :LongName "Hydrogeophysics Group, Aarhus University Invalid"}

          "Nil long name is ok"
          {:Roles ["ARCHIVER"]
           :ShortName "AARHUS-HYDRO"}))

  (testing "DirectoryName keyword validation"
    (are3 [attribs]
          (let [dn (data-umm-c/directory-name attribs)]
            (assert-invalid-keywords
              {:DirectoryNames [dn]}
              ["DirectoryNames" 0]
              [(msg/directory-name-not-matches-kms-keywords dn)]))

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
             [(data-umm-cmn/platform
                {:ShortName "A340-600"
                 :LongName "Airbus A340-600"
                 :Type "Jet"
                 :Instruments [(data-umm-cmn/instrument {:ShortName short-name
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
             [(data-umm-cmn/platform
                {:ShortName "A340-600"
                 :LongName "Airbus A340-600"
                 :Type "Jet"
                 :Instruments [(data-umm-cmn/instrument {:ShortName short-name
                                                         :LongName long-name})]})]})
          "Exact match"
          "ATM" "Airborne Topographic Mapper"

          "Nil long name in project and in KMS"
          "ACOUSTIC SOUNDERS" nil

          "Case Insensitive"
          "Atm" "aIRBORNE Topographic Mapper"))

  (testing "Science Keyword validation"
    (are [attribs]
         (let [sk (data-umm-cmn/science-keyword attribs)]
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
         (assert-valid-keywords {:ScienceKeywords [(data-umm-cmn/science-keyword attribs)]})

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
           :Subregion1 "WESTERN ASIA"}))))
