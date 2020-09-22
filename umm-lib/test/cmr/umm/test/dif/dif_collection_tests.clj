(ns cmr.umm.test.dif.dif-collection-tests
  "Tests parsing and generating DIF Collection XML."
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
            [cmr.umm.dif.dif-collection :as c]
            [cmr.umm.echo10.echo10-collection :as echo10-c]
            [cmr.umm.echo10.echo10-core :as echo10]
            [cmr.umm.umm-collection :as umm-c]
            [cmr.umm.dif.dif-core :as dif]
            [cmr.spatial.mbr :as m]
            [cmr.umm.test.echo10.echo10-collection-tests :as test-echo10]
            [cmr.umm.validation.validation-core :as v]
            [cmr.common.test.test-check-ext :as ext :refer [checking]])
  (:import cmr.spatial.mbr.Mbr))

(defn- spatial-coverage->expected-parsed
  "Takes the spatial-coverage used to generate the dif and returns the expected parsed spatial-coverage
  from the dif."
  [spatial-coverage]
  (when spatial-coverage
    (let [{:keys [granule-spatial-representation
                  spatial-representation
                  geometries]} spatial-coverage
          ;; DIF only support bounding rectangles
          geometries (seq (filter (comp (partial = Mbr) type) geometries))
          ;; DIF only supports the cartesian coordinate system for the collection spatial representation
          spatial-representation (when geometries :cartesian)]
      (when (or granule-spatial-representation spatial-representation)
        (assoc spatial-coverage
               :granule-spatial-representation granule-spatial-representation
               :spatial-representation spatial-representation
               :geometries geometries
               :orbit-parameters nil)))))

(defn- instruments->expected
  "Returns the expected instruments for the given instruments"
  [instruments]
  (seq (map #(assoc % :technique nil, :sensors nil, :characteristics nil, :operation-modes nil)
            instruments)))

(defn- platform->expected
  "Returns the expected platform for the given platform"
  [platform]
  (-> platform
      (assoc :type umm-c/not-provided :characteristics nil)
      (update-in [:instruments] instruments->expected)))

(defn- platforms->expected-parsed
  "Returns the expected parsed platforms for the given platforms."
  [platforms]
  (let [platforms (seq (map platform->expected platforms))]
    (if (= 1 (count platforms))
      platforms
      (if-let [instruments (seq (mapcat :instruments platforms))]
        (conj (map #(assoc % :instruments nil) platforms)
              (umm-c/map->Platform
                {:short-name umm-c/not-provided
                 :long-name umm-c/not-provided
                 :type umm-c/not-provided
                 :instruments instruments}))
        platforms))))

(defn- related-urls->expected-parsed
  "Returns the expected parsed related-urls for the given related-urls."
  [related-urls]
  (seq (map #(assoc % :size nil :mime-type nil) related-urls)))

(defn- collection-associations->expected-collection-associations
  "Returns the expected parsed collection-associations for the given collection-associations."
  [collection-associations]
  (seq (map #(assoc % :version-id umm-c/not-provided) collection-associations)))

(defn- filter-contacts
  "Remove contacts from a Personnel record that are not emails."
  [person]
  (update-in person [:contacts] (fn [contacts]
                                  (filter #(= :email (:type %))
                                          contacts))))
(defn- science-keywords->expected-parsed
  "Returns expected parsed science keywords if science keywords is empty"
  [science-keywords]
  (if (empty? science-keywords)
    [(umm-c/map->ScienceKeyword {:category umm-c/not-provided
                                 :topic    umm-c/not-provided
                                 :term     umm-c/not-provided})]
    science-keywords))

(defn- expected-organizations
  "Re-order the organizations by distribution centers, add an archive center for each
  distribution center, then processing centers"
  [organizations]
  (let [distribution-centers (filter #(= :distribution-center (:type %)) organizations)]
    (concat
     distribution-centers
     (map #(assoc % :type :archive-center) distribution-centers)
     (filter #(= :processing-center (:type %)) organizations))))

(defn- umm->expected-parsed-dif
  "Modifies the UMM record for testing DIF. DIF contains a subset of the total UMM fields so certain
  fields are removed for comparison of the parsed record"
  [coll]
  (let [{{:keys [short-name version-id processing-level-id collection-data-type]} :product
         :keys [entry-title spatial-coverage personnel]} coll
        range-date-times (get-in coll [:temporal :range-date-times])
        temporal (if (seq range-date-times)
                   (umm-c/map->Temporal {:range-date-times range-date-times
                                         :single-date-times []
                                         :periodic-date-times []})
                   nil)
        personnel (not-empty (->> personnel
                                  ;; only support email right now
                                  (map filter-contacts)
                                  ;; DIF has no Middle_Name tag
                                  (map #(assoc % :middle-name nil))))]
    (-> coll
        ;; DIF does not have short-name or long-name, so we assign them to be entry-id and entry-title respectively
        ;; long-name will only take the first 1024 characters of entry-title if entry-title is too long
        ;; DIF also does not have version-description.
        (assoc :product (umm-c/map->Product {:short-name short-name
                                             :long-name (util/trunc entry-title 1024)
                                             :version-id version-id
                                             :processing-level-id processing-level-id
                                             :collection-data-type collection-data-type}))
        ;; There is no delete-time in DIF
        (assoc-in [:data-provider-timestamps :delete-time] nil)
        (assoc-in [:data-provider-timestamps :revision-date-time]
                  (get-in coll [:data-provider-timestamps :update-time]))
        ;; DIF only has range-date-times
        (assoc :temporal temporal)
        ;; DIF only has distribution centers as Organization
        (update :organizations expected-organizations)
        (assoc :personnel personnel)
        ;; DIF only support some portion of the spatial
        (update-in [:spatial-coverage] spatial-coverage->expected-parsed)
        ;; DIF 9 requires science keywords
        (update-in [:science-keywords] science-keywords->expected-parsed)
        ;; DIF does not support size or mime-type in RelatedURLs
        (update-in [:related-urls] related-urls->expected-parsed)
        ;; DIF does not have version-id in collection associations and we hardcoded it to "dummy"
        (update-in [:collection-associations] collection-associations->expected-collection-associations)
        ;; CMR-588: UMM doesn't have a good recommendation on how to handle spatial-keywords
        (dissoc :spatial-keywords)
        ;; DIF platform does not have type, instruments or characteristics fields
        (update-in [:platforms] platforms->expected-parsed)
        ;; DIF does not have two-d-coordinate-systems
        (dissoc :two-d-coordinate-systems)
        ;; DIF does not have associated-difs
        (dissoc :associated-difs)
        ;; DIF does not have metadata-language
        (dissoc :metadata-language)

        ;; DIF 9 does not have collection citation
        (dissoc :collection-citations)

        ;; DIF9 does not support ranges for additional attributes
        (update-in [:product-specific-attributes]
                   (fn [psas]
                     (seq (map (fn [psa]
                                 (assoc psa
                                        :parameter-range-begin nil
                                        :parameter-range-end nil
                                        :parsed-parameter-range-begin nil
                                        :parsed-parameter-range-end nil))
                               psas))))
        umm-c/map->UmmCollection)))

(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (dif/umm->dif-xml collection)]
      (and
        (seq xml)
        (empty? (c/validate-xml xml))))))

(deftest generate-and-parse-collection-test
  (checking "dif collection round tripping" 100
    [collection coll-gen/collections]
    (let [xml (dif/umm->dif-xml collection)
          parsed (c/parse-collection xml)
          expected-parsed (umm->expected-parsed-dif collection)]
      (is (= expected-parsed parsed)))))

(deftest generate-and-parse-collection-between-formats-test
  (checking "dif parse between formats" 100
    [collection coll-gen/collections]
    (let [xml (dif/umm->dif-xml collection)
          parsed-dif (c/parse-collection xml)
          echo10-xml (echo10/umm->echo10-xml parsed-dif)
          parsed-echo10 (echo10-c/parse-collection echo10-xml)
          expected-parsed (test-echo10/umm->expected-parsed-echo10 (umm->expected-parsed-dif collection))]
      (is (= expected-parsed parsed-echo10))
      (is (= 0 (count (echo10-c/validate-xml echo10-xml)))))))

;; This is a made-up include all fields collection xml sample for the parse collection test
(def all-fields-collection-xml
  "<DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.8.4.xsd\">
    <Entry_ID>geodata_1848</Entry_ID>
    <Entry_Title>Global Land Cover 2000 (GLC 2000)</Entry_Title>
    <Data_Set_Citation>
      <Dataset_Title>Global Land Cover 2000 (GLC 2000)</Dataset_Title>
      <Dataset_Release_Date>2003-01-01</Dataset_Release_Date>
      <Version>006</Version>
    </Data_Set_Citation>
    <Personnel>
      <Role>DIF AUTHOR</Role>
      <Role>TECHNICAL CONTACT</Role>
      <First_Name>ANDREA</First_Name>
      <Last_Name>DE BONO</Last_Name>
      <Email>geo@unepgrid.ch</Email>
    </Personnel>
    <Parameters>
      <Category>EARTH SCIENCE</Category>
      <Topic>LAND SURFACE</Topic>
      <Term>LAND USE/LAND COVER</Term>
      <Variable_Level_1>LAND COVER</Variable_Level_1>
    </Parameters>
    <Parameters uuid=\"cad5c02a-e771-434e-bef6-8dced38a68e8\">
      <Category>EARTH SCIENCE</Category>
      <Topic>ATMOSPHERE</Topic>
      <Term>PRECIPITATION</Term>
      <Variable_Level_1>PRECIPITATION AMOUNT</Variable_Level_1>
      <Variable_Level_2>PRECIPITATION Level 2</Variable_Level_2>
      <Variable_Level_3>PRECIPITATION Level 3</Variable_Level_3>
      <Detailed_Variable>PRECIPITATION Details</Detailed_Variable>
    </Parameters>
    <ISO_Topic_Category>ENVIRONMENT</ISO_Topic_Category>
    <Keyword>Land Cover</Keyword>
    <Keyword>1Km</Keyword>
    <Keyword>JRC</Keyword>
    <Keyword>GLC,</Keyword>
    <Keyword>2000</Keyword>
    <Keyword>satellite</Keyword>
    <Sensor_Name>
      <Short_Name>VEGETATION-1</Short_Name>
      <Long_Name>VEGETATION INSTRUMENT 1 (SPOT 4)</Long_Name>
    </Sensor_Name>
    <Source_Name>
      <Short_Name>SPOT-1</Short_Name>
      <Long_Name>Systeme Probatoire Pour l'Observation de la Terre-1</Long_Name>
    </Source_Name>
    <Source_Name>
      <Short_Name>SPOT-4</Short_Name>
      <Long_Name>Systeme Probatoire Pour l'Observation de la Terre-4</Long_Name>
    </Source_Name>
    <Temporal_Coverage>
      <Start_Date>1996-02-24</Start_Date>
      <Stop_Date>1997-03-24</Stop_Date>
    </Temporal_Coverage>
    <Temporal_Coverage>
      <Start_Date>1998-02-24T22:20:41-05:00</Start_Date>
      <Stop_Date>1999-03-24T22:20:41-05:00</Stop_Date>
    </Temporal_Coverage>
    <Data_Set_Progress>ONGOING</Data_Set_Progress>
    <Spatial_Coverage>
      <Southernmost_Latitude>-90.0</Southernmost_Latitude>
      <Northernmost_Latitude>-60.5033</Northernmost_Latitude>
      <Westernmost_Longitude>-180.0</Westernmost_Longitude>
      <Easternmost_Longitude>180.0</Easternmost_Longitude>
    </Spatial_Coverage>
    <Location>
      <Location_Category>GEOGRAPHIC REGION</Location_Category>
      <Location_Type>GLOBAL</Location_Type>
    </Location>
    <Data_Resolution>
      <Latitude_Resolution>1 km</Latitude_Resolution>
      <Longitude_Resolution>1 km</Longitude_Resolution>
      <Horizontal_Resolution_Range>1 km - &lt; 10 km or approximately .01 degree - &lt; .09 degree</Horizontal_Resolution_Range>
    </Data_Resolution>
    <Project>
      <Short_Name>ESI</Short_Name>
      <Long_Name>Environmental Sustainability Index</Long_Name>
    </Project>
    <Project>
      <Short_Name>UNEP/GRID</Short_Name>
      <Long_Name>UNEP/Global Resources Information Database</Long_Name>
    </Project>
    <Quality>High Quality Metadata</Quality>
    <Use_Constraints>Public</Use_Constraints>
    <Data_Center>
      <Data_Center_Name>
        <Short_Name>EU/JRC/IES</Short_Name>
        <Long_Name>Institute for Environment and Sustainability, Joint Research Center, European Union</Long_Name>
      </Data_Center_Name>
      <Personnel>
        <Role>DATA CENTER CONTACT</Role>
        <First_Name>ETIENNE</First_Name>
        <Last_Name>BARTHOLOME</Last_Name>
        <Email>etienne.bartholome@jrc.it</Email>
        <Phone>+39 332 789908</Phone>
        <Fax>+39 332 789073</Fax>
        <Contact_Address>
          <Address>Space Applications Institute, T.P. 440</Address>
          <Address>EC Joint Research Centre JRC</Address>
          <City>Ispra (VA)</City>
          <Postal_Code>21020</Postal_Code>
          <Country>Italy</Country>
        </Contact_Address>
      </Personnel>
    </Data_Center>
    <Data_Center>
      <Data_Center_Name>
        <Short_Name>UNEP/DEWA/GRID-EUROPE</Short_Name>
        <Long_Name>Global Resource Information Database - Geneva, Division of Early Warning and Assessment, United Nations Environment Programme</Long_Name>
      </Data_Center_Name>
      <Data_Center_URL>http://www.grid.unep.ch/</Data_Center_URL>
      <Personnel>
        <Role>DATA CENTER CONTACT</Role>
        <Last_Name>UNEP/GRID</Last_Name>
        <Email>gridinfo@unep.org</Email>
        <Phone>+254-2-621234</Phone>
        <Fax>+254-2-226890 or 215787</Fax>
        <Contact_Address>
          <Address>United Nations Environment Programme</Address>
          <Address>Global Resource Information Database UNEP/GRID</Address>
          <Address>P.O.Box 30552</Address>
          <Province_or_State>Nairobi</Province_or_State>
          <Country>KENYA</Country>
        </Contact_Address>
      </Personnel>
    </Data_Center>
    <Summary>
      <Abstract>Summary of collection.</Abstract>
      <Purpose>A grand purpose</Purpose>
    </Summary>
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
      <DOI>http://dx.doi.org/12.3456/ABC012XYZ</DOI>
      <Other_Reference_Details>blah</Other_Reference_Details>
    </Reference>
    <Related_URL>
      <URL_Content_Type>
        <Type>GET DATA</Type>
      </URL_Content_Type>
      <URL>http://geodata.grid.unep.ch/</URL>
    </Related_URL>
    <Related_URL>
      <URL_Content_Type>
        <Type>GET DATA</Type>
        <Subtype>ON-LINE ARCHIVE</Subtype>
      </URL_Content_Type>
      <URL>ftp://airsl2.gesdisc.eosdis.nasa.gov/ftp/data/s4pa/Aqua_AIRS_Level2/AIRH2CCF.006/</URL>
      <Description>Access the AIRS/Aqua FINAL AIRS Level 2 Cloud Clear Radiance Product (With HSB) data  by FTP.</Description>
    </Related_URL>
    <Parent_DIF>CNDP-ESP_IPY_POL2006-11139-C02-01CGL_ESASSI</Parent_DIF>
    <Parent_DIF>CNDP-ESP_2</Parent_DIF>
    <IDN_Node>
      <Short_Name>UNEP/GRID</Short_Name>
    </IDN_Node>
    <Originating_Metadata_Node>GCMD</Originating_Metadata_Node>
    <Metadata_Name>CEOS IDN DIF</Metadata_Name>
    <Metadata_Version>VERSION 9.8.4</Metadata_Version>
    <DIF_Creation_Date>2013-02-21</DIF_Creation_Date>
    <Last_DIF_Revision_Date>2013-10-22</Last_DIF_Revision_Date>
    <Extended_Metadata>
      <Metadata>
        <Group>gov.nasa.gsfc.gcmd</Group>
        <Name>metadata.uuid</Name>
        <Value>743933e5-1404-4502-915f-83cde56af440</Value>
      </Metadata>
      <Metadata>
        <Group>gov.nasa.gsfc.gcmd</Group>
        <Name>metadata.extraction_date</Name>
        <Value>2013-09-30 09:45:15</Value>
      </Metadata>
      <Metadata>
        <Group>EMS</Group>
        <Name>ProductLevelId</Name>
        <Value>2</Value>
      </Metadata>
      <Metadata>
        <Group>ECHO</Group>
        <Name>CollectionDataType</Name>
        <Value>NEAR_REAL_TIME</Value>
      </Metadata>
      <Metadata>
        <Group>spatial coverage</Group>
        <Name>GranuleSpatialRepresentation</Name>
        <Value>GEODETIC</Value>
      </Metadata>
      <Metadata>
        <Group>custom.group</Group>
        <Name>String attribute</Name>
        <Description>something string</Description>
        <Value>alpha</Value>
      </Metadata>
      <Metadata>
        <Group>custom.group</Group>
        <Name>Float attribute</Name>
        <Description>something float</Description>
        <Value>12.3</Value>
      </Metadata>
      <Metadata>
        <Group>custom.group</Group>
        <Name>Int attribute</Name>
        <Description>something int</Description>
        <Value>42</Value>
      </Metadata>
      <Metadata>
        <Group>custom.group</Group>
        <Name>Date attribute</Name>
        <Description>something date</Description>
        <Value>2015-09-14</Value>
      </Metadata>
      <Metadata>
        <Group>custom.group</Group>
        <Name>Datetime attribute</Name>
        <Description>something datetime</Description>
        <Value>2015-09-14T13:01:00Z</Value>
      </Metadata>
      <Metadata>
        <Group>custom.group</Group>
        <Name>Time attribute</Name>
        <Description>something time</Description>
        <Value>13:01:00Z</Value>
      </Metadata>
      <Metadata>
        <Group>custom.group</Group>
        <Name>Bool attribute</Name>
        <Description>something bool</Description>
        <Value>false</Value>
      </Metadata>
      <Metadata>
        <Group>gov.nasa.earthdata.cmr</Group>
        <Name>Restriction</Name>
        <Value>1</Value>
      </Metadata>
      <Metadata>
        <Name>Processor</Name>
        <Value>LPDAAC</Value>
      </Metadata>
    </Extended_Metadata>
  </DIF>")

(def valid-collection-xml
  "<DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.8.4.xsd\">
    <Entry_ID>minimal_dif_dataset</Entry_ID>
    <Entry_Title>A minimal dif dataset</Entry_Title>
    <Data_Set_Citation>
      <Dataset_Title>dataset_title</Dataset_Title>
    </Data_Set_Citation>
    <Parameters>
      <Category>category</Category>
      <Topic>topic</Topic>
      <Term>term</Term>
    </Parameters>
    <ISO_Topic_Category>iso topic category</ISO_Topic_Category>
    <Data_Center>
      <Data_Center_Name>
        <Short_Name>datacenter_short_name</Short_Name>
        <Long_Name>data center long name</Long_Name>
      </Data_Center_Name>
      <Personnel>
        <Role>DummyRole</Role>
        <Last_Name>UNEP</Last_Name>
      </Personnel>
    </Data_Center>
    <Reference>
      <Author>author</Author>
      <Publication_Date>2015</Publication_Date>
      <Title>title</Title>
      <Publication_Place>Frederick, MD</Publication_Place>
      <Publisher>publisher</Publisher>
      <DOI>http://dx.doi.org/12.3456/ABC012XYZ</DOI>
    </Reference>
    <Summary>
      <Abstract>summary of the dataset</Abstract>
      <Purpose>A grand purpose</Purpose>
    </Summary>
    <Metadata_Name>CEOS IDN DIF</Metadata_Name>
    <Metadata_Version>VERSION 9.8.4</Metadata_Version>
    <Last_DIF_Revision_Date>2013-10-22</Last_DIF_Revision_Date>
  </DIF>")

(def expected-temporal
  (umm-c/map->Temporal
    {:range-date-times
     [(umm-c/map->RangeDateTime
        {:beginning-date-time (p/parse-datetime "1996-02-24")
         :ending-date-time (p/parse-datetime "1997-03-24T23:59:59.999")})
      (umm-c/map->RangeDateTime
        {:beginning-date-time (p/parse-datetime "1998-02-24T22:20:41-05:00")
         :ending-date-time (p/parse-datetime "1999-03-24T22:20:41-05:00")})]
     :single-date-times []
     :periodic-date-times []}))

(def expected-collection
  (umm-c/map->UmmCollection
    {:entry-title "Global Land Cover 2000 (GLC 2000)"
     :summary "Summary of collection."
     :purpose "A grand purpose"
     :quality "High Quality Metadata"
     :use-constraints "Public"
     :product (umm-c/map->Product
                {:short-name "geodata_1848"
                 :long-name "Global Land Cover 2000 (GLC 2000)"
                 :version-id "006"
                 :processing-level-id "2"
                 :collection-data-type "NEAR_REAL_TIME"})
     :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                 {:insert-time (p/parse-datetime "2013-02-21")
                                  :update-time (p/parse-datetime "2013-10-22")
                                  :revision-date-time (p/parse-datetime "2013-10-22")})
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
                                 :doi "http://dx.doi.org/12.3456/ABC012XYZ"
                                 :other-reference-details "blah"})]
     :spatial-keywords ["GLOBAL"]
     :platforms [(umm-c/map->Platform
                   {:short-name umm-c/not-provided
                    :long-name umm-c/not-provided
                    :type umm-c/not-provided
                    :instruments [(umm-c/map->Instrument
                                    {:short-name "VEGETATION-1"
                                     :long-name "VEGETATION INSTRUMENT 1 (SPOT 4)"})]})
                 (umm-c/map->Platform
                   {:short-name "SPOT-1"
                    :long-name "Systeme Probatoire Pour l'Observation de la Terre-1"
                    :type umm-c/not-provided})
                 (umm-c/map->Platform
                   {:short-name "SPOT-4"
                    :long-name "Systeme Probatoire Pour l'Observation de la Terre-4"
                    :type umm-c/not-provided})]
     :temporal expected-temporal
     :collection-progress :in-work
     :science-keywords
     [(umm-c/map->ScienceKeyword
        {:category "EARTH SCIENCE"
         :topic "LAND SURFACE"
         :term "LAND USE/LAND COVER"
         :variable-level-1 "LAND COVER"})
      (umm-c/map->ScienceKeyword
        {:category "EARTH SCIENCE"
         :topic "ATMOSPHERE"
         :term "PRECIPITATION"
         :variable-level-1 "PRECIPITATION AMOUNT"
         :variable-level-2 "PRECIPITATION Level 2"
         :variable-level-3 "PRECIPITATION Level 3"
         :detailed-variable "PRECIPITATION Details"})]
     :product-specific-attributes
     [(umm-c/map->ProductSpecificAttribute
        {:group "gov.nasa.gsfc.gcmd"
         :name "metadata.uuid"
         :data-type :string
         :value "743933e5-1404-4502-915f-83cde56af440"
         :parsed-value "743933e5-1404-4502-915f-83cde56af440"
         :description "Not provided"})
      (umm-c/map->ProductSpecificAttribute
        {:group "gov.nasa.gsfc.gcmd"
         :name "metadata.extraction_date"
         :data-type :string
         :value "2013-09-30 09:45:15"
         :parsed-value "2013-09-30 09:45:15"
         :description "Not provided"})
      (umm-c/map->ProductSpecificAttribute
        {:group "custom.group"
         :name "String attribute"
         :description "something string"
         :data-type :string
         :value "alpha"
         :parsed-value "alpha"})
      (umm-c/map->ProductSpecificAttribute
        {:group "custom.group"
         :name "Float attribute"
         :description "something float"
         :data-type :float
         :value "12.3"
         :parsed-value 12.3})
      (umm-c/map->ProductSpecificAttribute
        {:group "custom.group"
         :name "Int attribute"
         :description "something int"
         :data-type :int
         :value "42"
         :parsed-value 42})
      (umm-c/map->ProductSpecificAttribute
        {:group "custom.group"
         :name "Date attribute"
         :description "something date"
         :data-type :date
         :value "2015-09-14"
         :parsed-value (p/parse-datetime "2015-09-14")})
      (umm-c/map->ProductSpecificAttribute
        {:group "custom.group"
         :name "Datetime attribute"
         :description "something datetime"
         :data-type :datetime
         :value "2015-09-14T13:01:00Z"
         :parsed-value (p/parse-datetime "2015-09-14T13:01:00Z")})
      (umm-c/map->ProductSpecificAttribute
        {:group "custom.group"
         :name "Time attribute"
         :description "something time"
         :data-type :time
         :value "13:01:00Z"
         :parsed-value (p/parse-time "13:01:00Z")})
      (umm-c/map->ProductSpecificAttribute
        {:group "custom.group"
         :name "Bool attribute"
         :description "something bool"
         :data-type :boolean
         :value "false"
         :parsed-value false})]
     :spatial-coverage
     (umm-c/map->SpatialCoverage
       {:granule-spatial-representation :geodetic
        :spatial-representation :cartesian
        :geometries [(m/mbr -180 -60.5033 180 -90)]})
     :collection-associations [(umm-c/map->CollectionAssociation
                                 {:short-name "CNDP-ESP_IPY_POL2006-11139-C02-01CGL_ESASSI"
                                  :version-id umm-c/not-provided})
                               (umm-c/map->CollectionAssociation
                                 {:short-name "CNDP-ESP_2"
                                  :version-id umm-c/not-provided})]
     :projects
     [(umm-c/map->Project
        {:short-name "ESI"
         :long-name "Environmental Sustainability Index"})
      (umm-c/map->Project
        {:short-name "UNEP/GRID"
         :long-name "UNEP/Global Resources Information Database"})]
     :related-urls
     [(umm-c/map->RelatedURL
        {:type "GET DATA"
         :url "http://geodata.grid.unep.ch/"})
      (umm-c/map->RelatedURL
        {:type "GET DATA"
         :sub-type "ON-LINE ARCHIVE"
         :url "ftp://airsl2.gesdisc.eosdis.nasa.gov/ftp/data/s4pa/Aqua_AIRS_Level2/AIRH2CCF.006/"
         :description "Access the AIRS/Aqua FINAL AIRS Level 2 Cloud Clear Radiance Product (With HSB) data  by FTP."
         :title "Access the AIRS/Aqua FINAL AIRS Level 2 Cloud Clear Radiance Product (With HSB) data  by FTP."})]
     :organizations
     [(umm-c/map->Organization
        {:type :distribution-center
         :org-name "EU/JRC/IES"})
      (umm-c/map->Organization
        {:type :distribution-center
         :org-name "UNEP/DEWA/GRID-EUROPE"})
      (umm-c/map->Organization
         {:type :archive-center
          :org-name "EU/JRC/IES"})
      (umm-c/map->Organization
         {:type :archive-center
          :org-name "UNEP/DEWA/GRID-EUROPE"})
      (umm-c/map->Organization
        {:type :processing-center
         :org-name "LPDAAC"})]
     :personnel [(umm-c/map->Personnel
                   {:first-name "ANDREA"
                    :last-name "DE BONO"
                    :roles ["DIF AUTHOR" "TECHNICAL CONTACT"]
                    :contacts [(umm-c/map->Contact
                                 {:type :email
                                  :value "geo@unepgrid.ch"})]})]
     :access-value 1.0}))

(deftest validate-parsed-dif-test
  (testing "Validate DIF to UMM Collection"
   (let [parsed-dif (c/parse-collection all-fields-collection-xml)]
     (is (empty? (v/validate-collection parsed-dif))))))

(deftest parse-collection-test
  (testing "parse collection"
    (is (= expected-collection (c/parse-collection all-fields-collection-xml))))
  (testing "parse temporal"
    (is (= expected-temporal (c/parse-temporal all-fields-collection-xml))))
  (testing "parse collection access value"
    (is (= 1.0 (c/parse-access-value all-fields-collection-xml)))))

(deftest validate-xml
  (testing "valid xml"
    (is (empty? (c/validate-xml valid-collection-xml))))
  (testing "invalid xml"
    (is (= [(str "Exception while parsing invalid XML: " 
                 "Line 18 - cvc-complex-type.2.4.a: Invalid content was found starting with element 'XXXX'. "
                 "One of '{\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Data_Center_URL, "
                 "\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Data_Set_ID, "
                 "\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Personnel}' is expected.")]
           (c/validate-xml (s/replace valid-collection-xml "Personnel" "XXXX"))))))

(deftest parse-nil-version-test
  ;; UMM-C is now making the version field a required field. It is optional in DIF-9 so we provide
  ;; a default of Not provided when it is missing from the DIF-9 metadata.
  (is (= umm-c/not-provided (get-in (c/parse-collection valid-collection-xml) [:product :version-id]))))
