(ns cmr.umm-spec.test.migration.version.service
  (:require
   [cheshire.core :refer [decode]]
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.mime-types :as mt]
   [cmr.common.test.test-check-ext :as ext :refer [defspec]]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.migration.version.service :as service]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :as v]
   [com.gfredericks.test.chuck.clojure-test :refer [for-all]]))

(def service-concept-1-0
  {:RelatedURL {:URLContentType "CollectionURL"
                :Description "OPeNDAP Service"
                :Type "GET SERVICE"
                :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"},
   :Coverage {:Type "SPATIAL_POINT"
              :CoverageSpatialExtent {:Type "SPATIAL_POINT"}}
   :AccessConstraints [(apply str (repeat 1024 "x"))]
   :UseConstraints [(apply str (repeat 1024 "x"))]
   :ServiceQuality {:QualityFlag "Available"
                    :Lineage (apply str (repeat 100 "x"))}})

(def service-concept-1-1
  {:Coverage {:CoverageSpatialExtent {:CoverageSpatialExtentTypeType "SPATIAL_POINT"}}
   :AccessConstraints "TEST"
   :UseConstraints "TEST"
   :ServiceOrganizations [{:Roles ["SERVICE PROVIDER"]
                           :ShortName "TEST ShortName"}]
   :RelatedURLs [{:URLContentType "CollectionURL"
                  :Description "OPeNDAP Service"
                  :Type "GET SERVICE"
                  :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}]})

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions {:service ["1.0" "1.1"]}}
    (is (= [] (#'vm/version-steps :service "1.1" "1.1")))
    (is (= [["1.0" "1.1"]] (#'vm/version-steps :service "1.0" "1.1")))
    (is (= [["1.1" "1.0"]] (#'vm/version-steps :service "1.1" "1.0")))))

(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record   (gen/no-shrink umm-gen/umm-var-generator)
            dest-version (gen/elements (v/versions :service))]
    (let [dest-media-type (str mt/umm-json "; version=" dest-version)
          metadata (core/generate-metadata (lkt/setup-context-for-test)
                                           umm-record dest-media-type)]
      (empty? (core/validate-metadata :service dest-media-type metadata)))))

(deftest migrate-1_0-up-to-1_1
  (is (= service-concept-1-1
         (vm/migrate-umm {} :service "1.0" "1.1"
           {:Coverage {:Type "SPATIAL_POINT"}
            :AccessConstraints ["TEST"]
            :UseConstraints ["TEST"]
            :ServiceOrganizations [{:Roles ["SERVICE PROVIDER"]
                                    :ShortName "TEST ShortName"
                                    :Uuid "TEST Uuid"}]
            :RelatedURL {:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/" :Description "OPeNDAP Service"
                         :Type "GET SERVICE"
                         :URLContentType "CollectionURL"}}))))

(deftest migrate-1_1-down-to-1_0
  (is (= service-concept-1-0
         (vm/migrate-umm {} :service "1.1" "1.0"
           {:RelatedURLs [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/" :Description "OPeNDAP Service"
                           :Type "GET SERVICE"
                           :URLContentType "CollectionURL"}]
            :AccessConstraints (apply str (repeat 4000 "x"))
            :UseConstraints (apply str (repeat 20000 "x"))
            :ServiceQuality {:QualityFlag "Available"
                             :Lineage (apply str (repeat 4000 "x"))}
            :Coverage {:CoverageSpatialExtent {:CoverageSpatialExtentTypeType
                                               "SPATIAL_POINT"}}}))))

(deftest migrate-service-options-1_1-up-to-1_2
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ServiceOptions {:SubsetTypes [ "Spatial", "Variable"]
                           :SupportedInputProjections [{:ProjectionName "Geographic"}]
                           :SupportedOutputProjections [{:ProjectionName "Geographic"}]
                           :SupportedInputFormats ["BINARY" "HDF4" "NETCDF-3" "HDF-EOS2"]
                           :SupportedOutputFormats ["BINARY" "HDF4" "NETCDF-3" "HDF-EOS2"]}
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"}]}
         (vm/migrate-umm
          {} :service "1.1" "1.2"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ServiceOptions {:SubsetTypes [ "Spatial" "Variable"]
                            :SupportedProjections [ "Geographic"]
                            :SupportedFormats ["Binary" "HDF4" "netCDF-3" "HDF-EOS4"]}
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"}]}))))

(deftest migrate-service-options-1_2-down-to-1_1
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ServiceOptions {:SubsetTypes [ "Spatial" "Variable"]
                           :SupportedProjections [ "Geographic"]
                           :SupportedFormats ["Binary" "HDF4" "HDF-EOS4" "HDF-EOS5"]}
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"}]}
         (vm/migrate-umm
          {} :service "1.2" "1.1"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ServiceOptions {:SubsetTypes [ "Spatial", "Variable"]
                            :SupportedInputProjections [{:ProjectionName "Geographic"}]
                            :SupportedOutputProjections [{:ProjectionName "Geographic"}]
                            :SupportedInputFormats ["BINARY" "HDF4" "HDF-EOS2" "HDF-EOS" "KML"]
                            :SupportedOutputFormats ["BINARY" "HDF4" "NETCDF-3" "HDF-EOS4"]}
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"}]}))))

(deftest migrate-contact-groups-1_1-up-to-1_2
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ContactGroups [{:Roles [ "INVESTIGATOR"]
                           :GroupName "I TEAM"}]
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"
                                  :ContactGroups [{:Roles [ "DEVELOPER"]
                                                   :GroupName "D TEAM"}]}]}
         (vm/migrate-umm
          {} :service "1.1" "1.2"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ContactGroups [{:Roles [ "INVESTIGATOR"]
                            :Uuid "74a1f32f-ca06-489b-bd61-4ce85872df9c"
                            :NonServiceOrganizationAffiliation "MSFC"
                            :GroupName "I TEAM"}]
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"
                                   :ContactGroups [{:Roles [ "DEVELOPER"]
                                                    :Uuid "86a1f32f-ca06-489b-bd61-4ce85872df08"
                                                    :NonServiceOrganizationAffiliation "GSFC"
                                                    :GroupName "D TEAM"}]}]}))))

(deftest migrate-contact-groups-1_2-down-to-1_1
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ContactGroups [{:Roles [ "INVESTIGATOR"]
                           :GroupName "I TEAM"}]
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"
                                  :ContactGroups [{:Roles [ "DEVELOPER"]
                                                   :GroupName "D TEAM"}]}]}
         (vm/migrate-umm
          {} :service "1.2" "1.1"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ContactGroups [{:Roles [ "INVESTIGATOR"]
                            :GroupName "I TEAM"}]
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"
                                   :ContactGroups [{:Roles [ "DEVELOPER"]
                                                    :GroupName "D TEAM"}]}]}))))

(deftest migrate-main-fields-1_1-up-to-1_2
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"}]}
         (vm/migrate-umm
          {} :service "1.1" "1.2"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"}]
           :OnlineAccessURLPatternMatch "abc*"
           :OnlineAccessURLPatternSubstitution "dummy_pattern"
           :Coverage {:Name "dummy"}}))))

(deftest migrate-main-fields-1_2-down-to-1_1
  (is (= {:Type "WEB SERVICES"
          :LongName (apply str (repeat 120 "x"))
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"}]}
         (vm/migrate-umm
          {} :service "1.2" "1.1"
          {:Type "ESI"
           :LongName (apply str (repeat 200 "x"))
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"}]
           :OperationMetadata []}))))

(deftest create-main-url-for-v1-3-test
  "Test the create-main-url-for-1_3 function"

  (are3 [expected-result related-urls]
    (is (= expected-result
           (service/create-main-url-for-1_3 related-urls)))

    "Replace the RelatedURLs with the first DistributionURL."
    {:Description "OPeNDAP Service for AIRX3STD.006"
     :URLContentType "DistributionURL"
     :Type "GET SERVICE"
     :Subtype "OPENDAP DATA"
     :URLValue "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}
    {:RelatedURLs
      [{:Description "User Guide"
        :URLContentType "PublicationURL"
        :Type "VIEW RELATED INFORMATION"
        :Subtype "USER'S GUIDE"
        :URL "http://docserver.gesdisc.eosdis.nasa.gov/repository/Mission/AIRS/3.3_ScienceDataProductDocumentation/3.3.4_ProductGenerationAlgorithms/V6_L3_User_Guide.pdf"}
       {:Description "OPeNDAP Service for AIRX3STD.006"
        :URLContentType "DistributionURL"
        :Type "GET SERVICE"
        :Subtype "OPENDAP DATA"
        :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}
       {:Description "User Guide"
        :URLContentType "PublicationURL"
        :Type "VIEW RELATED INFORMATION"
        :Subtype "USER'S GUIDE"
        :URL "http://docserver.gesdisc.eosdis.nasa.gov/repository/Mission/AIRS/3.3_ScienceDataProductDocumentation/3.3.4_ProductGenerationAlgorithms/V6_L3_User_Guide.pdf"}]}

    "Since DistributionURL doesn't exist nil is returned."
    nil
    {:RelatedURLs
      [{:Description "User Guide"
        :URLContentType "PublicationURL"
        :Type "VIEW RELATED INFORMATION"
        :Subtype "USER'S GUIDE"
        :URL "http://docserver.gesdisc.eosdis.nasa.gov/repository/Mission/AIRS/3.3_ScienceDataProductDocumentation/3.3.4_ProductGenerationAlgorithms/V6_L3_User_Guide.pdf"}
       {:Description "User Guide"
        :URLContentType "PublicationURL"
        :Type "VIEW RELATED INFORMATION"
        :Subtype "USER'S GUIDE"
        :URL "http://docserver.gesdisc.eosdis.nasa.gov/repository/Mission/AIRS/3.3_ScienceDataProductDocumentation/3.3.4_ProductGenerationAlgorithms/V6_L3_User_Guide.pdf"}]}))

(deftest create-main-url-for-v1-2-test
  "Test the create-main-url-for-1_2 function"

  (are3 [expected-result url]
    (is (= expected-result
           (service/create-main-related-urls-for-1_2 url)))

    "Replace the URL sub element with those from RelatedURL."
    [{:Description "OPeNDAP Service for AIRX3STD.006"
      :URLContentType "DistributionURL"
      :Type "GET SERVICE"
      :Subtype "OPENDAP DATA"
      :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}]
    {:URL {:Description "OPeNDAP Service for AIRX3STD.006"
           :URLContentType "DistributionURL"
           :Type "GET SERVICE"
           :Subtype "OPENDAP DATA"
           :URLValue "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}}


    "Since there are no RelatedURLs none should come back."
    nil
    nil))

(def remove-get-data-service-1-2->1-3-test-input
  {:Roles ["SCIENCE CONTACT"]
   :ContactInformation {:RelatedUrls [{:Description "OPeNDAP Service for AIRX3STD.006"
                                       :URLContentType "DistributionURL"
                                       :Type "GET SERVICE"
                                       :Subtype "OPENDAP DATA"
                                       :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                                       :GetData {:Format "ascii"
                                                 :MimeType "application/xml"
                                                 :Size 10
                                                 :Unit "MB"
                                                 :Fees "$0.01"}}]
                        :ContactMechanisms [{:Type "Email"
                                             :Value "gsfc-help-disc at lists.nasa.gov"}
                                            {:Type "Telephone" :Value "301-614-9999"}]
                        :Addresses [{:StreetAddresses ["Goddard Earth Sciences Data and Information Systems" "Attn: User" "NASA Goddard Space Flight Center" "Code 610.2"]
                                     :City "Greenbelt"
                                     :StateProvince "MD"
                                     :Country "USA"
                                     :PostalCode "20771"}]}
   :GroupName "Main Level Group Name 1"})

(def remove-get-data-service-1-2->1-3-test-expected
  {:Roles ["SCIENCE CONTACT"]
   :ContactInformation {:RelatedUrls [{:Description "OPeNDAP Service for AIRX3STD.006"
                                       :URLContentType "DistributionURL"
                                       :Type "GET SERVICE"
                                       :Subtype "OPENDAP DATA"
                                       :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}]
                        :ContactMechanisms [{:Type "Email"
                                             :Value "gsfc-help-disc at lists.nasa.gov"}
                                            {:Type "Telephone" :Value "301-614-9999"}]
                        :Addresses [{:StreetAddresses ["Goddard Earth Sciences Data and Information Systems" "Attn: User" "NASA Goddard Space Flight Center" "Code 610.2"]
                                     :City "Greenbelt"
                                     :StateProvince "MD"
                                     :Country "USA"
                                     :PostalCode "20771"}]}
   :GroupName "Main Level Group Name 1"})

(deftest remove-get-data-service-1-2->1-3-test
  "Test the remove-get-data-service-1-2->1-3 function"

  (are3 [expected-result contact]
    (is (= expected-result
           (service/remove-get-data-service-1-2->1-3 contact)))

    "Remove the GetService from the ContactGroups RelatedUrls element."
    remove-get-data-service-1-2->1-3-test-expected
    remove-get-data-service-1-2->1-3-test-input))

(def service-org-contact-groups-v2
 '({:Roles ["SCIENCE CONTACT"],
    :ContactInformation
    {:RelatedUrls
     ({:Description "OPeNDAP Service for AIRX3STD.006",
       :URLContentType "DistributionURL",
       :Type "GET SERVICE",
       :Subtype "OPENDAP DATA",
       :URL
       "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}),
     :ContactMechanisms
     [{:Type "Email",
       :Value "gsfc-help-disc at lists.nasa.gov"}
      {:Type "Telephone", :Value "301-614-9999"}],
     :Addresses
     [{:StreetAddresses
       ["Goddard Earth Sciences Data and Information Systems, Attn: User , NASA Goddard Space Flight Center, Code 610.2"],
       :City "Greenbelt",
       :StateProvince "MD",
       :Country "USA",
       :PostalCode "20771"}]},
    :GroupName "Service Org Group Name"}
   {:Roles ["TECHNICAL CONTACT"],
    :ContactInformation
    {:ContactMechanisms
     [{:Type "Email",
       :Value "gsfc-help-disc at lists.nasa.gov"}
      {:Type "Telephone", :Value "301-614-9999"}],
     :Addresses
     [{:StreetAddresses
       ["Goddard Earth Sciences Data and Information Systems, Attn: User , NASA Goddard Space Flight Center, Code 610.2"],
       :City "Greenbelt",
       :StateProvince "MD",
       :Country "USA",
       :PostalCode "20771"}]},
    :GroupName "Service Org Group Name"}
   {:Roles ["SCIENCE CONTACT"],
    :ContactInformation
    {:ContactMechanisms
     [{:Type "Email",
       :Value "gsfc-help-disc at lists.nasa.gov"}
      {:Type "Telephone", :Value "301-614-9999"}],
     :Addresses
     [{:StreetAddresses
       ["Goddard Earth Sciences Data and Information Systems, Attn: User , NASA Goddard Space Flight Center, Code 610.2"],
       :City "Greenbelt",
       :StateProvince "MD",
       :Country "USA",
       :PostalCode "20771"}]},
    :GroupName "Service Org 2 Group Name 1"}))

(def service-org-contact-persons-v2
 '({:Roles ["SERVICE PROVIDER"],
    :ContactInformation
    {:RelatedUrls
     ({:Description "OPeNDAP Service for AIRX3STD.006",
       :URLContentType "DistributionURL",
       :Type "GET SERVICE",
       :Subtype "OPENDAP DATA",
       :URL
       "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}),
     :ContactMechanisms
     [{:Type "Email",
       :Value "gsfc-help-disc at lists.nasa.gov"}
      {:Type "Telephone", :Value "301-614-9999"}],
     :Addresses
     [{:StreetAddresses
       ["Goddard Earth Sciences Data and Information Systems, Attn: User , NASA Goddard Space Flight Center, Code 610.2"],
       :City "Greenbelt",
       :StateProvince "MD",
       :Country "USA",
       :PostalCode "20771"}]},
    :FirstName "FirstName Service Org",
    :MiddleName "Service Org MiddleName",
    :LastName "LastName Service Org"}))

(deftest update-service-organization-1-2->1-3-test
  "Test the update-service-organization-1_2->1_3 function"

  (let [s1-2 (decode
               (slurp (io/file (io/resource "example-data/umm-json/service/v1.2/Service_v1.2->v1.3.json")))
               true)
        s1-3 (decode
               (slurp (io/file (io/resource "example-data/umm-json/service/v1.3/Service_v1.3-from-v1.2.json")))
               true)
        serv-orgs [{:Roles ["SERVICE PROVIDER"],
                    :ShortName "NASA/GESDISC",
                    :LongName "GES DISC SERVICE HELP DESK SUPPORT GROUP"}
                   {:Roles ["SERVICE PROVIDER"],
                    :ShortName "NASA/GESDISC-2",
                    :LongName "GES DISC SERVICE HELP DESK SUPPORT GROUP 2"}]]
    (are3 [expected-result test-record]
      (let [actual-result (service/update-service-organization-1_2->1_3 test-record)]
        (is (= (:ServiceOrganizations expected-result)
               (:ServiceOrganizations actual-result)))
        (is (= (:ContactGroups expected-result)
               (:ContactGroups actual-result)))
        (is (= (:ContactPersons expected-result)
               (:ContactPersons actual-result))))

      "Move the ServiceOrganizations ContactGroups and ContactPersons to the main level ContactGroups
       and ContactPersons.
       The input contains 2 ServiceOrganizations. The first ServiceOrganization contains 2 contact
       groups and 1 contact persons. The second has 1 contact group and no contact persons. The main
       level contact groups contains 2 groups and the main level contact persons contains 1 contact
       person.
       In the output there are 2 ServiceOrganizations with no contact information in them. The main
       level contact groups contains 5 contact groups and the main level contact persons contains 2."
      s1-3
      s1-2

      "Tests when ServiceOrganizations do not have any contacts and there no Contact Groups or Persons."
      (-> s1-3
          (assoc :ServiceOrganizations serv-orgs)
          (dissoc :ContactGroups)
          (dissoc :ContactPersons))
      (-> s1-2
          (assoc :ServiceOrganizations serv-orgs)
          (dissoc :ContactGroups)
          (dissoc :ContactPersons))

      "Tests when no main level contact persons or groups exist"
      (-> s1-3
          (assoc :ContactGroups service-org-contact-groups-v2)
          (assoc :ContactPersons service-org-contact-persons-v2))
      (-> s1-2
          (dissoc :ContactGroups)
          (dissoc :ContactPersons)))))

(deftest create-online-resource-test
  "Test the create-online-resource function."

  (are3 [expected-result serv-orgs]
    (is (= expected-result
           (service/create-online-resource serv-orgs)))

    "Test getting the first ContactInformation RelatedURLs where the URLContentType is DataCenterURL.
     The output is an OnlineResource structure."
    {:Linkage "https://daacscenter1.org"
     :Description "A description"
     :Name "HOME PAGE"}
    {:ContactInformation {:ServiceHours "1-4"
                          :RelatedUrls [{:URLContentType "CollectionURL"
                                         :Type "PROJECT HOME PAGE"
                                         :URL "https://daacscenter1.org"}
                                        {:URLContentType "DataCenterURL"
                                         :Type "HOME PAGE"
                                         :URL "https://daacscenter1.org"
                                         :Description "A description"}]
                          :ContactInstruction "instructions"}}

    "Tests When ContactInformation don't exist."
    nil
    nil

    "Tests when I don't have any RelatedURLs."
    nil
    {:ContactInformation {:ServiceHours "1-4",
                          :ContactInstruction "instructions"}}))

(deftest update-service-organization-1-3->1-2-test
  "Test the update-service-organization-1_3->1_2 function"

  (let [s1-2 (decode
               (slurp (io/file (io/resource "example-data/umm-json/service/v1.2/Service_v1.2-from-v1.3.json")))
               true)
        s1-3 (decode
               (slurp (io/file (io/resource "example-data/umm-json/service/v1.3/Service_v1.3->v1.2.json")))
               true)]
    (are3 [expected-result test-record]
      (let [actual-result (service/update-service-organization-1_3->1_2 test-record)]
        (is (= (:ServiceOrganizations expected-result)
               actual-result)))
      "Add the version 1.3 OnlineResource to ContactInformation RelatedUrls. Remove OnlineResource."
      s1-2
      s1-3)))

(deftest update-service-type-1-3->1-2-test
  "Test the updated-service-type-1_3->1_2 function"

  (are3 [expected-result test-record]
    (is (= expected-result
           (service/update-service-type-1_3->1_2 test-record)))

    "Test that WMTS gets translated to WMTS"
    {:Type "WMS"}
    {:Type "WMTS"}

    "Test that EGI - No Processing is translated to WEB SERVICES"
    {:Type "WEB SERVICES"}
    {:Type "EGI - No Processing"}

    "Testing that other values pass through"
    {:Type "ECHO ORDERS"}
    {:Type "ECHO ORDERS"}))

(defn- load-service-file
  "Load a test data file for services"
  [version-file]
  (decode (->> version-file
              (format "example-data/umm-json/service/%s")
              io/resource
              io/file
              slurp)
          true))

(deftest migrations-up-and-down
  ""
  (are3
   [source-version source-file destination-version destination-file]
   (let [expected (load-service-file destination-file)
         source (load-service-file source-file)
         actual (vm/migrate-umm {} :service source-version destination-version source)]
     (is (= expected actual)))

   ;; ---- 1.3 tests ----
   "Test the full migration of UMM-S from version 1.2 to version 1.3 using predefined example files."
   "1.2" "v1.2/Service_v1.2->v1.3.json"
   "1.3" "v1.3/Service_v1.3-from-v1.2.json"

   "Test the full migration of UMM-S from version 1.3 to version 1.2 using predefined example files."
   "1.3" "v1.3/Service_v1.3->v1.2.json"
   "1.2" "v1.2/Service_v1.2-from-v1.3.json"

   ;; ---- 1.3.1 tests ----
   "Test the full migration of UMM-S from version 1.3 to version 1.3.1 using predefined example files."
   "1.3" "v1.3/Service_v1.3-to-v1.3.1.json"
   "1.3.1" "v1.3.1/Service_v1.3.1-from-v1.3.json"

   "Test the full migration of UMM-S from version 1.3.1 to version 1.3 using predefined example files."
   "1.3.1" "v1.3.1/Service_v1.3.1-to-v1.3.json"
   "1.3" "v1.3/Service_v1.3-from-v1.3.1.json"

   ;; ---- 1.3.2 tests ----
   "Test the full migration of UMM-S from version 1.3.1 to version 1.3.2 using predefined example files."
   "1.3.1" "v1.3.1/Service_v1.3.1-to-v1.3.2.json"
   "1.3.2" "v1.3.2/Service_v1.3.2-from-v1.3.1.json"

   "Test the full migration of UMM-S from version 1.3.2 to version 1.3.1 using predefined example files."
   "1.3.2" "v1.3.2/Service_v1.3.2-to-v1.3.1.json"
   "1.3.1" "v1.3.1/Service_v1.3.1-from-v1.3.2.json"

   ;; ---- a 1.3.3 test ----
   "Test the full migration of UMM-S from version 1.3.3 to version 1.3.2 using predefined example files."
   "1.3.3" "v1.3.3/Service_v1.3.3-to-v1.3.2.json"
   "1.3.2" "v1.3.2/Service_v1.3.2-from-v1.3.3.json"

   ;; ---- 1.3.4 tests ----
   "Test the full migration of UMM-S from version 1.3.3 to version 1.3.4 using predefined example files."
   "1.3.3" "v1.3.3/Service_v1.3.3-to-v1.3.4.json"
   "1.3.4" "v1.3.4/Service_v1.3.4-from-v1.3.3.json"

   "Test the full migration of UMM-S from version 1.3.4 to version 1.3.3 using predefined example files."
   "1.3.4" "v1.3.4/Service_v1.3.4-to-v1.3.3.json"
   "1.3.3" "v1.3.3/Service_v1.3.3-from-v1.3.4.json"

   ;; ---- 1.4 tests ----
   "Migrating down from 1.4 to 1.3.4"
   "1.4" "v1.4/Service_v1.4-to-v1.3.4.json"
   "1.3.4" "v1.3.4/Service_v1.3.4-from-v1.3.3.json"

   "Migration up from 1.3.4 to 1.4"
   "1.3.4" "v1.3.4/Service_v1.3.4-from-v1.3.3.json"
   "1.4" "v1.4/Service_v1.4-from-v1.3.4.json"

   ;; ---- 1.4.1 tests ----
   "Migrating down from 1.4.1 to 1.4"
   "1.4.1" "v1.4.1/Service_v1.4.1.json"
   "1.4" "v1.4.1/Service_v1.4.1-to-v1.4.json"

   "Migrating up from 1.4 to 1.4.1"
   "1.4" "v1.4.1/Service_v1.4.json"
   "1.4.1" "v1.4.1/Service_v1.4-to-v1.4.1.json"

   ;; ---- 1.5.0 tests ----
   "Migrating down from 1.5.0 to 1.4.1"
   "1.5.0" "v1.5.0/Service_v1.5.0.json"
   "1.4.1" "v1.5.0/Service_v1.4.1.json"

   "Migrating up from 1.4.1 to 1.5.0"
   "1.4.1" "v1.5.0/Service_v1.4.1.json"
   "1.5.0" "v1.5.0/Service_v1.4.1-to-v1.5.0.json"))



(comment

 (core/validate-metadata
  :service "application/vnd.nasa.cmr.umm+json; version=1.2"
  (slurp (io/file (io/resource "example-data/umm-json/service/v1.2/S1200245793-EDF_OPS_v1.2.json")))))
  ; (slurp (io/file (io/resource "example-data/umm-json/service/v1.2/S10000000-TEST_ORNL_WCS_v1.2.json"))))
