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
            [cmr.umm.dif10.collection.personnel :as personnel]
            [cmr.umm.test.echo10.collection :as test-echo10]
            [cmr.umm.collection.product-specific-attribute :as psa])
  (:import cmr.spatial.mbr.Mbr))


(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection coll-gen/collections]
           (let [xml (dif10/umm->dif10-xml collection)]
             (empty? (c/validate-xml xml)))))

(defn- related-urls->expected-parsed
  [related-urls]
  (if (empty? related-urls)
    [(umm-c/map->RelatedURL {:url "Not provided"})]
    related-urls))

(defn- spatial-coverage->expected-parsed
  "DIF10 requires spatial coverage and it accepts only a single geometry"
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
        umm-c/map->Product)))

(defn- science-keywords->expected-parsed
  [science-keywords]
  (if (empty? science-keywords)
    [(umm-c/map->ScienceKeyword {:category "Not provided"
                                 :topic    "Not provided"
                                 :term     "Not provided"})]
    science-keywords))


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
          (update-in [:type] (fn [type] (get platform/platform-types type "Not provided")))
          (update-in [:instruments] instrument->expected-parsed)))))

(defn- umm-contacts->expected-contacts
  "Removes non-email contacts since we don't use those yet and don't generate them in the XML."
  [contacts]
  (filter #(= :email (:type %)) contacts))

(defn- personnel->expected-parsed
  [personnel]
  (seq (for [person personnel]
         (-> person
         (update-in [:roles]
                    (fn [roles]
                      (or (seq (filter personnel/personnel-roles roles))
                          ["TECHNICAL CONTACT"])))
         (update-in [:contacts] umm-contacts->expected-contacts)))))

(defn- projects->expected-parsed
  [projects]
  (if (empty? projects)
    [(umm-c/map->Project {:short-name "Not provided"})]
    projects))

(defn- product-specific-attributes->expected-parsed
  [psas]
  (seq (for [psa psas]
         (if (nil? (:parameter-range-begin psa))
           (assoc psa
             :parameter-range-begin "Not provided"
             :parsed-parameter-range-begin
             (psa/safe-parse-value (:data-type psa) "Not provided"))
           psa))))

(defn- remove-field-from-records
  [records field]
  (seq (map #(assoc % field nil) records)))

(defn- remove-unsupported-fields
  "Remove fields unsupported and not yet supported in DIF10"
  [coll]
  (-> coll
      (dissoc :spatial-keywords :associated-difs :access-value :metadata-language
              :collection-associations  :quality :temporal-keywords :two-d-coordinate-systems :use-constraints)
      (assoc-in  [:product :processing-level-id] nil)
      (assoc-in [:product :version-description] nil)
      (update-in [:publication-references] remove-field-from-records :doi)
      (update-in [:related-urls] remove-field-from-records :size)
      umm-c/map->UmmCollection))

(defn- add-required-placeholder-fields
  "Add placeholders for fields which are empty but are marked as required in DIF10 schema"
  [coll]
  (-> coll
      (update-in [:science-keywords] science-keywords->expected-parsed)
      (update-in [:platforms] platforms->expected-parsed)
      (update-in [:personnel] personnel->expected-parsed)
      (update-in [:projects] projects->expected-parsed)
      (update-in [:related-urls] related-urls->expected-parsed)
      (update-in [:product-specific-attributes] product-specific-attributes->expected-parsed)
      (update-in [:spatial-coverage] spatial-coverage->expected-parsed)))

(defn- umm->expected-parsed-dif10
  "Modifies the UMM record for testing DIF. Unsupported fields are removed for comparison of the parsed record and
  fields which are required by DIF 10 are added."
  [coll]
  (let [short-name (:entry-id coll)
        long-name (:entry-title coll)]
    (-> coll
        remove-unsupported-fields
        add-required-placeholder-fields
        (update-in [:product] (product->expected-parsed short-name long-name))
        umm-c/map->UmmCollection)))

(defspec generate-and-parse-collection-test 100
  (for-all [collection coll-gen/collections]
           (let [expected (umm->expected-parsed-dif10 collection)
                 xml (dif10/umm->dif10-xml collection)
                 actual (c/parse-collection xml)]
             (= expected actual))))

(defn- remove-not-provided
  [values sub-key]
  (seq (remove #(= (sub-key %) "Not provided") values)))

(defn remove-dif10-place-holder-fields
  "Remove dummy fields from a UMM record which would come in when a generated UMM is converted to
  DIF 10 XML and parsed back to the UMM record. The dummy fields are added during the the creation
  of DIF 10 XML to satisfy the DIF 10 schema constraints. The fields which are maked as Not provided will
  be removed by this function."
  [coll]
  (-> coll
      (update-in [:related-urls] remove-not-provided :url)
      (update-in [:platforms] remove-not-provided :short-name)
      (update-in [:platforms] (fn [platforms]
                                (for [platform platforms]
                                  (update-in platform [:instruments]
                                             remove-not-provided :short-name))))
      (update-in [:projects] remove-not-provided :short-name)
      (update-in [:science-keywords] remove-not-provided :category)))

(defn- revert-spatial-coverage
  "The spatial coverage is removed if spatial coverage in the original UMM collection is absent.
  The geometries in the spatial coverge are reverted to original since DIF 10 only reads the first
  geometry"
  [spatial-coverage orig-spatial-coverage]
  (when orig-spatial-coverage
    (assoc spatial-coverage :geometries (:geometries orig-spatial-coverage))))

(defn- revert-platform-type
  "The platform types are reverted to original types since DIF 10 uses enumeration types which
  in general would not match with types in UMM which are simple strings and so will be marked as
  Not provided during the transformation process."
  [platforms orig-platforms]
  (seq (for [[platform orig-platform]
             (map vector platforms orig-platforms)]
         (assoc platform :type (:type orig-platform)))))

(defn- revert-product
  "DIF 10 does not have short name and long name, Short name and long name are ignored during the
  creation of DIF10 XML from UMM. This restores the short name and long name in the parsed XML
  from the original collection"
  [product orig-product]
  (let [{:keys [short-name long-name]} orig-product]
    (assoc product :short-name short-name :long-name long-name)))

(defn- revert-personnel
  "The personnel roles are reverted to original types since DIF 10 uses enumeration types which
  in general would not match with types in UMM which are simple strings and so will be marked as
  TECHNICAL CONTACT, one of the enumeraion types, during the transformation process."
  [personnel orig-personnel]
  (seq (for [[person orig-person] (map vector personnel orig-personnel)]
         (assoc person :roles (:roles orig-person)))))

(defn- revert-psas
  "parameter-range-begin is a required field in DIF 10 Additional_Attributes"
  [psas orig-psas]
  (seq (for [[psa orig-psa] (map vector psas orig-psas)]
             (assoc psa
               :parameter-range-begin (:parameter-range-begin orig-psa)
               :parsed-parameter-range-begin (:parsed-parameter-range-begin orig-psa)))))

(defn rectify-dif10-fields
  "Revert the UMM fields which are modified when a generated UMM is converted to DIF 10 XML and
  parsed back to UMM. The fields are modified during the creation of the XML to satisfy the schema
  constraints of DIF 10."
  [coll original-coll]
  (let [{:keys [platforms spatial-coverage product personnel product-specific-attributes]} original-coll]
    (-> coll
        (update-in [:platforms] revert-platform-type platforms)
        (update-in [:spatial-coverage] revert-spatial-coverage spatial-coverage)
        (update-in [:product] revert-product product)
        (update-in [:personnel] revert-personnel personnel)
        (update-in [:product-specific-attributes] revert-psas product-specific-attributes))))

(defn restore-modified-fields
  "Some of the UMM fields which are lost/modified/added during translation from UMM to DIF 10 XML
  and back are reverted using the earlier record for testing purposes."
  [dif10-umm orig-umm]
  (-> dif10-umm
      remove-dif10-place-holder-fields
      (rectify-dif10-fields orig-umm)))

(defspec generate-and-parse-collection-between-formats-test 100
  (for-all [collection coll-gen/collections]
           (let [xml (dif10/umm->dif10-xml collection)
                 parsed-dif10 (restore-modified-fields (c/parse-collection xml) collection)
                 echo10-xml (echo10/umm->echo10-xml parsed-dif10)
                 parsed-echo10 (echo10-c/parse-collection echo10-xml)
                 expected-parsed (test-echo10/umm->expected-parsed-echo10
                                   (remove-unsupported-fields collection))]
             (and (= expected-parsed parsed-echo10)
                  (empty? (echo10-c/validate-xml echo10-xml))))))

(def dif10-collection-xml
  "<DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
    <Entry_ID>minimal_dif_dataset</Entry_ID>
    <Version>001</Version>
    <Entry_Title>A minimal dif dataset</Entry_Title>
  <Personnel>
    <Role>TECHNICAL CONTACT</Role>
    <Contact_Person>
      <First_Name>first name</First_Name>
      <Middle_Name>middle name</Middle_Name>
      <Last_Name>last name</Last_Name>
      <Email>dssweb@ucar.edu</Email>
    </Contact_Person>
  </Personnel>
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
    <Additional_Attributes>
      <Name>String add attrib</Name>
      <DataType>STRING</DataType>
      <Description>something string</Description>
      <ParameterRangeBegin>alpha</ParameterRangeBegin>
      <ParameterRangeEnd>bravo</ParameterRangeEnd>
      <Value>alpha1</Value>
    </Additional_Attributes>
    <Additional_Attributes>
      <Name>Float add attrib</Name>
      <DataType>FLOAT</DataType>
      <Description>something float</Description>
      <ParameterRangeBegin>0.1</ParameterRangeBegin>
      <ParameterRangeEnd>100.43</ParameterRangeEnd>
      <Value>12.3</Value>
    </Additional_Attributes>
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
                                         :geometries [(m/mbr -180.0 90.0 180.0 -90.0)]})
                    :personnel [(umm-c/map->Personnel
                                 {:first-name "first name"
                                  :last-name "last name"
                                  :middle-name "middle name"
                                  :roles ["TECHNICAL CONTACT"]
                                  :contacts [(umm-c/map->Contact
                                               {:type :email
                                                :value "dssweb@ucar.edu"})]})]
                    :product-specific-attributes [(umm-c/map->ProductSpecificAttribute
                                                    {:name "String add attrib"
                                                     :description "something string"
                                                     :data-type :string
                                                     :parameter-range-begin "alpha"
                                                     :parameter-range-end "bravo"
                                                     :value "alpha1"
                                                     :parsed-parameter-range-begin "alpha"
                                                     :parsed-parameter-range-end "bravo"
                                                     :parsed-value "alpha1"})
                                                  (umm-c/map->ProductSpecificAttribute
                                                    {:name "Float add attrib"
                                                     :description "something float"
                                                     :data-type :float
                                                     :parameter-range-begin "0.1"
                                                     :parameter-range-end "100.43"
                                                     :value "12.3"
                                                     :parsed-parameter-range-begin 0.1
                                                     :parsed-parameter-range-end 100.43
                                                     :parsed-value 12.3})]})
        actual (c/parse-collection dif10-collection-xml)]
    (is (= expected actual))))

(deftest validate-xml
  (testing "valid xml"
    (is (empty? (c/validate-xml dif10-collection-xml))))
  (testing "invalid xml"
    (is (= [(str "Line 19 - cvc-complex-type.2.4.a: Invalid content"
                 " was found starting with element 'XXXX'. One of"
                 " '{\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Science_Keywords,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":ISO_Topic_Category,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Ancillary_Keyword,"
                 " \"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Platform}' is expected.")]
           (c/validate-xml (s/replace dif10-collection-xml "Platform" "XXXX"))))))