(ns cmr.umm.test.dif.collection
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
            [cmr.umm.test.generators.collection :as coll-gen]
            [cmr.umm.dif.collection :as c]
            [cmr.umm.echo10.collection :as echo10-c]
            [cmr.umm.echo10.core :as echo10]
            [cmr.umm.collection :as umm-c]
            [cmr.umm.dif.core :as dif]
            [cmr.spatial.mbr :as m]
            [cmr.umm.test.echo10.collection :as test-echo10])
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

(defn- related-urls->expected-parsed
  "Returns the expected parsed related-urls for the given related-urls."
  [related-urls]
  (seq (map #(assoc % :size nil) related-urls)))

(defn- phone->expected-parsed
  "Returns the expected parsed phone for the given phone."
  [phone]
  ;; DIF has no number-type, phones are just numbers
  (assoc phone :number-type nil))

(defn- personnel->expected-parsed
  "Returns the expected parsed personnel for the given personnel."
  [personnel]
  (seq (map (fn [person]
              ;; DIF only allows one address per person
              (let [person (assoc person :addresses (take 1 (:addresses person)))
                    phones (:phones person)
                    phones (map phone->expected-parsed phones)]
                (assoc person :phones phones)))
            personnel)))

(defn- organization->expected-parsed
  "Returns the expected parsed organization for the given organization."
  [organization]
  (let [personnel (:personnel organization)]
    (assoc organization :personnel (personnel->expected-parsed personnel))))


(defn- umm->expected-parsed-dif
  "Modifies the UMM record for testing DIF. DIF contains a subset of the total UMM fields so certain
  fields are removed for comparison of the parsed record"
  [coll]
  (let [{{:keys [version-id processing-level-id collection-data-type]} :product
         :keys [entry-id entry-title spatial-coverage]} coll
        range-date-times (get-in coll [:temporal :range-date-times])
        temporal (if (seq range-date-times)
                   (umm-c/map->Temporal {:range-date-times range-date-times
                                         :single-date-times []
                                         :periodic-date-times []})
                   nil)
        organizations (filter #(= :distribution-center (:type %)) (:organizations coll))
        ;; Fix the personnel fields.
        organizations (map organization->expected-parsed organizations)]
    (-> coll
        ;; DIF does not have short-name or long-name, so we assign them to be entry-id and entry-title respectively
        ;; long-name will only take the first 1024 characters of entry-title if entry-title is too long
        (assoc :product (umm-c/map->Product {:short-name entry-id
                                             :long-name (c/trunc entry-title 1024)
                                             :version-id version-id
                                             :processing-level-id processing-level-id
                                             :collection-data-type collection-data-type}))
        ;; AccessValue is optional in dif and hasn't be specified.
        (dissoc :access-value)
        ;; There is no delete-time in DIF
        (assoc-in [:data-provider-timestamps :delete-time] nil)
        ;; DIF only has range-date-times
        (assoc :temporal temporal)
        ;; DIF only has distribution centers as Organization
        (assoc :organizations organizations)
        ;; DIF only support some portion of the spatial
        (update-in [:spatial-coverage] spatial-coverage->expected-parsed)
        ;; DIF does not support size in RelatedURLs
        (update-in [:related-urls] related-urls->expected-parsed)
        ;; CMR-588: UMM doesn't have a good recommendation on how to handle spatial-keywords
        (dissoc :spatial-keywords)
        ;; CMR-590: UMM doesn't have a good recommendation on how to handle platforms
        (dissoc :platforms)
        ;; DIF does not have two-d-coordinate-systems
        (dissoc :two-d-coordinate-systems)
        ;; DIF does not have associated-difs
        (dissoc :associated-difs)
        umm-c/map->UmmCollection)))

(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (dif/umm->dif-xml collection)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))))))

(defspec generate-and-parse-collection-test 100
  (for-all [collection coll-gen/collections]

    (let [xml (dif/umm->dif-xml collection)
          parsed (c/parse-collection xml)
          expected-parsed (umm->expected-parsed-dif collection)]
      (= parsed expected-parsed))))

(comment
  (cmr.common.dev.capture-reveal/reveal collection)
  (cmr.common.dev.capture-reveal/reveal parsed)
  (cmr.common.dev.capture-reveal/reveal xml)
  (cmr.common.dev.capture-reveal/reveal expected-parsed)

  (is (= (cmr.common.dev.capture-reveal/reveal parsed)
         (cmr.common.dev.capture-reveal/reveal expected-parsed)))


  ; (let [x #cmr.umm.collection.UmmCollection{:entry-id "0", :entry-title "0", :summary "0", :product #cmr.umm.collection.Product{:short-name "0", :long-name "0", :version-id "0", :processing-level-id nil, :collection-data-type nil}, :access-value nil, :data-provider-timestamps #cmr.umm.collection.DataProviderTimestamps{:insert-time #=(org.joda.time.DateTime. 0), :update-time #=(org.joda.time.DateTime. 0), :delete-time nil}, :spatial-keywords nil, :temporal-keywords nil, :temporal #cmr.umm.collection.Temporal{:time-type nil, :date-type nil, :temporal-range-type nil, :precision-of-seconds nil, :ends-at-present-flag nil, :range-date-times [#cmr.umm.collection.RangeDateTime{:beginning-date-time #=(org.joda.time.DateTime. 0), :ending-date-time nil}], :single-date-times [], :periodic-date-times []}, :science-keywords [#cmr.umm.collection.ScienceKeyword{:category "0", :topic "0", :term "0", :variable-level-1 "0", :variable-level-2 nil, :variable-level-3 nil, :detailed-variable nil}], :platforms nil, :product-specific-attributes nil, :projects nil, :two-d-coordinate-systems nil, :related-urls nil, :organizations (#cmr.umm.collection.Organization{:type :distribution-center, :org-name "!", :personnel [#cmr.umm.collection.ContactPerson{:roles ["Investigator"], :addresses [#cmr.umm.collection.Address{:city "!", :country "!", :postal-code "!", :state-province "!", :street-address-lines ["!"]}], :emails nil, :first-name nil, :last-name "!", :middle-name nil, :phones [#cmr.umm.collection.Phone{:number "!", :number-type "!"}]}]}), :spatial-coverage nil, :associated-difs nil}
  ;       xml (dif/umm->dif-xml x)
  ;       _ (cmr.common.dev.capture-reveal/capture xml)
  ;       expected (umm->expected-parsed-dif x)
  ;       parsed (c/parse-collection xml)]
  ;   (is (= expected parsed))
  ;   ;expected
  ;   (println xml)
  ;   (println expected)
  ;   (println parsed)
  ;   )

  ;(c/validate-xml (cmr.common.dev.capture-reveal/reveal xml)
  )

(defspec generate-and-parse-collection-between-formats-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (dif/umm->dif-xml collection)
          parsed-dif (c/parse-collection xml)
          echo10-xml (echo10/umm->echo10-xml parsed-dif)
          parsed-echo10 (echo10-c/parse-collection echo10-xml)
          expected-parsed (test-echo10/umm->expected-parsed-echo10 (umm->expected-parsed-dif collection))]
      (and (= parsed-echo10 expected-parsed)
           (= 0 (count (echo10-c/validate-xml echo10-xml)))))))

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
  </Summary>
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
  <Group>AdditionalAttribute</Group>
  <Name>String add attrib</Name>
  <Description>something string</Description>
  <Type>STRING</Type>
  <Value type=\"ParamRangeBegin\">alpha</Value>
  <Value type=\"ParamRangeEnd\">bravo</Value>
  <Value type=\"Value\">alpha1</Value>
  </Metadata>
  <Metadata>
  <Group>AdditionalAttribute</Group>
  <Name>Float add attrib</Name>
  <Description>something float</Description>
  <Type>FLOAT</Type>
  <Value type=\"MeasurementResolution\">1</Value>
  <Value type=\"ParamRangeBegin\">0.1</Value>
  <Value type=\"ParamRangeEnd\">100.43</Value>
  <Value type=\"ParamUtilsOfMeasure\">Percent</Value>
  <Value type=\"Value\">12.3</Value>
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
  <Summary>
  <Abstract>summary of the dataset</Abstract>
  </Summary>
  <Metadata_Name>CEOS IDN DIF</Metadata_Name>
  <Metadata_Version>VERSION 9.8.4</Metadata_Version>
  <Last_DIF_Revision_Date>2013-10-22</Last_DIF_Revision_Date>
  </DIF>")

(deftest parse-collection-test
  (let [expected (umm-c/map->UmmCollection
                   {:entry-id "geodata_1848"
                    :entry-title "Global Land Cover 2000 (GLC 2000)"
                    :summary "Summary of collection."
                    :product (umm-c/map->Product
                               {:short-name "geodata_1848"
                                :long-name "Global Land Cover 2000 (GLC 2000)"
                                :version-id "006"
                                :processing-level-id "2"
                                :collection-data-type "NEAR_REAL_TIME"})
                    :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                                {:insert-time (p/parse-date "2013-02-21")
                                                 :update-time (p/parse-date "2013-10-22")})
                    ;:spatial-keywords ["Word-2" "Word-1" "Word-0"]
                    :temporal
                    (umm-c/map->Temporal
                      {:range-date-times
                       [(umm-c/map->RangeDateTime
                          {:beginning-date-time (p/parse-date "1996-02-24")
                           :ending-date-time (p/parse-date "1997-03-24")})
                        (umm-c/map->RangeDateTime
                          {:beginning-date-time (p/parse-datetime "1998-02-24T22:20:41-05:00")
                           :ending-date-time (p/parse-datetime "1999-03-24T22:20:41-05:00")})]
                       :single-date-times []
                       :periodic-date-times []})
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
                       {:name "String add attrib"
                        :description "something string"
                        :data-type :string
                        :parameter-range-begin "alpha"
                        :parameter-range-end "bravo"
                        :value "alpha1"})
                     (umm-c/map->ProductSpecificAttribute
                       {:name "Float add attrib"
                        :description "something float"
                        :data-type :float
                        :parameter-range-begin 0.1
                        :parameter-range-end 100.43
                        :value 12.3})]
                    :spatial-coverage
                    (umm-c/map->SpatialCoverage
                      {:granule-spatial-representation :geodetic
                       :spatial-representation :cartesian
                       :geometries [(m/mbr -180 -60.5033 180 -90)]})
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
                        :org-name "EU/JRC/IES"
                        :personnel [(umm-c/map->ContactPerson
                                  {:emails ["etienne.bartholome@jrc.it"]
                                   :phones [(umm-c/map->Phone {:number "+39 332 789908"})]
                                   :addresses [(umm-c/map->Address
                                                 {:country "Italy"
                                                  :city "Ispra (VA)"
                                                  :postal-code "21020"
                                                  :street-address-lines ["Space Applications Institute, T.P. 440"
                                                                        "EC Joint Research Centre JRC"]})]
                                   :first-name "ETIENNE"
                                   :last-name "BARTHOLOME"
                                   :roles ["DATA CENTER CONTACT"]})]})
                     (umm-c/map->Organization
                       {:type :distribution-center
                        :org-name "UNEP/DEWA/GRID-EUROPE"
                        :personnel [(umm-c/map->ContactPerson
                                  {:emails ["gridinfo@unep.org"]
                                   :phones [(umm-c/map->Phone {:number "+254-2-621234"})]
                                   :addresses [(umm-c/map->Address
                                                 {:country "KENYA"
                                                  :state-province "Nairobi"
                                                  :street-address-lines ["United Nations Environment Programme"
                                                                         "Global Resource Information Database UNEP/GRID"
                                                                         "P.O.Box 30552"]})]
                                   :last-name "UNEP/GRID"
                                   :roles ["DATA CENTER CONTACT"]})]})]})
        actual (c/parse-collection all-fields-collection-xml)]
    (is (= expected actual))))


(deftest validate-xml
  (testing "valid xml"
    (is (= 0 (count (c/validate-xml valid-collection-xml)))))
  (testing "invalid xml"
    (is (= [(str "Line 18 - cvc-complex-type.2.4.a: Invalid content was found starting with element 'XXXX'. "
                 "One of '{\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Data_Center_URL, "
                 "\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Data_Set_ID, "
                 "\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\":Personnel}' is expected.")]
           (c/validate-xml (s/replace valid-collection-xml "Personnel" "XXXX"))))))
