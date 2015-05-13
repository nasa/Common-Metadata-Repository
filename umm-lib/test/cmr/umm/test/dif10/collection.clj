(ns cmr.umm.test.dif10.collection
  "Tests parsing and generating DIF 10 Collection XML."
  (:require [clojure.test :refer :all]
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
            [cmr.umm.dif10.core :as dif10]
            [cmr.spatial.mbr :as m]
            [cmr.umm.dif10.collection.platform :as platform]
            [cmr.umm.test.echo10.collection :as test-echo10])
  (:import cmr.spatial.mbr.Mbr))


(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection coll-gen/collections]
           (let [xml (dif10/umm->dif10-xml collection)]
               (empty? (c/validate-xml xml)))))

(defn- related-urls->expected-parsed
  [related-urls]
  (if (empty? related-urls)
    [(umm-c/map->RelatedURL {:url "Not provided"})]
    (seq (map #(umm-c/map->RelatedURL (dissoc % :mime-type :size)) related-urls))))

(defn- publication-references->expected-parsed
  [publication-references]
  (seq (map #(umm-c/map->PublicationReference (dissoc % :doi)) publication-references)))

(defn- spatial-coverage->expected-parsed
  [spatial-coverage]
  (if (nil? spatial-coverage)
    (umm-c/map->SpatialCoverage {:granule-spatial-representation :cartesian})
    (update-in spatial-coverage [:geometries] #(when (some? %) [(first %)]))))

(defn- product->expected-parsed
  [short-name long-name]
    (fn [product]
      (-> product
          (assoc-in [:short-name] short-name)
          (assoc-in [:long-name] long-name)
          (dissoc :processing-level-id)
          (dissoc :version-description)
          umm-c/map->Product)))

(defn- science-keywords->expected-parsed
  [science-keywords]
  (if (empty? science-keywords)
    [(umm-c/map->ScienceKeyword {:category "Not provided"
                                 :topic    "Not provided"
                                 :term     "Not provided"})]
    science-keywords))

(defn- platform-type->expected-parsed
  [type]
  (if (platform/PLATFORM_TYPES type) type "Not provided"))

(defn- instrument->expected-parsed
  [instruments]
  (if (empty? instruments)
    [(umm-c/map->Instrument {:short-name "Not provided"})]
    instruments))

(defn- platforms->expected-parsed
  [platforms]
  (if (empty? platforms)
    [(umm-c/map->Platform
       {:type "Not provided"
        :short-name "Not provided"
        :instruments [(umm-c/map->Instrument {:short-name "Not provided"})]})]
    (for [platform platforms]
      (-> platform
          (update-in [:type] platform-type->expected-parsed)
          (update-in [:instruments] instrument->expected-parsed)))))

(defn- projects->expected-parsed
  [projects]
  (if (empty? projects)
    [(umm-c/map->Project {:short-name "Not provided"})]
    projects))

(defn- umm->expected-parsed-dif10
  "Modifies the UMM record for testing DIF. Unsupported fields are removed for comparison of the parsed record"
  [coll]
  (let [short-name (:entry-id coll)
        long-name (:entry-title coll)]
    (-> coll
        (update-in [:science-keywords] science-keywords->expected-parsed)
        (update-in [:platforms] platforms->expected-parsed)
        (update-in [:projects] projects->expected-parsed)
        (update-in  [:product] (product->expected-parsed short-name long-name))
        (dissoc :spatial-keywords)
        (dissoc :associated-difs)
        (dissoc :access-value)

        (update-in [:related-urls] related-urls->expected-parsed)
        (dissoc :metadata-language)
        (dissoc :collection-associations)
        (dissoc :personnel)
        (dissoc :quality)
        (dissoc :temporal-keywords)
        (dissoc :two-d-coordinate-systems)
        (update-in [:publication-references] publication-references->expected-parsed)
        (dissoc :product-specific-attributes)
        (dissoc :use-constraints)
        (update-in [:spatial-coverage] spatial-coverage->expected-parsed)
        umm-c/map->UmmCollection)))

(defspec generate-and-parse-collection-test 100
  (for-all [collection coll-gen/collections]
           (let [expected (umm->expected-parsed-dif10 collection)
                 xml (dif10/umm->dif10-xml collection)
                 actual (c/parse-collection xml)]
             (= expected actual))))

(def dif10-collection-xml
  "<DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
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
      <Geometry>
        <Coordinate_System>CARTESIAN</Coordinate_System>
        <Bounding_Rectangle>
          <Southernmost_Latitude>-90</Southernmost_Latitude>
          <Northernmost_Latitude>90</Northernmost_Latitude>
          <Westernmost_Longitude>-180</Westernmost_Longitude>
          <Easternmost_Longitude>180</Easternmost_Longitude>
        </Bounding_Rectangle>
      </Geometry>
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
          <Last_Name>Not provided</Last_Name>
        </Contact_Person>
      </Personnel>
    </Organization>
    <Reference>
      <Author>author</Author>
      <Publication_Date>2015</Publication_Date>
      <Title>title</Title>
      <Series>1</Series>
      <Edition>2</Edition>
      <Volume>3</Volume>
      <Issue>4</Issue>
      <Report_Number>5</Report_Number>
      <Publication_Place>Frederick, MD</Publication_Place>
      <Publisher>publisher</Publisher>
      <Pages>678</Pages>
      <ISBN>978-0-394-80001-1</ISBN>
      <Online_Resource>http://example.com</Online_Resource>
      <Other_Reference_Details>blah</Other_Reference_Details>
    </Reference>
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
      <Data_Creation>1970-01-01T00:00:00</Data_Creation>
      <Data_Last_Revision>1970-01-01T00:00:00</Data_Last_Revision>
    </Metadata_Dates>
    <Collection_Data_Type>SCIENCE_QUALITY</Collection_Data_Type>
    <Product_Flag>Not provided</Product_Flag>
   </DIF>")

(deftest parse-collection-test
  (let [expected (umm-c/map->UmmCollection
                   {:entry-id "minimal_dif_dataset"
                    :entry-title "A minimal dif dataset"
                    :summary "summary of the dataset"
                    :purpose "A grand purpose"
                    :product (umm-c/map->Product
                               {:short-name "minimal_dif_dataset"
                                :long-name "A minimal dif dataset"
                                :version-id "001"
                                :collection-data-type "SCIENCE_QUALITY"})
                    :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                                {:insert-time (p/parse-datetime "2000-03-24T22:20:41-05:00")
                                                 :update-time (p/parse-datetime "2000-03-24T22:20:41-05:00")})
                    :publication-references [(umm-c/map->PublicationReference
                                               {:author "author"
                                                :publication-date "2015"
                                                :title "title"
                                                :series "1"
                                                :edition "2"
                                                :volume "3"
                                                :issue "4"
                                                :report-number "5"
                                                :publication-place "Frederick, MD"
                                                :publisher "publisher"
                                                :pages "678"
                                                :isbn "978-0-394-80001-1"
                                                :related-url "http://example.com"
                                                :other-reference-details "blah"})]
                    :platforms [(umm-c/map->Platform
                                  {:short-name "Short Name"
                                   :long-name "Long Name"
                                   :type "In Situ Land-based Platforms"
                                   :instruments [(umm-c/map->Instrument
                                                  {:short-name "Short Name"})]})]
                    :projects [(umm-c/map->Project
                                 {:short-name "short name"})]
                    :temporal (umm-c/map->Temporal
                                {:range-date-times
                                 [(umm-c/map->RangeDateTime
                                    {:beginning-date-time (p/parse-datetime "1998-02-24T22:20:41-05:00")
                                     :ending-date-time (p/parse-datetime "1999-03-24T22:20:41-05:00")})]
                                 :single-date-times []
                                 :periodic-date-times []})
                    :science-keywords [(umm-c/map->ScienceKeyword
                                         {:category "EARTH SCIENCE"
                                          :topic "CRYOSPHERE"
                                          :term "SEA ICE"})]
                    :related-urls [(umm-c/map->RelatedURL
                                     {:url "http://www.foo.com"})]
                    :organizations [(umm-c/map->Organization
                                      {:type :archive-center
                                       :org-name "EU/JRC/IES"})]
                    :spatial-coverage (umm-c/map->SpatialCoverage
                                        {:granule-spatial-representation :geodetic
                                         :spatial-representation :cartesian,
                                         :geometries [(m/mbr -180.0 90.0 180.0 -90.0)]})})
        actual (c/parse-collection dif10-collection-xml)]
    (is (= expected actual))))

(deftest validate-xml
  (testing "valid xml"
    (is (empty? (c/validate-xml dif10-collection-xml))))
  (testing "invalid xml"
    (is (= [(str "Line 10 - cvc-complex-type.2.4.a: Invalid content"
                 " was found starting with element 'XXXX'. One of"
                 " '{\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Science_Keywords,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":ISO_Topic_Category,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Ancillary_Keyword,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Platform}' is expected.")]
           (c/validate-xml (s/replace dif10-collection-xml "Platform" "XXXX"))))))