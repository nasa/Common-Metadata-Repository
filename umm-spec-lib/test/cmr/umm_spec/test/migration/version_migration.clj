(ns cmr.umm-spec.test.migration.version-migration
  (:require [clojure.test :refer :all]
            [cmr.common.mime-types :as mt]
            [cmr.common.test.test-check-ext :as ext :refer [defspec]]
            [cmr.umm-spec.versioning :as v]
            [cmr.umm-spec.migration.version-migration :as vm]
            [cmr.umm-spec.test.umm-generators :as umm-gen]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [cmr.umm-spec.umm-spec-core :as core]
            [cmr.umm-spec.models.umm-collection-models :as umm-c]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]
            [cmr.umm-spec.models.umm-common-models :as umm-cmn]
            [cmr.umm-spec.util :as u]))

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions ["1.0" "1.1" "1.2" "1.3"]}
    (is (= [] (#'vm/version-steps "1.2" "1.2")))
    (is (= [["1.1" "1.2"] ["1.2" "1.3"]] (#'vm/version-steps "1.1" "1.3")))
    (is (= [["1.2" "1.1"] ["1.1" "1.0"]] (#'vm/version-steps "1.2" "1.0")))))

(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record   (gen/no-shrink umm-gen/umm-c-generator)
            dest-version (gen/elements v/versions)]
    (let [dest-media-type (str mt/umm-json "; version=" dest-version)
          metadata (core/generate-metadata (lkt/setup-context-for-test)
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
               (vm/migrate-umm (lkt/setup-context-for-test)
                               :collection "1.1" "1.2" {:SpatialKeywords nil}))))
  (is (empty? (:LocationKeywords
               (vm/migrate-umm (lkt/setup-context-for-test)
                               :collection "1.1" "1.2" {:SpatialKeywords []}))))

  (is (= [{:Category "CONTINENT"}]
         (:LocationKeywords
          (vm/migrate-umm
           (lkt/setup-context-for-test)
           :collection "1.1" "1.2" {:SpatialKeywords ["CONTINENT"]}))))
  ;; If not in the hierarchy, convert to CATEGORY OTHER and put the value as Type.
  (is (= [{:Category "OTHER" :Type "Somewhereville"}]
         (:LocationKeywords
          (vm/migrate-umm
           (lkt/setup-context-for-test)
           :collection "1.1" "1.2" {:SpatialKeywords ["Somewhereville"]}))))

  (is (= [{:Category "CONTINENT" :Type "AFRICA" :Subregion1 "CENTRAL AFRICA" :Subregion2 "ANGOLA"}]
         (:LocationKeywords
          (vm/migrate-umm
           (lkt/setup-context-for-test)
           :collection "1.1" "1.2" {:SpatialKeywords ["ANGOLA"]})))))


(deftest migrate_1_2-down-to-1_1
  (is (nil?
       (:LocationKeywords
        (vm/migrate-umm {} :collection "1.2" "1.1"
                        {:LocationKeywords
                         [{:Category "CONTINENT"}]}))))

  ;; Spatial keywords is required in 1.1
  (is (= [u/not-provided]
         (:SpatialKeywords
          (vm/migrate-umm {} :collection "1.2" "1.1"
                          {:LocationKeywords nil}))))

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
  (testing "Organizations to Data Centers"
    (let [organizations [{:Role "POINTOFCONTACT"
                          :Party {:OrganizationName {:ShortName "NASA/GSFC/SSED/CDDIS",
                                                     :LongName "Crustal Dynamics Data Information System, Solar System Exploration Division, Goddard Space Flight Center, NASA"}
                                  :Contacts [{:Type "Email"
                                              :Value "support-cddis@earthdata.nasa.gov"}]
                                  :Addresses [{:Country "United States"
                                               :StreetAddresses ["NASA GSFC" "Code 690.1"]
                                               :City "Greenbelt"
                                               :StateProvince "Maryland"
                                               :PostalCode "20771"}]
                                  :RelatedUrls [{:Title "CDDIS Home Page"
                                                 :Description "Home page for the CDDIS website",
                                                 :URLs ["http://cddis.gsfc.nasa.gov/"]}]
                                  :ServiceHours "M-F 9-5"
                                  :ContactInstructions "Email"}}
                         {:Role "DISTRIBUTOR"
                          :Party {:OrganizationName {:ShortName "NSIDC"}}}]
          result (vm/migrate-umm {} :collection "1.3" "1.4"
                                 {:Organizations organizations
                                  :Personnel "placeholder"})]
      (is (= [{:Roles ["ARCHIVER"]
               :ShortName "NASA/GSFC/SSED/CDDIS"
               :LongName "Crustal Dynamics Data Information System, Solar System Exploration Division, Goddard Space Flight Center, NASA"
               :ContactInformation [{:ServiceHours "M-F 9-5"
                                     :ContactInstruction "Email"
                                     :RelatedUrls [{:Title "CDDIS Home Page"
                                                    :Description "Home page for the CDDIS website"
                                                    :URLs ["http://cddis.gsfc.nasa.gov/"]}]
                                     :Addresses [{:Country "United States"
                                                  :StreetAddresses ["NASA GSFC" "Code 690.1"]
                                                  :City "Greenbelt"
                                                  :StateProvince "Maryland"
                                                  :PostalCode "20771"}]
                                     :ContactMechanisms [{:Type "Email"
                                                          :Value "support-cddis@earthdata.nasa.gov"}]}]}
              {:Roles ["DISTRIBUTOR"]
               :ShortName "NSIDC"
               :LongName nil
               :ContactInformation nil}]
             (:DataCenters result)))
      (is (nil? (:ContactGroups result)))
      (is (nil? (:Organizations result)))
      (is (nil? (:Personnel result)))))
  (testing "Personnel to Contact Persons"
    (let [personnel [{:Role "Technical Contact"
                      :Party {:Person {:FirstName "Carey"
                                       :MiddleName "E"
                                       :LastName "Noll"}
                              :Contacts [{:Type "Email"
                                          :Value "Carey.Noll@nasa.gov"}]
                              :Addresses [{:Country "United States"
                                           :StreetAddresses ["NASA GSFC" "Code 690.1"]
                                           :City "Greenbelt"
                                           :StateProvince "Maryland"
                                           :PostalCode "20771"}]}}]
          result (vm/migrate-umm {} :collection "1.3" "1.4"
                                 {:Personnel personnel})]
        (is (nil? (:ContactGroups result)))
        (is (nil? (:Organizations result)))
        (is (nil? (:Personnel result)))
        (is (= [{:Roles ["Technical Contact"]
                 :FirstName "Carey"
                 :MiddleName "E"
                 :LastName "Noll"
                 :Uuid nil
                 :ContactInformation [{:ServiceHours nil
                                       :ContactInstruction nil
                                       :RelatedUrls nil
                                       :Addresses [{:Country "United States"
                                                    :StreetAddresses ["NASA GSFC" "Code 690.1"]
                                                    :City "Greenbelt"
                                                    :StateProvince "Maryland"
                                                    :PostalCode "20771"}]
                                       :ContactMechanisms [{:Type "Email" :Value "Carey.Noll@nasa.gov"}]}]}]
               (:ContactPersons result))))))

(deftest migrate-1_4-down-to-1_3
  (testing "Data Centers to Organizations"
    (let [data-centers [{:Roles ["ORIGINATOR"]
                         :ShortName "LPDAAC"
                         :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                                           :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                                           :FirstName "John"
                                           :MiddleName "D"
                                           :LastName "Smith"}]
                         :ContactInformation [{:RelatedUrls [{:Description "Contact related url description"
                                                              :Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"]
                                                              :URLs ["www.contact.foo.com", "www.contact.shoo.com"]
                                                              :Title "contact related url title"
                                                              :MimeType "application/html"}]
                                               :ServiceHours "Weekdays 9AM - 5PM"
                                               :ContactInstruction "sample contact instruction"
                                               :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                                   {:Type "Email" :Value "cmr@nasa.gov"}]
                                               :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                            :City "Greenbelt"
                                                            :StateProvince "MD"
                                                            :PostalCode "20771"
                                                            :Country "U.S.A."}]}]}]
          result (vm/migrate-umm {} :collection "1.4" "1.3"
                                 {:DataCenters data-centers})]
      (is (nil? (:DataCenters result)))
      (is (nil? (:ContactGroups result)))
      (is (nil? (:ContactPersons result)))
      (is (= [{:Role "ORIGINATOR"
               :Party {:OrganizationName {:ShortName "LPDAAC" :LongName nil}
                       :ServiceHours "Weekdays 9AM - 5PM"
                       :ContactInstructions "sample contact instruction"
                       :Contacts [{:Type "Telephone", :Value "301-851-1234"}
                                  {:Type "Email", :Value "cmr@nasa.gov"}]
                       :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                    :City "Greenbelt"
                                    :StateProvince "MD"
                                    :PostalCode "20771"
                                    :Country "U.S.A."}]
                       :RelatedUrls [{:Description "Contact related url description"
                                      :Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"]
                                      :URLs ["www.contact.foo.com" "www.contact.shoo.com"]
                                      :Title "contact related url title"
                                      :MimeType "application/html"}]}}]
           (:Organizations result)))))
  (testing "Contact Persons to Personnel"
    (let [contact-persons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                            :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                            :ContactInformation [{:RelatedUrls [{:Description "Contact related url description"
                                                                 :Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"]
                                                                 :URLs ["www.contact.foo.com", "www.contact.shoo.com"]
                                                                 :Title "contact related url title"
                                                                 :MimeType "application/html"}]
                                                  :ServiceHours "Weekdays 9AM - 5PM"
                                                  :ContactInstruction "sample contact instruction"
                                                  :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                                      {:Type "Email" :Value "cmr@nasa.gov"}]
                                                  :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                               :City "Greenbelt"
                                                               :StateProvince "MD"
                                                               :PostalCode "20771"
                                                               :Country "U.S.A."}]}]
                            :FirstName "John"
                            :MiddleName "D"
                            :LastName "Smith"}]
            result (vm/migrate-umm {} :collection "1.4" "1.3"
                                   {:ContactPersons contact-persons})]
      (is (nil? (:DataCenters result)))
      (is (nil? (:ContactGroups result)))
      (is (nil? (:ContactPersons result)))
      (is (= [{:Role "POINTOFCONTACT"
               :Party {:Person {:Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                                :FirstName "John"
                                :MiddleName "D"
                                :LastName "Smith"}
                       :ServiceHours "Weekdays 9AM - 5PM"
                       :ContactInstructions "sample contact instruction"
                       :Contacts [{:Type "Telephone", :Value "301-851-1234"}
                                  {:Type "Email", :Value "cmr@nasa.gov"}]
                       :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                    :City "Greenbelt"
                                    :StateProvince "MD"
                                    :PostalCode "20771"
                                    :Country "U.S.A."}]
                       :RelatedUrls [{:Description "Contact related url description"
                                      :Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"]
                                      :URLs ["www.contact.foo.com" "www.contact.shoo.com"]
                                      :Title "contact related url title"
                                      :MimeType "application/html"}]}}]
           (:Personnel result))))))

(deftest migrate-roundtrip-1_3-to-1_4
  (let [organizations [{:Role "POINTOFCONTACT"
                        :Party {:OrganizationName {:ShortName "NASA/GSFC/SSED/CDDIS",
                                                   :LongName "Crustal Dynamics Data Information System, Solar System Exploration Division, Goddard Space Flight Center, NASA"}
                                :Contacts [{:Type "Email"
                                            :Value "support-cddis@earthdata.nasa.gov"}]
                                :Addresses [{:Country "United States"
                                             :StreetAddresses ["NASA GSFC" "Code 690.1"]
                                             :City "Greenbelt"
                                             :StateProvince "Maryland"
                                             :PostalCode "20771"}]
                                :RelatedUrls [{:Title "CDDIS Home Page"
                                               :Description "Home page for the CDDIS website",
                                               :URLs ["http://cddis.gsfc.nasa.gov/"]}]
                                :ServiceHours "M-F 9-5"
                                :ContactInstructions "Email"}}]
        personnel [{:Role "POINTOFCONTACT"
                    :Party {:Person {:FirstName "Carey"
                                     :MiddleName "E"
                                     :LastName "Noll"
                                     :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}
                            :Contacts [{:Type "Email"
                                        :Value "Carey.Noll@nasa.gov"}]
                            :Addresses [{:Country "United States"
                                         :StreetAddresses ["NASA GSFC" "Code 690.1"]
                                         :City "Greenbelt"
                                         :StateProvince "Maryland"
                                         :PostalCode "20771"}]
                            :ServiceHours nil
                            :ContactInstructions nil
                            :RelatedUrls nil}}]
        result-migrate-up (vm/migrate-umm {} :collection "1.3" "1.4"
                                          {:Organizations organizations
                                           :Personnel personnel})
        result (vm/migrate-umm {} :collection "1.4" "1.3" result-migrate-up)]
    (is (= organizations (:Organizations result)))
    (is (= personnel (:Personnel result)))
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

(deftest migrate-1_5-up-to-1_6
  (let [result (vm/migrate-umm {} :collection "1.5" "1.6"
                               {:DataCenters
                                [{:ShortName "dc1"
                                  :ContactInformation
                                  [{:ServiceHours "M-F 9-5"
                                    :ContactInstruction "Call"}
                                   {:ServiceHours "M-W 6-9"}]
                                  :ContactPersons
                                  [{:LastName "A"
                                    :ContactInformation
                                    [{:ServiceHours "M-F 9-5"
                                      :ContactInstruction "Call"}
                                     {:ServiceHours "M-W 6-9"}]}]
                                  :ContactGroups
                                  [{:GroupName "B"
                                    :ContactInformation
                                    [{:ServiceHours "M-F 9-5"
                                      :ContactInstruction "Call"}
                                     {:ServiceHours "M-W 6-9"}]}]}]
                                :ContactPersons
                                [{:LastName "A"
                                  :ContactInformation
                                  [{:ServiceHours "M-F 9-5"
                                    :ContactInstruction "Call"}
                                   {:ServiceHours "M-W 6-9"}]}]
                                :ContactGroups
                                [{:GroupName "B"
                                  :ContactInformation
                                  [{:ServiceHours "M-F 9-5"
                                    :ContactInstruction "Call"}
                                   {:ServiceHours "M-W 6-9"}]}]})]

    (is (= {:ServiceHours "M-F 9-5"
            :ContactInstruction "Call"}
         (:ContactInformation (first (:DataCenters result)))))
    (is (= {:ServiceHours "M-F 9-5"
            :ContactInstruction "Call"}
         (:ContactInformation (first (:ContactPersons (first (:DataCenters result)))))))
    (is (= {:ServiceHours "M-F 9-5"
            :ContactInstruction "Call"}
         (:ContactInformation (first (:ContactGroups (first (:DataCenters result)))))))
    (is (= {:ServiceHours "M-F 9-5"
            :ContactInstruction "Call"}
         (:ContactInformation (first (:ContactPersons result)))))
    (is (= {:ServiceHours "M-F 9-5"
            :ContactInstruction "Call"}
         (:ContactInformation (first (:ContactGroups result)))))))

(deftest migrate-1_6-down-to-1_5
  (let [result (vm/migrate-umm {} :collection "1.6" "1.5"
                               {:DataCenters
                                [{:ShortName "dc1"
                                  :ContactInformation
                                  {:ServiceHours "M-F 9-5"
                                   :ContactInstruction "Call"}
                                  :ContactPersons
                                  [{:LastName "A"
                                    :ContactInformation
                                    {:ServiceHours "M-F 9-5"
                                     :ContactInstruction "Call"}}]
                                  :ContactGroups
                                  [{:GroupName "B"
                                    :ContactInformation
                                    {:ServiceHours "M-F 9-5"
                                     :ContactInstruction "Call"}}]}]
                                :ContactPersons
                                [{:LastName "A"
                                  :ContactInformation
                                  {:ServiceHours "M-F 9-5"
                                   :ContactInstruction "Call"}}]
                                :ContactGroups
                                [{:GroupName "B"
                                  :ContactInformation
                                  {:ServiceHours "M-F 9-5"
                                   :ContactInstruction "Call"}}]})]

    (is (= [{:ServiceHours "M-F 9-5"
             :ContactInstruction "Call"}]
         (:ContactInformation (first (:DataCenters result)))))
    (is (= [{:ServiceHours "M-F 9-5"
             :ContactInstruction "Call"}]
         (:ContactInformation (first (:ContactPersons (first (:DataCenters result)))))))
    (is (= [{:ServiceHours "M-F 9-5"
             :ContactInstruction "Call"}]
         (:ContactInformation (first (:ContactGroups (first (:DataCenters result)))))))
    (is (= [{:ServiceHours "M-F 9-5"
             :ContactInstruction "Call"}]
         (:ContactInformation (first (:ContactPersons result)))))
    (is (= [{:ServiceHours "M-F 9-5"
             :ContactInstruction "Call"}]
         (:ContactInformation (first (:ContactGroups result)))))))

(deftest migrate-1_6-up-to-1_7
  (let [result (vm/migrate-umm {} :collection "1.6" "1.7"
                               {:ISOTopicCategories
                                ["biota" "cloud" "climatologyMeteorologyAtmosphere"]})]
    ;; Any ISOTopicCategory that is not in the defined enumeration list is converted to "location"
    (is (= ["biota" "location" "climatologyMeteorologyAtmosphere"] (:ISOTopicCategories result)))))

(deftest migrate-1_7-down-to-1_6
  (let [result (vm/migrate-umm {} :collection "1.7" "1.6"
                               {:ISOTopicCategories
                                ["biota" "location" "climatologyMeteorologyAtmosphere"]})]
    (is (= ["biota" "location" "climatologyMeteorologyAtmosphere"] (:ISOTopicCategories result)))))

(deftest migrate-1_7-up-to-1_8
  (let [result (vm/migrate-umm {} :collection "1.7" "1.8"
                               {:Version "003"})]
    ;; VersionDescription is nil, CollectionProgress has default
    (is (= nil (:VersionDescription result)))
    (is (= u/not-provided (:CollectionProgress result)))))

(deftest migrate-1_8-down-to-1_7
  (let [result (vm/migrate-umm {} :collection "1.8" "1.7"
                               {:VersionDescription "description of the collection version"})]
    (is (= nil (:VersionDescription result)))))

(deftest migrate-1_8-up-to-1_9
  (let [result (vm/migrate-umm {} :collection "1.8" "1.9"
                               {:CollectionCitations [{:SeriesName ">np", :Creator "^", :ReleasePlace ";CUhWxe", :Title "u8,#XJA4U=",
                                                       :DOI {:Authority ";'", :DOI "F19,L"}, :Publisher nil, :ReleaseDate nil,
                                                       :IssueIdentification nil, :Editor nil, :DataPresentationForm nil,
                                                       :Version nil, :OtherCitationDetails nil,
                                                       :RelatedUrl {:URLs ["www.google.com" "www.foo.com"]
                                                                    :Title "URL Title"
                                                                    :Description "URL Description"}}]
                                :PublicationReferences [{:RelatedUrl {:URLs ["www.google.com" "www.foo.com"]
                                                                      :Title "URL Title"
                                                                      :Description "URL Description"}}
                                                        {:RelatedUrl {:URLs ["www.foo.com"]}}]
                                :DataCenters [{:Roles ["ORIGINATOR"]
                                               :ShortName "LPDAAC"
                                               :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                                                                 :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                                                                 :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                                                                     :Title "A nice title"
                                                                                                     :Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"]
                                                                                                     :URLs ["www.contact.foo.com", "www.contact.shoo.com"]
                                                                                                     :MimeType "application/html"}]
                                                                                      :ServiceHours "Weekdays 9AM - 5PM"
                                                                                      :ContactInstruction "sample contact instruction"
                                                                                      :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                                                                          {:Type "Email" :Value "cmr@nasa.gov"}]
                                                                                      :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                                                                   :City "Greenbelt"
                                                                                                   :StateProvince "MD"
                                                                                                   :PostalCode "20771"
                                                                                                   :Country "U.S.A."}]}
                                                                 :FirstName "John"
                                                                 :MiddleName "D"
                                                                 :LastName "Smith"}]
                                               :ContactInformation {:ContactMechanisms [{:Type "Twitter" :Value "@lpdaac"}]}}
                                              {:Roles ["ARCHIVER" "DISTRIBUTOR"]
                                               :ShortName "TNRIS"
                                               :LongName "Texas Natural Resources Information System"
                                               :Uuid "aa63353f-8686-4175-9296-f6685a04a6da"
                                               :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                                                                 :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                                                                 :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                                                                     :Title "A lovely title. Doesn't it make you so happy?"
                                                                                                     :Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"]
                                                                                                     :URLs ["www.contact.foo.com", "www.contact.shoo.com"]
                                                                                                     :MimeType "application/html"}]
                                                                                      :ServiceHours "Weekdays 9AM - 5PM"
                                                                                      :ContactInstruction "sample contact instruction"
                                                                                      :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                                                                          {:Type "Email" :Value "cmr@nasa.gov"}]
                                                                                      :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                                                                   :City "Greenbelt"
                                                                                                   :StateProvince "MD"
                                                                                                   :PostalCode "20771"
                                                                                                   :Country "U.S.A."}]}
                                                                 :FirstName "John"
                                                                 :MiddleName "D"
                                                                 :LastName "Smith"}]}
                                              {:Roles ["ARCHIVER" "DISTRIBUTOR"]
                                               :ShortName "NSIDC"
                                               :Uuid "aa63353f-8686-4175-9296-f6685a04a6da"
                                               :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                                                   :Title "Title McTitleburgh"
                                                                                   :Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"]
                                                                                   :URLs ["www.contact.foo.com", "www.contact.shoo.com"]
                                                                                   :MimeType "application/html"}]
                                                                    :ServiceHours "Weekdays 9AM - 5PM"
                                                                    :ContactInstruction "sample contact instruction"
                                                                    :ContactMechanisms [{:Type "Telephone" :Value "301-851-1234"}
                                                                                        {:Type "Email" :Value "cmr@nasa.gov"}
                                                                                        {:Type "Fax" :Value "301-851-4321"}]
                                                                    :Addresses [{:StreetAddresses ["NASA GSFC, Code 610.2"]
                                                                                 :City "Greenbelt"
                                                                                 :StateProvince "MD"
                                                                                 :PostalCode "20771"
                                                                                 :Country "U.S.A."}]}
                                               :ContactGroups [{:Roles ["Investigator"]
                                                                :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc888"
                                                                :ContactInformation {:RelatedUrls [{:Description "Contact group related url description"
                                                                                                    :Title "Just when you thought titles couldn't get any better"
                                                                                                    :Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"]
                                                                                                    :URLs ["www.contact.group.foo.com"]
                                                                                                    :MimeType "application/html"}]
                                                                                     :ServiceHours "Weekdays 9AM - 5PM"
                                                                                     :ContactInstruction "sample contact group instruction"
                                                                                     :ContactMechanisms [{:Type "Fax" :Value "301-851-1234"}]
                                                                                     :Addresses [{:StreetAddresses ["5700 Rivertech Ct"]
                                                                                                  :City "Riverdale"
                                                                                                  :StateProvince "MD"
                                                                                                  :PostalCode "20774"
                                                                                                  :Country "U.S.A."}]}
                                                                :GroupName "NSIDC_IceBridge"}]}
                                              {:Roles ["PROCESSOR"]
                                               :ShortName "Processing Center"
                                               :LongName "processor.processor"}]})]
    ;; DOI is moved from :CollectionCitations to :DOI
    ;; RelatedUrl is moved to :OnlineResource
    (is (= {:Authority ";'", :DOI "F19,L"} (:DOI result)))
    (is (= [{:SeriesName ">np", :Creator "^", :ReleasePlace ";CUhWxe", :Title "u8,#XJA4U=",
             :Publisher nil, :ReleaseDate nil, :IssueIdentification nil,
             :Editor nil, :DataPresentationForm nil, :Version nil, :OtherCitationDetails nil
             :OnlineResource {:Linkage "www.google.com" :Name "URL Title" :Description "URL Description"}}]
           (:CollectionCitations result)))
    ;; RelatedUrls no longer contain Titles
    (let [data-center-related-url
          (:RelatedUrls (first (map :ContactInformation (first (map :ContactPersons (:DataCenters result))))))]
      (is (= [{:Relation ["VIEW RELATED INFORMATION" "USER SUPPORT"],
               :MimeType "application/html",
               :URLs ["www.contact.foo.com" "www.contact.shoo.com"],
               :Description "Contact related url description"}]
             data-center-related-url)))
    ;; PublicationReferences Related URL migrates to Online Resource
    (is (= [{:OnlineResource {:Linkage "www.google.com" :Name "URL Title" :Description "URL Description"}}
            {:OnlineResource {:Linkage "www.foo.com" :Name u/not-provided :Description u/not-provided}}]
           (:PublicationReferences result)))))

(deftest migrate-1_9-down-to-1_8
  (let [result (vm/migrate-umm {} :collection "1.9" "1.8"
                               {:DOI {:Authority ";'", :DOI "F19,L"}
                                :CollectionCitations [{:SeriesName ">np", :Creator "^", :ReleasePlace ";CUhWxe", :Title "u8,#XJA4U=",
                                                       :Publisher nil, :ReleaseDate nil, :IssueIdentification nil,
                                                       :Editor nil, :DataPresentationForm nil, :Version nil, :OtherCitationDetails nil
                                                       :OnlineResource {:Linkage "www.google.com"
                                                                        :Name "URL Title"
                                                                        :Description "URL Description"}}]
                                :PublicationReferences [{:OnlineResource {:Linkage "www.google.com"
                                                                          :Name "URL Title"
                                                                          :Description "URL Description"}}]})]
    ;; DOI is moved from :DOI to :CollectionCitations
    (is (= nil (:DOI result)))
    (is (= [{:SeriesName ">np", :Creator "^", :ReleasePlace ";CUhWxe", :Title "u8,#XJA4U=",
             :DOI {:Authority ";'", :DOI "F19,L"},
             :Publisher nil, :ReleaseDate nil, :IssueIdentification nil,
             :Editor nil, :DataPresentationForm nil, :Version nil, :OtherCitationDetails nil
             :RelatedUrl {:URLs ["www.google.com"]
                          :Title "URL Title"
                          :Description "URL Description"
                          :Relation ["VIEW RELATED INFORMATION" "Citation"]
                          :MimeType "text/html"}}]
           (:CollectionCitations result)))
    ;; PublicationReferences Online Resource migrates to RelatedUrl
    (is (= [{:RelatedUrl {:URLs ["www.google.com"]
                          :Title "URL Title"
                          :Description "URL Description"
                          :Relation ["VIEW RELATED INFORMATION" "Citation"]
                          :MimeType "text/html"}}]
           (:PublicationReferences result)))))
