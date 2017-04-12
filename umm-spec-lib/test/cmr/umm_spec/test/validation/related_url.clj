(ns cmr.umm-spec.test.validation.related-url
  "This has tests for UMM Related URL validations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.services.errors :as e]
   [cmr.umm-spec.dif-util :as dif-util]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.umm-spec.models.umm-common-models :as c]
   [cmr.umm-spec.related-url :as url]
   [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.validation.umm-spec-validation-core :as v]
   [cmr.umm-spec.xml-to-umm-mappings.echo10.related-url :as echo10-url]))

(deftest collection-related-urls-validation
  (testing "Valid related urls"
    (h/assert-warnings-valid
     (coll/map->UMM-C
                     {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                     :URLContentType "DistributionURL"
                                     :Type "GET SERVICE"
                                     :SubType "ECHO"}
                                    {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                     :URLContentType "PublicationURL"
                                     :Type "VIEW RELATED INFORMATION"
                                     :SubType "USER'S GUIDE"}]})))

  (testing "Multiple invalid related urls"
    (h/assert-warnings-multiple-invalid
     (coll/map->UMM-C
      {:RelatedUrls [{:URL "http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"
                      :URLContentType "DistributionURL"
                      :Type "GET SERVICE"
                      :SubType "ECHO"}
                     {:URL "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"
                      :URLContentType "PublicationURL"
                      :Type "VIEW RELATED INFORMATION"
                      :SubType "USER'S GUIDE"}
                     {:URL "https://www.foo.com"
                      :URLContentType "Bad URLContentType"
                      :Type "VIEW RELATED INFORMATION"
                      :SubType "USER'S GUIDE"}
                     {:URL "https://www.bar.com"
                      :URLContentType "PublicationURL"
                      :Type "Bad Type"
                      :SubType "USER'S GUIDE"}
                     {:URL "https://www.foobar.com"
                      :URLContentType "PublicationURL"
                      :Type "VIEW RELATED INFORMATION"
                      :SubType "Bad SubType"}
                     {:URL "https://www.baz.com"
                      :URLContentType "PublicationURL"
                      :Type "VIEW RELATED INFORMATION"
                      :SubType "USER'S GUIDE"
                      :GetService {:MimeType "application/html"
                                   :FullName "Not provided"
                                   :DataID "Not provided"
                                   :Protocol "Not provided"}}
                     {:URL "https://www.baz.com"
                      :URLContentType "PublicationURL"
                      :Type "VIEW RELATED INFORMATION"
                      :SubType "USER'S GUIDE"
                      :GetData {:Size 10.0
                                :Unit "MB"
                                :Format "Not provided"}}]})
     [{:path [:RelatedUrls 4]
       :errors ["URLContentType: PublicationURL, Type: VIEW RELATED INFORMATION, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:RelatedUrls 3]
       :errors ["URLContentType: PublicationURL, Type: Bad Type, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: VIEW RELATED INFORMATION, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}
      {:path [:RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:RelatedUrls 6]
       :errors ["Only URLContentType: DistributionURL Type: GET DATA can contain GetData, RelatedUrl contains URLContentType: PublicationURL Type: VIEW RELATED INFORMATION"]}
      {:path [:RelatedUrls 5]
       :errors ["Only URLContentType: DistributionURL Type: GET SERVICE can contain GetService, RelatedUrl contains URLContentType: PublicationURL Type: VIEW RELATED INFORMATION"]}])))
;
(deftest collection-data-center-related-urls-validation
  (testing "Valid related urls"
    (h/assert-warnings-valid
     (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactInformation
                        {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                        :URLContentType "DataCenterURL"
                                        :Type "HOME PAGE"}
                                       {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                        :URLContentType "DataCenterURL"
                                        :Type "HOME PAGE"}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-warnings-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactInformation
         {:RelatedUrls [{:URL "http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"
                         :URLContentType "DataCenterURL"
                         :Type "HOME PAGE"}
                        {:URL "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"
                         :URLContentType "DataCenterURL"
                         :Type "HOME PAGE"}
                        {:URL "https://www.foo.com"
                         :URLContentType "Bad URLContentType"
                         :Type "HOME PAGE"}
                        {:URL "https://www.bar.com"
                         :URLContentType "DataCenterURL"
                         :Type "Bad Type"}
                        {:URL "https://www.foobar.com"
                         :URLContentType "DataCenterURL"
                         :Type "HOME PAGE"
                         :SubType "Bad SubType"}]}}]})
     [{:path [:DataCenters 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: DataCenterURL, Type: Bad Type, SubType: null is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: DataCenterURL, Type: HOME PAGE, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: HOME PAGE, SubType: null is not a vaild URLContentType/Type/SubType combination."
                "URLContentType must be DataCenterURL for DataCenter RelatedUrls"]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}]))
  (testing "Contact Persons valid related urls"
    (h/assert-warnings-valid
     (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactPersons
                        [{:ContactInformation
                          {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                          :URLContentType "DataContactURL"
                                          :Type "HOME PAGE"}
                                         {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                          :URLContentType "DataContactURL"
                                          :Type "HOME PAGE"}]}}]}]})))
  (testing "Contact Persons multiple invalid related urls"
    (h/assert-warnings-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactPersons
         [{:ContactInformation
           {:RelatedUrls [{:URL "http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"
                           :URLContentType "DataContactURL"
                           :Type "HOME PAGE"}
                          {:URL "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"
                           :URLContentType "DataContactURL"
                           :Type "HOME PAGE"}
                          {:URL "https://www.foo.com"
                           :URLContentType "Bad URLContentType"
                           :Type "HOME PAGE"}
                          {:URL "https://www.bar.com"
                           :URLContentType "DataContactURL"
                           :Type "Bad Type"}
                          {:URL "https://www.foobar.com"
                           :URLContentType "DataContactURL"
                           :Type "HOME PAGE"
                           :SubType "Bad SubType"}]}}]}]})
     [{:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: DataContactURL, Type: Bad Type, SubType: null is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: DataContactURL, Type: HOME PAGE, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: HOME PAGE, SubType: null is not a vaild URLContentType/Type/SubType combination."
                "URLContentType must be DataContactURL for ContactPersons or ContactGroups RelatedUrls"]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}]))
  (testing "Contact Groups valid related urls"
    (h/assert-warnings-valid
     (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactGroups
                        [{:ContactInformation
                          {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                          :URLContentType "DataContactURL"
                                          :Type "HOME PAGE"}
                                         {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                          :URLContentType "DataContactURL"
                                          :Type "HOME PAGE"}]}}]}]})))
  (testing "Contact Groups multiple invalid related urls"
    (h/assert-warnings-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactGroups
         [{:ContactInformation
           {:RelatedUrls [{:URL "http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"
                           :URLContentType "DataContactURL"
                           :Type "HOME PAGE"}
                          {:URL "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"
                           :URLContentType "DataContactURL"
                           :Type "HOME PAGE"}
                          {:URL "https://www.foo.com"
                           :URLContentType "Bad URLContentType"
                           :Type "HOME PAGE"}
                          {:URL "https://www.bar.com"
                           :URLContentType "DataContactURL"
                           :Type "Bad Type"}
                          {:URL "https://www.foobar.com"
                           :URLContentType "DataContactURL"
                           :Type "HOME PAGE"
                           :SubType "Bad SubType"}]}}]}]})
     [{:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: DataContactURL, Type: Bad Type, SubType: null is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: DataContactURL, Type: HOME PAGE, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: HOME PAGE, SubType: null is not a vaild URLContentType/Type/SubType combination."
                "URLContentType must be DataContactURL for ContactPersons or ContactGroups RelatedUrls"]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-contact-persons-related-urls-validation
  (testing "Valid related urls"
    (h/assert-warnings-valid
     (coll/map->UMM-C
                     {:ContactPersons
                      [{:ContactInformation
                        {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                        :URLContentType "DataContactURL"
                                        :Type "HOME PAGE"}
                                       {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                        :URLContentType "DataContactURL"
                                        :Type "HOME PAGE"}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-warnings-multiple-invalid
     (coll/map->UMM-C
      {:ContactPersons
       [{:ContactInformation
         {:RelatedUrls [{:URL "http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"
                         :URLContentType "DataContactURL"
                         :Type "HOME PAGE"}
                        {:URL "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"
                         :URLContentType "DataContactURL"
                         :Type "HOME PAGE"}
                        {:URL "https://www.foo.com"
                         :URLContentType "Bad URLContentType"
                         :Type "HOME PAGE"}
                        {:URL "https://www.bar.com"
                         :URLContentType "DataContactURL"
                         :Type "Bad Type"}
                        {:URL "https://www.foobar.com"
                         :URLContentType "DataContactURL"
                         :Type "HOME PAGE"
                         :SubType "Bad SubType"}]}}]})
     [{:path [:ContactPersons 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: DataContactURL, Type: Bad Type, SubType: null is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: DataContactURL, Type: HOME PAGE, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: HOME PAGE, SubType: null is not a vaild URLContentType/Type/SubType combination."
                "URLContentType must be DataContactURL for ContactPersons or ContactGroups RelatedUrls"]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))


(deftest collection-contact-groups-related-urls-validation
  (testing "Valid related urls"
    (h/assert-warnings-valid
     (coll/map->UMM-C
                     {:ContactGroups
                      [{:ContactInformation
                        {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                        :URLContentType "DataContactURL"
                                        :Type "HOME PAGE"}
                                       {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                        :URLContentType "DataContactURL"
                                        :Type "HOME PAGE"}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-warnings-multiple-invalid
     (coll/map->UMM-C
      {:ContactGroups
       [{:ContactInformation
         {:RelatedUrls [{:URL "http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"
                         :URLContentType "DataContactURL"
                         :Type "HOME PAGE"}
                        {:URL "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"
                         :URLContentType "DataContactURL"
                         :Type "HOME PAGE"}
                        {:URL "https://www.foo.com"
                         :URLContentType "Bad URLContentType"
                         :Type "HOME PAGE"}
                        {:URL "https://www.bar.com"
                         :URLContentType "DataContactURL"
                         :Type "Bad Type"}
                        {:URL "https://www.foobar.com"
                         :URLContentType "DataContactURL"
                         :Type "HOME PAGE"
                         :SubType "Bad SubType"}]}}]})
     [{:path [:ContactGroups 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: DataContactURL, Type: Bad Type, SubType: null is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: DataContactURL, Type: HOME PAGE, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: HOME PAGE, SubType: null is not a vaild URLContentType/Type/SubType combination."
                "URLContentType must be DataContactURL for ContactPersons or ContactGroups RelatedUrls"]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-related-url-content-type-test
  (testing "All valid URLContentType/Type/SubType combinations"
    (doseq [URLContentType (keys su/valid-url-content-types-map)
            :let [valid-types (su/valid-types-for-url-content-type URLContentType)]]
      (doseq [Type valid-types
              ;; Nil is a valid SubType for any URLContentType/Type combination. So we add it to valid-sub-types
              :let [valid-sub-types (conj (su/valid-subtypes-for-type URLContentType Type) nil)]]
        (doseq [SubType valid-sub-types]
          (h/assert-valid (coll/map->UMM-C
                           {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                           :URLContentType URLContentType
                                           :Type Type
                                           :SubType SubType}]})))))))

(deftest collection-collection-citations-related-urls-validation
  (testing "Valid related urls"
    (h/assert-warnings-valid
     (coll/map->UMM-C
                     {:CollectionCitations
                      [{:OnlineResource {:Linkage "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"}}
                       {:OnlineResource {:Linkage "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-warnings-multiple-invalid
     (coll/map->UMM-C
      {:CollectionCitations
       [{:OnlineResource {:Linkage "http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"}}
        {:OnlineResource {:Linkage "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"}}]})
     [{:path [:CollectionCitations 0 :OnlineResource :Linkage]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:CollectionCitations 1 :OnlineResource :Linkage]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-publication-references-related-urls-validation
  (testing "Valid related urls"
    (h/assert-warnings-valid
     (coll/map->UMM-C
                     {:PublicationReferences
                      [{:OnlineResource {:Linkage "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"}}
                       {:OnlineResource {:Linkage "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-warnings-multiple-invalid
     (coll/map->UMM-C
      {:PublicationReferences
       [{:OnlineResource {:Linkage "http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"}}
        {:OnlineResource {:Linkage "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"}}]})
     [{:path [:PublicationReferences 0 :OnlineResource :Linkage]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:PublicationReferences 1 :OnlineResource :Linkage]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest dif-valid-url-types
 (testing "DIF hard-coded conversion table contains valid combinations"
  (doseq [url-type (vals dif-util/dif-url-content-type->umm-url-types)]
   (h/assert-warnings-valid
    (coll/map->UMM-C {:RelatedUrls [(merge
                                     url-type
                                     {:URL "https://www.foo.com"})]})))))

(deftest echo10-valid-url-types
 (testing "ECHO10 hard-coded conversion table contains valid combinations"
  (doseq [url-type (vals echo10-url/online-resource-type->related-url-types)]
   (h/assert-warnings-valid
    (coll/map->UMM-C {:RelatedUrls [(merge
                                     url-type
                                     {:URL "https://www.foo.com"})]})))))
