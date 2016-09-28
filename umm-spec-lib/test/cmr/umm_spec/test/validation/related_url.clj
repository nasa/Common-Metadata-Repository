(ns cmr.umm-spec.test.validation.related-url
  "This has tests for UMM Related URL validations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.services.errors :as e]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.umm-spec.models.umm-common-models :as c]
   [cmr.umm-spec.related-url :as url]
   [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as h]
   [cmr.umm-spec.validation.umm-spec-validation-core :as v]))

(deftest collection-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:RelatedUrls [{:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}
                                    {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                            "http://www.cbd.int/"]}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:RelatedUrls [{:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}
                     {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}]})
     [{:path [:RelatedUrls 0 :URLs 0]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:RelatedUrls 1 :URLs 0]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}]))
  (testing "Multiple invalid related Urls"
      (h/assert-multiple-invalid
       (coll/map->UMM-C
        {:RelatedUrls [{:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"
                               "http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}]})
       [{:path [:RelatedUrls 0 :URLs 0]
         :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
        {:path [:RelatedUrls 0 :URLs 1]
         :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-data-center-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactInformation
                        {:RelatedUrls [{:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}
                                       {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                               "http://www.cbd.int/"]}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactInformation
         {:RelatedUrls [{:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}
                        {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}]}}]})
     [{:path [:DataCenters 0 :ContactInformation :RelatedUrls 0 :URLs 0]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactInformation :RelatedUrls 1 :URLs 0]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}]))
  (testing "Contact Persons valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactPersons
                        [{:ContactInformation
                          {:RelatedUrls [{:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}
                                         {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                                 "http://www.cbd.int/"]}]}}]}]})))
  (testing "Contact Persons multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactPersons
         [{:ContactInformation
           {:RelatedUrls [{:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}
                          {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}]}}]}]})
     [{:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 0 :URLs 0]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactPersons 0 :ContactInformation :RelatedUrls 1 :URLs 0]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}]))
  (testing "Contact Groups valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:DataCenters
                      [{:ContactGroups
                        [{:ContactInformation
                          {:RelatedUrls [{:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}
                                         {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                                 "http://www.cbd.int/"]}]}}]}]})))
  (testing "Contact Groups multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:DataCenters
       [{:ContactGroups
         [{:ContactInformation
           {:RelatedUrls [{:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}
                          {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}]}}]}]})
     [{:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 0 :URLs 0]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:DataCenters 0 :ContactGroups 0 :ContactInformation :RelatedUrls 1 :URLs 0]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-contact-persons-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:ContactPersons
                      [{:ContactInformation
                        {:RelatedUrls [{:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}
                                       {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                               "http://www.cbd.int/"]}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:ContactPersons
       [{:ContactInformation
         {:RelatedUrls [{:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}
                        {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}]}}]})
     [{:path [:ContactPersons 0 :ContactInformation :RelatedUrls 0 :URLs 0]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:ContactPersons 0 :ContactInformation :RelatedUrls 1 :URLs 0]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-contact-groups-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:ContactGroups
                      [{:ContactInformation
                        {:RelatedUrls [{:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}
                                       {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                               "http://www.cbd.int/"]}]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:ContactGroups
       [{:ContactInformation
         {:RelatedUrls [{:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}
                        {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}]}}]})
     [{:path [:ContactGroups 0 :ContactInformation :RelatedUrls 0 :URLs 0]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:ContactGroups 0 :ContactInformation :RelatedUrls 1 :URLs 0]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-collection-citations-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:CollectionCitations
                      [{:RelatedUrl {:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}}
                       {:RelatedUrl {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                            "http://www.cbd.int/"]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:CollectionCitations
       [{:RelatedUrl {:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}}
        {:RelatedUrl {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}}]})
     [{:path [:CollectionCitations 0 :RelatedUrl :URLs 0]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:CollectionCitations 1 :RelatedUrl :URLs 0]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))

(deftest collection-publication-references-related-urls-validation
  (testing "Valid related urls"
    (h/assert-valid (coll/map->UMM-C
                     {:PublicationReferences
                      [{:RelatedUrl {:URLs ["http://fresc.usgs.gov/products/dataset/moorhen_telemetry.zip"]}}
                       {:RelatedUrl {:URLs ["http://gce-lter.marsci.uga.edu/lter/asp/db/send_eml.asp?detail=full&missing=NaN&delimiter=\t&accession=FNG-GCEM-0401"
                                            "http://www.cbd.int/"]}}]})))
  (testing "Multiple invalid related urls"
    (h/assert-multiple-invalid
     (coll/map->UMM-C
      {:PublicationReferences
       [{:RelatedUrl {:URLs ["http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip"]}}
        {:RelatedUrl {:URLs ["http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods"]}}]})
     [{:path [:PublicationReferences 0 :RelatedUrl :URLs 0]
       :errors ["[http:\\\\fresc.usgs.gov\\products\\dataset\\moorhen_telemetry.zip] is not a valid URL"]}
      {:path [:PublicationReferences 1 :RelatedUrl :URLs 0]
       :errors ["[http://ingrid.ldgo.columbia.edu/SOURCES/.IGOSS/.fsu/|u|u+|u|v/dods] is not a valid URL"]}])))
