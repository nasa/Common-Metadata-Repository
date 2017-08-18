(ns cmr.umm-spec.test.migration.version-migration
  (:require
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.mime-types :as mt]
   [cmr.common.test.test-check-ext :as ext :refer [defspec]]
   [cmr.umm-spec.migration.version-migration :as vm]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :as v]
   [com.gfredericks.test.chuck.clojure-test :refer [for-all]]))

(def umm-1-9-related-urls
  {:ContactGroups [{:Roles ["Investigator"]}
                   :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc888"
                   :ContactInformation {:RelatedUrls [{:Description "Contact group related url description"
                                                       :URLContentType "DataContactURL"
                                                       :Type "HOME PAGE"
                                                       :URL "www.contact.group.foo.com"}]
                                        :ServiceHours "Weekdays 9AM - 5PM"
                                        :ContactInstruction "sample contact group instruction"
                                        :ContactMechanisms [{:Type "Fax" :Value "301-851-1234"}]
                                        :Addresses [{:StreetAddresses ["5700 Rivertech Ct"]
                                                     :City "Riverdale"
                                                     :StateProvince "MD"
                                                     :PostalCode "20774"
                                                     :Country "U.S.A."}]}
                   :GroupName "NSIDC_IceBridge"]
   :RelatedUrls [{:Description "Contact group related url description"
                  :URLContentType "DistributionURL"
                  :Type "GET DATA"
                  :Subtype "ECHO"
                  :URL "www.contact.group.foo.com"
                  :GetData {:Size 10.0
                            :Unit "MB"
                            :Format "Not provided"}}
                 {:Description "Contact group related url description"
                  :URLContentType "DistributionURL"
                  :Type "GET SERVICE"
                  :Subtype "ECHO"
                  :URL "www.contact.group.foo.com"
                  :GetService {:MimeType "application/html"
                               :FullName "Not provided"
                               :DataID "Not provided"
                               :Protocol "Not provided"}}]
   :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                     :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                     :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                         :URLContentType "DataContactURL"
                                                         :Type "HOME PAGE"
                                                         :URL "www.contact.foo.com"}
                                                        {:Description "Contact related url description"
                                                         :URLContentType "DataContactURL"
                                                         :Type "HOME PAGE"
                                                         :URL "www.contact.shoo.com"}]
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
   :DataCenters [{:Roles ["ORIGINATOR"]
                  :ShortName "LPDAAC"
                  :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                                    :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                                    :ContactInformation {:RelatedUrls [{:Description "Contact related url description"
                                                                        :URLContentType "DataContactURL"
                                                                        :Type "HOME PAGE"
                                                                        :URL "www.contact.shoo.com"}
                                                                       {:Description "Contact related url description"
                                                                        :URLContentType "DataContactURL"
                                                                        :Type "HOME PAGE"
                                                                        :URL "www.contact.shoo.com"}]
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
                                                                        :URLContentType "DataContactURL"
                                                                        :Type "HOME PAGE"
                                                                        :URL "www.contact.shoo.com"}
                                                                       {:Description "Contact related url description"
                                                                        :URLContentType "DataContactURL"
                                                                        :Type "HOME PAGE"
                                                                        :URL "www.contact.shoo.com"}]
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
                                                      :URLContentType "DataCentertURL"
                                                      :Type "HOME PAGE"
                                                      :URL "www.contact.foo.com"}
                                                     {:Description "Contact related url description"
                                                      :URLContentType "DataCentertURL"
                                                      :Type "HOME PAGE"
                                                      :URL "www.contact.shoo.com"}]
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
                                                                       :URLContentType "DataContactURL"
                                                                       :Type "HOME PAGE"
                                                                       :URL "www.contact.group.foo.com"}]
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
                  :LongName "processor.processor"}]})

(def umm-1-8-collection
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
   :ContactGroups [{:Roles ["Investigator"]
                    :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc888"
                    :ContactInformation {:RelatedUrls [{:Title "Zounds! What a great title!"
                                                        :Description "Contact group related url description"
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
                    :GroupName "NSIDC_IceBridge"}]
   :RelatedUrls [{:Description "Contact group related url description"
                  :Title "Just when you thought titles couldn't get any better"
                  :Relation ["VIEW RELATED INFORMATION" "USER FEEDBACK"]
                  :URLs ["www.contact.group.foo.com"]
                  :MimeType "application/html"}
                 {:Description "Contact group related url description"
                  :Title "Just when you thought titles couldn't get any better"
                  :Relation ["GET DATA"]
                  :URLs ["www.contact.group.foo.com" "www.google.com"]
                  :MimeType "application/html"
                  :FileSize {:Size 10.0 :Unit "MB"}}
                 {:Description "Contact group related url description"
                  :Title "Just when you thought titles couldn't get any better"
                  :Relation ["GET SERVICE"]
                  :URLs ["www.contact.group.foo.com" "www.google.com"]
                  :MimeType "application/html"}
                 {:Description "Contact group related url description"
                  :Title "Just when you thought titles couldn't get any better"
                  :Relation ["INVALID" "X"]
                  :URLs ["www.contact.group.foo.com"]
                  :MimeType "application/html"}]
   :ContactPersons [{:Roles ["Data Center Contact" "Technical Contact" "Science Contact"]
                     :Uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"
                     :ContactInformation {:RelatedUrls [{:Title "Contact Title"
                                                         :Description "Contact related url description"
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
                  :LongName "processor.processor"}]})

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions ["1.0" "1.1" "1.2" "1.3" "1.9" "1.10"]}
    (is (= [] (#'vm/version-steps "1.2" "1.2")))
    (is (= [["1.1" "1.2"] ["1.2" "1.3"] ["1.3" "1.9"] ["1.9" "1.10"]] (#'vm/version-steps "1.1" "1.10")))
    (is (= [["1.9" "1.10"]] (#'vm/version-steps "1.9" "1.10")))
    (is (= [["1.10" "1.9"]] (#'vm/version-steps "1.10" "1.9")))
    (is (= [["1.10" "1.9"] ["1.9" "1.3"] ["1.3" "1.2"] ["1.2" "1.1"] ["1.1" "1.0"]] (#'vm/version-steps "1.10" "1.0")))))

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
                                                        {:RelatedUrl {:URLs ["www.foo.com"]}}]})]

    ;; DOI is moved from :CollectionCitations to :DOI
    ;; RelatedUrl is moved to :OnlineResource
    (is (= {:Authority ";'", :DOI "F19,L"} (:DOI result)))
    (is (= [{:SeriesName ">np", :Creator "^", :ReleasePlace ";CUhWxe", :Title "u8,#XJA4U=",
             :Publisher nil, :ReleaseDate nil, :IssueIdentification nil,
             :Editor nil, :DataPresentationForm nil, :Version nil, :OtherCitationDetails nil
             :OnlineResource {:Linkage "www.google.com" :Name "URL Title" :Description "URL Description"}}]
           (:CollectionCitations result)))
    ;; PublicationReferences Related URL migrates to Online Resource
    (is (= [{:OnlineResource {:Linkage "www.google.com" :Name "URL Title" :Description "URL Description"}}
            {:OnlineResource {:Linkage "www.foo.com" :Name u/not-provided :Description u/not-provided}}]
           (:PublicationReferences result)))))

(deftest migrate-1-8-related-urls-up-to-1-9
  (let [result (vm/migrate-umm {} :collection "1.8" "1.9" umm-1-8-collection)]
    (let [data-center-contact-persons (:RelatedUrls (first (map :ContactInformation (first (map :ContactPersons (:DataCenters result))))))
          data-center-contact-groups (:RelatedUrls (:ContactInformation (first (second (next (map :ContactGroups (:DataCenters result)))))))
          data-center-contact-information (:RelatedUrls (second (next (map :ContactInformation (:DataCenters result)))))
          collection-contact-groups (:RelatedUrls (:ContactInformation (first (:ContactGroups result))))
          collection-contact-persons (:RelatedUrls (:ContactInformation (first (:ContactPersons result))))
          collection-urls (:RelatedUrls result)]
      (is (= [{:Description "Contact group related url description"
               :URLContentType "PublicationURL"
               :Type "VIEW RELATED INFORMATION"
               :Subtype "USER FEEDBACK"
               :URL "www.contact.group.foo.com"}
              {:Description "Contact group related url description"
               :URLContentType "DistributionURL"
               :Type "GET DATA"
               :Subtype nil
               :URL "www.contact.group.foo.com"
               :GetData {:Size 10.0
                         :Unit "MB"
                         :Format "Not provided"}}
              {:Description "Contact group related url description"
               :URLContentType "DistributionURL"
               :Type "GET DATA"
               :Subtype nil
               :URL "www.google.com"
               :GetData {:Size 10.0
                         :Unit "MB"
                         :Format "Not provided"}}
              {:Description "Contact group related url description"
               :URLContentType "DistributionURL"
               :Type "GET SERVICE"
               :Subtype nil
               :URL "www.contact.group.foo.com"
               :GetService {:MimeType "application/html"
                            :FullName "Not provided"
                            :DataID "Not provided"
                            :DataType "Not provided"
                            :Protocol "Not provided"}}
              {:Description "Contact group related url description"
               :URLContentType "DistributionURL"
               :Type "GET SERVICE"
               :Subtype nil
               :URL "www.google.com"
               :GetService {:MimeType "application/html"
                            :FullName "Not provided"
                            :DataType "Not provided"
                            :DataID "Not provided"
                            :Protocol "Not provided"}}
              {:Description "Contact group related url description"
               :URLContentType "PublicationURL"
               :Type "VIEW RELATED INFORMATION"
               :Subtype "GENERAL DOCUMENTATION"
               :URL "www.contact.group.foo.com"}]
             collection-urls))
      (is (= [{:URL "www.contact.foo.com"
               :Description "Contact related url description"
               :URLContentType "DataContactURL"
               :Type "HOME PAGE"}
              {:Description "Contact related url description"
               :URL "www.contact.shoo.com"
               :URLContentType "DataContactURL"
               :Type "HOME PAGE"}]
             data-center-contact-persons))
      (is (= [{:Description "Contact group related url description"
               :URL "www.contact.group.foo.com"
               :URLContentType "DataContactURL"
               :Type "HOME PAGE"}]
             data-center-contact-groups))
      (is (= [{:URL "www.contact.foo.com",
               :Description "Contact related url description"
               :URLContentType "DataCenterURL"
               :Type "HOME PAGE"}
              {:Description "Contact related url description",
               :URL "www.contact.shoo.com"
               :URLContentType "DataCenterURL"
               :Type "HOME PAGE"}]
             data-center-contact-information))
      (is (= [{:URL "www.contact.foo.com",
               :Description "Contact related url description"
               :URLContentType "DataContactURL"
               :Type "HOME PAGE"}
              {:Description "Contact related url description",
               :URL "www.contact.shoo.com"
               :URLContentType "DataContactURL"
               :Type "HOME PAGE"}]
             collection-contact-persons))
      (is (= [{:URL "www.contact.group.foo.com",
               :Description "Contact group related url description"
               :URLContentType "DataContactURL"
               :Type "HOME PAGE"}]
             collection-contact-groups)))))

(deftest migrate-1_8-spatial-extent-up-to-1_9_all
  (let [result (vm/migrate-umm {} :collection "1.8" "1.9"
                               {:SpatialExtent {:HorizontalSpatialDomain {:Geometry {:CoordinateSystem "CARTESIAN"
                                                                                     :BoundingRectangles [{:CenterPoint {:Longitude "0"
                                                                                                                         :Latitude "0"}
                                                                                                           :WestBoundingCoordinate "0"
                                                                                                           :NorthBoundingCoordinate "0"
                                                                                                           :EastBoundingCoordinate "0"
                                                                                                           :SouthBoundingCoordinate "0"}
                                                                                                          {:CenterPoint {:Longitude "0"
                                                                                                                         :Latitude "0"}
                                                                                                           :WestBoundingCoordinate "0"
                                                                                                           :NorthBoundingCoordinate "0"
                                                                                                           :EastBoundingCoordinate "0"
                                                                                                           :SouthBoundingCoordinate "0"}]
                                                                                     :GPolygons [{:CenterPoint {:Longitude "0"
                                                                                                                :Latitude "0"}
                                                                                                  :Boundary {:Points [{:Longitude "-10", :Latitude "-10"}
                                                                                                                      {:Longitude "10", :Latitude "-10"}
                                                                                                                      {:Longitude "10", :Latitude "10"}
                                                                                                                      {:Longitude "-10", :Latitude "10"}
                                                                                                                      {:Longitude "-10", :Latitude "-10"}]}}]
                                                                                     :Lines [{:CenterPoint {:Longitude "0"
                                                                                                            :Latitude "0"}
                                                                                              :Points [{:Longitude "-10", :Latitude "-10"}
                                                                                                       {:Longitude "10", :Latitude "-10"}]}]}}
                                                :GranuleSpatialRepresentation "NO_SPATIAL"}})]

    (is (= {:HorizontalSpatialDomain {:Geometry {:CoordinateSystem "CARTESIAN"
                                                 :BoundingRectangles [{:WestBoundingCoordinate "0"
                                                                       :NorthBoundingCoordinate "0"
                                                                       :EastBoundingCoordinate "0"
                                                                       :SouthBoundingCoordinate "0"}
                                                                      {:WestBoundingCoordinate "0"
                                                                       :NorthBoundingCoordinate "0"
                                                                       :EastBoundingCoordinate "0"
                                                                       :SouthBoundingCoordinate "0"}]
                                                 :GPolygons [{:Boundary {:Points [{:Longitude "-10", :Latitude "-10"}
                                                                                  {:Longitude "10", :Latitude "-10"}
                                                                                  {:Longitude "10", :Latitude "10"}
                                                                                  {:Longitude "-10", :Latitude "10"}
                                                                                  {:Longitude "-10", :Latitude "-10"}]}}]
                                                 :Lines [{:Points [{:Longitude "-10", :Latitude "-10"}
                                                                   {:Longitude "10", :Latitude "-10"}]}]}}
            :GranuleSpatialRepresentation "NO_SPATIAL"}
           (:SpatialExtent result)))))

(deftest migrate-1_8-spatial-extent-up-to-1_9_without_some_centerpoints
  (let [result (vm/migrate-umm {} :collection "1.8" "1.9"
                               {:SpatialExtent {:HorizontalSpatialDomain {:Geometry {:CoordinateSystem "CARTESIAN"
                                                                                     :BoundingRectangles [{:WestBoundingCoordinate "0"
                                                                                                           :NorthBoundingCoordinate "0"
                                                                                                           :EastBoundingCoordinate "0"
                                                                                                           :SouthBoundingCoordinate "0"}
                                                                                                          {:WestBoundingCoordinate "0"
                                                                                                           :NorthBoundingCoordinate "0"
                                                                                                           :EastBoundingCoordinate "0"
                                                                                                           :SouthBoundingCoordinate "0"}]
                                                                                     :GPolygons [{:CenterPoint {:Longitude "0"
                                                                                                                :Latitude "0"}
                                                                                                  :Boundary {:Points [{:Longitude "-10", :Latitude "-10"}
                                                                                                                      {:Longitude "10", :Latitude "-10"}
                                                                                                                      {:Longitude "10", :Latitude "10"}
                                                                                                                      {:Longitude "-10", :Latitude "10"}
                                                                                                                      {:Longitude "-10", :Latitude "-10"}]}}]
                                                                                     :Lines [{:CenterPoint {:Longitude "0"
                                                                                                            :Latitude "0"}
                                                                                              :Points [{:Longitude "-10", :Latitude "-10"}
                                                                                                       {:Longitude "10", :Latitude "-10"}]}]}}
                                                :GranuleSpatialRepresentation "NO_SPATIAL"}})]

    (is (= {:HorizontalSpatialDomain {:Geometry {:CoordinateSystem "CARTESIAN"
                                                 :BoundingRectangles [{:WestBoundingCoordinate "0"
                                                                       :NorthBoundingCoordinate "0"
                                                                       :EastBoundingCoordinate "0"
                                                                       :SouthBoundingCoordinate "0"}
                                                                      {:WestBoundingCoordinate "0"
                                                                       :NorthBoundingCoordinate "0"
                                                                       :EastBoundingCoordinate "0"
                                                                       :SouthBoundingCoordinate "0"}]
                                                 :GPolygons [{:Boundary {:Points [{:Longitude "-10", :Latitude "-10"}
                                                                                  {:Longitude "10", :Latitude "-10"}
                                                                                  {:Longitude "10", :Latitude "10"}
                                                                                  {:Longitude "-10", :Latitude "10"}
                                                                                  {:Longitude "-10", :Latitude "-10"}]}}]
                                                 :Lines [{:Points [{:Longitude "-10", :Latitude "-10"}
                                                                   {:Longitude "10", :Latitude "-10"}]}]}}
            :GranuleSpatialRepresentation "NO_SPATIAL"}
           (:SpatialExtent result)))))

(deftest migrate-1_8-spatial-extent-up-to-1_9_without_gpolygon
  (let [result (vm/migrate-umm {} :collection "1.8" "1.9"
                               {:SpatialExtent {:HorizontalSpatialDomain {:Geometry {:CoordinateSystem "CARTESIAN"
                                                                                     :BoundingRectangles [{:WestBoundingCoordinate "0"
                                                                                                           :NorthBoundingCoordinate "0"
                                                                                                           :EastBoundingCoordinate "0"
                                                                                                           :SouthBoundingCoordinate "0"}
                                                                                                          {:WestBoundingCoordinate "0"
                                                                                                           :NorthBoundingCoordinate "0"
                                                                                                           :EastBoundingCoordinate "0"
                                                                                                           :SouthBoundingCoordinate "0"}]
                                                                                     :Lines [{:CenterPoint {:Longitude "0"
                                                                                                            :Latitude "0"}
                                                                                              :Points [{:Longitude "-10", :Latitude "-10"}
                                                                                                       {:Longitude "10", :Latitude "-10"}]}]}}
                                                :GranuleSpatialRepresentation "NO_SPATIAL"}})]

    (is (= {:HorizontalSpatialDomain {:Geometry {:CoordinateSystem "CARTESIAN"
                                                 :BoundingRectangles [{:WestBoundingCoordinate "0"
                                                                       :NorthBoundingCoordinate "0"
                                                                       :EastBoundingCoordinate "0"
                                                                       :SouthBoundingCoordinate "0"}
                                                                      {:WestBoundingCoordinate "0"
                                                                       :NorthBoundingCoordinate "0"
                                                                       :EastBoundingCoordinate "0"
                                                                       :SouthBoundingCoordinate "0"}]
                                                 :Lines [{:Points [{:Longitude "-10", :Latitude "-10"}
                                                                   {:Longitude "10", :Latitude "-10"}]}]}}
            :GranuleSpatialRepresentation "NO_SPATIAL"}
           (:SpatialExtent result)))))

(deftest migrate-1_8-spatial-extent-up-to-1_9_without_horizontal
  (let [result (vm/migrate-umm {} :collection "1.8" "1.9"
                               {:SpatialExtent {:VerticalSpatialDomain {}}})]

    (is (= {:VerticalSpatialDomain {}}
           (:SpatialExtent result)))))

(deftest migrate-1-9-down-to-1-8
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
           (:PublicationReferences result)))
    ;; Default RelatedUrls is added
    (is (= [u/not-provided-related-url] (:RelatedUrls result)))))

(deftest migrate-1_8-instruments-up-to-1_9
  (let [result (vm/migrate-umm {} :collection "1.8" "1.9"
                               {:Platforms [{:Instruments [{:ShortName "Inst1"
                                                            :NumberOfSensors 5
                                                            :Sensors [{:ShortName "Sensor 1"}
                                                                      {:ShortName "Sensor 2"}]}
                                                           {:ShortName "Inst2"
                                                            :NumberOfSensors 5
                                                            :Sensors [{:ShortName "Sensor 1"}
                                                                      {:ShortName "Sensor 2"}]}]}]})]
    (is (= [{:Instruments [{:ShortName "Inst1"
                            :NumberOfInstruments 5
                            :ComposedOf [{:ShortName "Sensor 1"}
                                         {:ShortName "Sensor 2"}]}
                           {:ShortName "Inst2"
                            :NumberOfInstruments 5
                            :ComposedOf [{:ShortName "Sensor 1"}
                                         {:ShortName "Sensor 2"}]}]}]
           (:Platforms result)))))

(deftest migrate-1_9-instruments-down-to-1_8
  (let [result (vm/migrate-umm {} :collection "1.9" "1.8"
                               {:Platforms [{:Instruments [{:ShortName "Inst1"
                                                            :NumberOfInstruments 5
                                                            :ComposedOf [{:ShortName "Sensor 1"}
                                                                         {:ShortName "Sensor 2"}]}
                                                           {:ShortName "Inst2"
                                                            :NumberOfInstruments 5
                                                            :ComposedOf [{:ShortName "Sensor 1"}
                                                                         {:ShortName "Sensor 2"}]}]}]})]
    (is (= [{:Instruments [{:ShortName "Inst1"
                            :NumberOfSensors 5
                            :Sensors [{:ShortName "Sensor 1"}
                                      {:ShortName "Sensor 2"}]}
                           {:ShortName "Inst2"
                            :NumberOfSensors 5
                            :Sensors [{:ShortName "Sensor 1"}
                                      {:ShortName "Sensor 2"}]}]}]
           (:Platforms result)))))

(deftest migrate-1-9-related-urls-down-to-1-8
  (let [result (vm/migrate-umm {} :collection "1.9" "1.8" umm-1-9-related-urls)
        data-center-contact-persons (get-in (first (:ContactPersons (first (:DataCenters result)))) [:ContactInformation :RelatedUrls])
        data-center-contact-groups (get-in (first (:ContactGroups (nth (:DataCenters result) 2))) [:ContactInformation :RelatedUrls])
        collection-contact-persons (get-in (first (:ContactPersons result)) [:ContactInformation :RelatedUrls])]
    (is (= [{:Description "Contact group related url description"
             :FileSize {:Unit "MB"
                        :Size 10.0}
             :Relation ["GET DATA" "ECHO"]
             :URLs ["www.contact.group.foo.com"]}
            {:Description "Contact group related url description"
                     :Relation ["GET SERVICE" "ECHO"]
                     :URLs ["www.contact.group.foo.com"]
             :MimeType "application/html"}]
           (:RelatedUrls result)))
    (is (= [{:URLs ["www.contact.shoo.com"],
             :Description "Contact related url description"}
            {:URLs ["www.contact.shoo.com"],
             :Description "Contact related url description"}]
           data-center-contact-persons))
    (is (= [{:URLs ["www.contact.group.foo.com"],
             :Description "Contact group related url description"}]
           data-center-contact-groups))
    (is (= [{:URLs ["www.contact.foo.com"],
             :Description "Contact related url description"}
            {:URLs ["www.contact.shoo.com"],
             :Description "Contact related url description"}]
           collection-contact-persons))))

(deftest migrate-1_9-up-to-1_10
  (is (= u/NOT-PROVIDED
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.9" "1.10"
                         {:CollectionProgress "ACTIVE"}))))
  (is (= "PLANNED"
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.9" "1.10"
                         {:CollectionProgress "planned"}))))
  (is (= "ACTIVE"
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.9" "1.10"
                         {:CollectionProgress "IN WORK"}))))
  (is (= u/NOT-PROVIDED
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.9" "1.10"
                         {:CollectionProgress "NOT PROVIDED"}))))
  (is (= "NOT APPLICABLE"
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.9" "1.10"
                         {:CollectionProgress "NOT APPLICABLE"}))))
  (is (= "COMPLETE"
         (:CollectionProgress
           (vm/migrate-umm {} :collection "1.9" "1.10"
                          {:CollectionProgress "COMPLETE"})))))

(deftest migrate-1_10-down-to-1_9
  (is (= "PLANNED"
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.10" "1.9"
                         {:CollectionProgress "PLANNED"}))))
  (is (= "IN WORK"
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.10" "1.9"
                         {:CollectionProgress "ACTIVE"}))))
  (is (= u/NOT-PROVIDED
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.10" "1.9"
                         {:CollectionProgress "NOT PROVIDED"}))))
  (is (= "NOT APPLICABLE"
        (:CollectionProgress
          (vm/migrate-umm {} :collection "1.10" "1.9"
                         {:CollectionProgress "NOT APPLICABLE"}))))
  (is (= "COMPLETE"
         (:CollectionProgress
           (vm/migrate-umm {} :collection "1.10" "1.9"
                          {:CollectionProgress "COMPLETE"})))))
(deftest migrate-1-9-tiling-identification-systems-to-1-10
  (let [tiling-id-systems {:TilingIdentificationSystems
                           [{:TilingIdentificationSystemName "MISR"
                             :Coordinate1 {:MinimumValue 1.0
                                           :MaximumValue 10.0}
                             :Coordinate2 {:MinimumValue 1.0
                                           :MaximumValue 10.0}}
                            {:TilingIdentificationSystemName "Heat Miser"
                              :Coordinate1 {:MinimumValue 11.0
                                            :MaximumValue 20.0}
                              :Coordinate2 {:MinimumValue 11.0
                                            :MaximumValue 20.0}}]}
        result (vm/migrate-umm {} :collection "1.9" "1.10" tiling-id-systems)
        other-result (vm/migrate-tiling-identification-systems tiling-id-systems)]
    (proto-repl.saved-values/save 8)
    (is (= result
           {:CollectionCitations nil,
            :Personnel
            [{:Party {:Person {:LastName "Not provided"}},
              :Role "POINTOFCONTACT"}],
            :TilingIdentificationSystems
            [{:Coordinate2 {:MinimumValue 1, :MaximumValue 10},
              :Coordinate1 {:MinimumValue 1, :MaximumValue 10},
              :TilingIdentificationSystemName "MISR"}],
            :PublicationReferences nil,
            :RelatedUrls
            [{:Subtype "GENERAL DOCUMENTATION",
              :GetData nil,
              :URLContentType "PublicationURL",
              :Description nil,
              :Type "VIEW RELATED INFORMATION",
              :URL "Not%20provided",
              :GetService nil}],
            :Organizations [],
            :Platforms nil,
            :PaleoTemporalCoverage nil}))))
