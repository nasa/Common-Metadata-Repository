(ns cmr.umm-spec.test.validation.related-url
  "This has tests for UMM Related URL validations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.services.errors :as e]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.umm-spec.models.umm-common-models :as c]
   [cmr.umm-spec.related-url :as url]
   [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]
   [cmr.umm-spec.validation.related-url :as url-validation]
   [cmr.umm-spec.validation.umm-spec-validation-core :as v]))

(deftest collection-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                     :URLContentType "DistributionURL"
                                     :Type "GET SERVICE"
                                     :SubType "ECHO"}
                                    {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                     :URLContentType "PublicationURL"
                                     :Type "VIEW RELATED INFORMATION"
                                     :SubType "USER'S GUIDE"}]})))

  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
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
                      :SubType "Bad SubType"}]})
     [{:path [:RelatedUrls 4]
       :errors ["URLContentType: PublicationURL, Type: VIEW RELATED INFORMATION, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:RelatedUrls 3]
       :errors ["URLContentType: PublicationURL, Type: Bad Type, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: VIEW RELATED INFORMATION, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}
      {:path [:RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}])))

(deftest collection-data-center-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactInformation
                        {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                        :URLContentType "DistributionURL"
                                        :Type "GET SERVICE"
                                        :SubType "ECHO"}
                                       {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                        :URLContentType "PublicationURL"
                                        :Type "VIEW RELATED INFORMATION"
                                        :SubType "USER'S GUIDE"}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactInformation
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
                         :SubType "Bad SubType"}]}}]})
     [{:path [:DataCenters 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: PublicationURL, Type: VIEW RELATED INFORMATION, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: PublicationURL, Type: Bad Type, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: VIEW RELATED INFORMATION, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}]))
  (testing "Contact Persons valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactPersons
                        [{:ContactInformation
                          {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                          :URLContentType "DistributionURL"
                                          :Type "GET SERVICE"
                                          :SubType "ECHO"}
                                         {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                          :URLContentType "PublicationURL"
                                          :Type "VIEW RELATED INFORMATION"
                                          :SubType "USER'S GUIDE"}]}}]}]})))
  (testing "Contact Persons multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactPersons
         [{:ContactInformation
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
                           :SubType "Bad SubType"}]}}]}]})
     [{:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: PublicationURL, Type: VIEW RELATED INFORMATION, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: PublicationURL, Type: Bad Type, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: VIEW RELATED INFORMATION, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}]))
  (testing "Contact Groups valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactGroups
                        [{:ContactInformation
                          {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                          :URLContentType "DistributionURL"
                                          :Type "GET SERVICE"
                                          :SubType "ECHO"}
                                         {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                          :URLContentType "PublicationURL"
                                          :Type "VIEW RELATED INFORMATION"
                                          :SubType "USER'S GUIDE"}]}}]}]})))
  (testing "Contact Groups multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactGroups
         [{:ContactInformation
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
                           :SubType "Bad SubType"}]}}]}]})
     [{:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: PublicationURL, Type: VIEW RELATED INFORMATION, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: PublicationURL, Type: Bad Type, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: VIEW RELATED INFORMATION, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}])))

(deftest collection-contact-persons-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:ContactPersons
                      [{:ContactInformation
                        {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                        :URLContentType "DistributionURL"
                                        :Type "GET SERVICE"
                                        :SubType "ECHO"}
                                       {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                        :URLContentType "PublicationURL"
                                        :Type "VIEW RELATED INFORMATION"
                                        :SubType "USER'S GUIDE"}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:ContactPersons
       [{:ContactInformation
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
                         :SubType "Bad SubType"}]}}]})
     [{:path [:ContactPersons 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: PublicationURL, Type: VIEW RELATED INFORMATION, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: PublicationURL, Type: Bad Type, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: VIEW RELATED INFORMATION, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-contact-groups-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:ContactGroups
                      [{:ContactInformation
                        {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                        :URLContentType "DistributionURL"
                                        :Type "GET SERVICE"
                                        :SubType "ECHO"}
                                       {:URL "http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                        :URLContentType "PublicationURL"
                                        :Type "VIEW RELATED INFORMATION"
                                        :SubType "USER'S GUIDE"}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:ContactGroups
       [{:ContactInformation
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
                         :SubType "Bad SubType"}]}}]})
     [{:path [:ContactGroups 0 :ContactInformation :RelatedUrls 4]
       :errors ["URLContentType: PublicationURL, Type: VIEW RELATED INFORMATION, SubType: Bad SubType is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 3]
       :errors ["URLContentType: PublicationURL, Type: Bad Type, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 2]
       :errors ["URLContentType: Bad URLContentType, Type: VIEW RELATED INFORMATION, SubType: USER'S GUIDE is not a vaild URLContentType/Type/SubType combination."]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 0 :URL]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 1 :URL]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-related-url-content-type-test
  (testing "All valid URLContentType/Type/SubType combinations"
    (doseq [URLContentType (keys url-validation/valid-url-content-types-map)
            :let [valid-types (keys (get url-validation/valid-url-content-types-map URLContentType))]]
      (doseq [Type valid-types
              ;; Nil is a valid SubType for any URLContentType/Type combination. So we add it to valid-sub-types
              :let [valid-sub-types (conj (keys (get url-validation/valid-url-content-types-map Type)) nil)]]
        (doseq [SubType valid-sub-types]
          (h/assert-valid (coll/map->UMM-C
                           {:RelatedUrls [{:URL "http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"
                                           :URLContentType URLContentType
                                           :Type Type
                                           :SubType SubType}]})))))))

; CollectionCitations and PublicationReferences don't use RelatedUrls anymore.
; Commenting these tests out for now, but will fix them later.

; (deftest collection-collection-citations-related-urls-validation
;   (testing "Valid related urls"
;     (h/assert-valid (coll/map->UMM-C
;                      {:CollectionCitations
;                       [{:RelatedUrl {:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}}
;                        {:RelatedUrl {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
;                                             "http://www.cbd.int/"]}}]})))
;   (testing "Multiple invalid related urls"
;     (h/assert-multiple-invalid
;      (coll/map->UMM-C
;       {:CollectionCitations
;        [{:RelatedUrl {:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}}
;         {:RelatedUrl {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}}]})
;      [{:path [:CollectionCitations 0 :RelatedUrl :URLs 0]
;        :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
;       {:path [:CollectionCitations 1 :RelatedUrl :URLs 0]
;        :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))
;
; (deftest collection-publication-references-related-urls-validation
;   (testing "Valid related urls"
;     (h/assert-valid (coll/map->UMM-C
;                      {:PublicationReferences
;                       [{:RelatedUrl {:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}}
;                        {:RelatedUrl {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
;                                             "http://www.cbd.int/"]}}]})))
;   (testing "Multiple invalid related urls"
;     (h/assert-multiple-invalid
;      (coll/map->UMM-C
;       {:PublicationReferences
;        [{:RelatedUrl {:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}}
;         {:RelatedUrl {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}}]})
;      [{:path [:PublicationReferences 0 :RelatedUrl :URLs 0]
;        :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
;       {:path [:PublicationReferences 1 :RelatedUrl :URLs 0]
;        :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))
