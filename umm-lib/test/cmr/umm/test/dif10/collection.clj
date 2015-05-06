(ns cmr.umm.test.dif10.collection
  "Tests parsing and generating DIF 10 Collection XML."
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [cmr.common.joda-time]
            [cmr.common.date-time-parser :as p]
            [cmr.common.util :as util]
            [cmr.umm.test.generators.collection :as coll-gen]
            [cmr.umm.dif10.collection :as c]
            [cmr.umm.echo10.collection :as echo10-c]
            [cmr.umm.echo10.core :as echo10]
            [cmr.umm.collection :as umm-c]
            [cmr.umm.dif10.core :as dif]
            [cmr.spatial.mbr :as m]
            [cmr.umm.test.echo10.collection :as test-echo10])
  (:import cmr.spatial.mbr.Mbr))

(def minimal-collection-xml
  "<DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.8.4.xsd\">
  <Entry_ID>minimal_dif_dataset</Entry_ID>
  <Version>001</Version>
  <Entry_Title>A minimal dif dataset</Entry_Title>
  <Science_Keywords>
  <Category>EARTH SCIENCE</Category>
  <Topic>CRYOSPHERE</Topic>
  <Term>SEA ICE</Term>
  </Science_Keywords>
  <Platform>
  <Type>In Situ Land-based Platforms</Type>
  <Short_Name>Short Name</Short_Name>
  <Long_Name>Long Name</Long_Name>
  <Instrument>
  <Short_Name>Short Name</Short_Name>
  </Instrument>
  </Platform>
  <Temporal_Coverage>
  <Range_DateTime>
  <Beginning_Date_Time>1998-02-24T22:20:41-05:00</Beginning_Date_Time>
  <Ending_Date_Time>1999-03-24T22:20:41-05:00</Ending_Date_Time>
  </Range_DateTime>
  </Temporal_Coverage>
  <Spatial_Coverage>
  <Granule_Spatial_Representation>GEODETIC</Granule_Spatial_Representation>
  </Spatial_Coverage>
  <Project>
  <Short_Name>short name</Short_Name>
  </Project>
  <Organization>
  <Organization_Type>ARCHIVER</Organization_Type>
  <Organization_Name>
  <Short_Name>EU/JRC/IES</Short_Name>
  <Long_Name>Institute for Environment and Sustainability, Joint Research Center, European Union</Long_Name>
  </Organization_Name>
  <Personnel>
  <Role>DATA CENTER CONTACT</Role>
  <Contact_Person>
  <First_Name>ETIENNE</First_Name>
  <Last_Name>last name</Last_Name>
  </Contact_Person>
  </Personnel>
  </Organization>
  <Summary>
  <Abstract>summary of the dataset</Abstract>
  <Purpose>A grand purpose</Purpose>
  </Summary>
  <Related_URL>
  <URL>http://www.foo.com</URL>
  </Related_URL>
  <Metadata_Name>CEOS IDN DIF</Metadata_Name>
  <Metadata_Version>VERSION 10.1</Metadata_Version>
  <Metadata_Dates>
  <Metadata_Creation>2000-03-24T22:20:41-05:00</Metadata_Creation>
  <Metadata_Last_Revision>2000-03-24T22:20:41-05:00</Metadata_Last_Revision>
  <Data_Creation>2000-03-24T22:20:41-05:00</Data_Creation>
  <Data_Last_Revision>2000-03-24T22:20:41-05:00</Data_Last_Revision>
  </Metadata_Dates>
  <Product_Flag>DATA_PRODUCT_FILE</Product_Flag>
  </DIF>")

(deftest parse-collection-test
  (let [expected (umm-c/map->UmmCollection
                   {:entry-id "minimal_dif_dataset_001"
                    :entry-title "A minimal dif dataset"
                    :summary "summary of the dataset"
                    :purpose "A grand purpose"
                    :product (umm-c/map->Product
                               {:short-name "minimal_dif_dataset"
                                :long-name "A minimal dif dataset"
                                :version-id "001"})
                    :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                                {:insert-time (p/parse-datetime "2000-03-24T22:20:41-05:00")
                                                 :update-time (p/parse-datetime "2000-03-24T22:20:41-05:00")})
                    :platforms [(umm-c/map->Platform
                                  {:short-name "Short Name"
                                   :long-name "Long Name"
                                   :type "In Situ Land-based Platforms"
                                   :instrument [(umm-c/map->Instrument
                                                  {:short-name "Short Name"})]})]
                    :projects
                    [(umm-c/map->Project
                       {:short-name "short name"})]
                    :temporal
                    (umm-c/map->Temporal
                      {:range-date-times
                       [(umm-c/map->RangeDateTime
                          {:beginning-date-time (p/parse-datetime "1998-02-24T22:20:41-05:00")
                           :ending-date-time (p/parse-datetime "1999-03-24T22:20:41-05:00")})]
                       :single-date-times []
                       :periodic-date-times []})
                    :science-keywords
                    [(umm-c/map->ScienceKeyword
                       {:category "EARTH SCIENCE"
                        :topic "CRYOSPHERE"
                        :term "SEA ICE"})]
                    :related-urls
                    [(umm-c/map->RelatedURL
                       {:url "http://www.foo.com"})]
                    :organizations
                    [(umm-c/map->Organization
                       {:type :archive-center
                        :org-name "Institute for Environment and Sustainability, Joint Research Center, European Union"})]
                    :spatial-coverage
                    (umm-c/map->SpatialCoverage
                      {:granule-spatial-representation :geodetic})})
        actual (c/parse-collection minimal-collection-xml)]
    (is (= expected actual))))

(deftest validate-xml
  (testing "valid xml"
    (is (empty? (c/validate-xml minimal-collection-xml))))
  (testing "invalid xml"
    (is (= [(str "Line 10 - cvc-complex-type.2.4.a: Invalid content"
                 " was found starting with element 'XXXX'. One of"
                 " '{\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Science_Keywords,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":ISO_Topic_Category,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Ancillary_Keyword,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Platform}' is expected.")]
           (c/validate-xml (s/replace minimal-collection-xml "Platform" "XXXX"))))))