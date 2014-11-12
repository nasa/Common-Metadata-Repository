(ns cmr.umm.test.echo10.collection
  "Tests parsing and generating ECHO10 Collection XML."
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
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.echo10.core :as echo10]
            [cmr.umm.collection :as umm-c]
            [cmr.umm.related-url-helper :as ru]))

(defn- umm-related-url->expected-related-url
  "Modifies the umm related-urls to the expected-related urls"
  [related-url]
  (let [{:keys [type title]} related-url]
    (case type
      ;; For resource type the title becomes description plus resource-type
      "VIEW RELATED INFORMATION" (assoc related-url :title (s/trim (str title " (USER SUPPORT)")))
      related-url)))

(defn umm-related-urls->expected-related-urls
  "Modifies the umm related-urls to the expected-related urls"
  [related-urls]
  ;; The related-urls are in the order of OnlineAccessURLs, OnlineResourceURLs and BrowseURLs
  (let [related-urls (map umm-related-url->expected-related-url related-urls)
        downloadable-urls (ru/downloadable-urls related-urls)
        resource-urls (ru/resource-urls related-urls)
        browse-urls (ru/browse-urls related-urls)]
    (seq (concat downloadable-urls resource-urls browse-urls))))

(defn- fix-contact-roles
  "Fix the roles for organizations that represent Contacts."
  [org]
  (let [role (name (:org-type org))
        persons (seq (map (fn [p]
                            (assoc p :roles [role]))
                          (:personnel org)))]
    (assoc org :personnel persons)))

(defn umm->expected-parsed-echo10
  "Modifies the UMM record for testing ECHO10. ECHO10 contains a subset of the total UMM fields so certain
  fields are removed for comparison to the parsed record"
  [coll]
  (let [{{:keys [short-name long-name version-id]} :product} coll
        entry-id (str short-name "_" version-id)
        organizations (map fix-contact-roles (:organizations coll))

        ;organizations (seq (filter #(not= :distribution-center (:type %)) (:organizations coll)))
        related-urls (umm-related-urls->expected-related-urls (:related-urls coll))]
    (-> coll
        ;; ECHO10 does not have entry-id and we generate it as concatenation of short-name and version-id
        (assoc :entry-id entry-id)
        ;; ECHO10 does not support Organizations of distribution-center which only exists in DIF.
        ;; UMMC-72 is proposing to change this.
        (assoc :organizations organizations)
        ;; ECHO10 OnlineResources' title is built as description plus resource-type
        (assoc :related-urls related-urls)
        umm-c/map->UmmCollection)))

(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (echo10/umm->echo10-xml collection)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))))))

(defspec generate-and-parse-collection-test 100
  (for-all [collection coll-gen/collections]
    (let [{{:keys [short-name version-id]} :product} collection
          xml (echo10/umm->echo10-xml collection)
          parsed (c/parse-collection xml)
          expected-parsed (umm->expected-parsed-echo10 collection)]
      (= parsed expected-parsed))))


(comment

  (let [x #cmr.umm.collection.UmmCollection{:entry-id "0", :entry-title "0", :summary "0", :product #cmr.umm.collection.Product{:short-name "0", :long-name "0", :version-id "0", :processing-level-id nil, :collection-data-type nil}, :access-value nil, :data-provider-timestamps #cmr.umm.collection.DataProviderTimestamps{:insert-time #=(org.joda.time.DateTime. 0), :update-time #=(org.joda.time.DateTime. 0), :delete-time nil}, :spatial-keywords nil, :temporal-keywords nil, :temporal #cmr.umm.collection.Temporal{:time-type nil, :date-type nil, :temporal-range-type nil, :precision-of-seconds nil, :ends-at-present-flag nil, :range-date-times [#cmr.umm.collection.RangeDateTime{:beginning-date-time #=(org.joda.time.DateTime. 0), :ending-date-time nil}], :single-date-times [], :periodic-date-times []}, :science-keywords [#cmr.umm.collection.ScienceKeyword{:category "0", :topic "0", :term "0", :variable-level-1 "0", :variable-level-2 nil, :variable-level-3 nil, :detailed-variable nil}], :platforms nil, :product-specific-attributes nil, :projects nil, :two-d-coordinate-systems nil, :related-urls nil, :organizations (#cmr.umm.collection.Organization{:org-type :distribution-center, :org-name "!", :personnel [#cmr.umm.collection.ContactPerson{:roles ["Investigator"], :addresses [#cmr.umm.collection.Address{:city "!", :country "!", :postal-code "!", :state-province "!", :street-address-lines ["!"]}], :emails nil, :first-name "!", :last-name "!", :middle-name nil, :phones [#cmr.umm.collection.Phone{:number "!", :number-type "!"}]}]}), :spatial-coverage nil, :associated-difs nil}]
    (let [xml (echo10/umm->echo10-xml x)]
      (println xml)
      ;(c/validate-xml xml)
      (println x)
      (println (umm->expected-parsed-echo10 x))
      (println (c/parse-collection xml))))

  )

;; This is a made-up include all fields collection xml sample for the parse collection test
(def all-fields-collection-xml
  "<Collection>
  <ShortName>MINIMAL</ShortName>
  <VersionId>1</VersionId>
  <InsertTime>1999-12-30T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <DeleteTime>2000-12-31T19:00:00-05:00</DeleteTime>
  <LongName>A minimal valid collection</LongName>
  <DataSetId>A minimal valid collection V 1</DataSetId>
  <Description>A minimal valid collection</Description>
  <CollectionDataType>NEAR_REAL_TIME</CollectionDataType>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
  <ProcessingCenter>SEDAC PC</ProcessingCenter>
  <ProcessingLevelId>1B</ProcessingLevelId>
  <ArchiveCenter>SEDAC AC</ArchiveCenter>
  <SpatialKeywords>
  <Keyword>Word-2</Keyword>
  <Keyword>Word-1</Keyword>
  <Keyword>Word-0</Keyword>
  </SpatialKeywords>
  <Temporal>
  <TimeType>Universal Time</TimeType>
  <DateType>Eastern Daylight</DateType>
  <TemporalRangeType>Long Range</TemporalRangeType>
  <PrecisionOfSeconds>1</PrecisionOfSeconds>
  <EndsAtPresentFlag>false</EndsAtPresentFlag>
  <RangeDateTime>
  <BeginningDateTime>1996-02-24T22:20:41-05:00</BeginningDateTime>
  <EndingDateTime>1997-03-24T22:20:41-05:00</EndingDateTime>
  </RangeDateTime>
  <RangeDateTime>
  <BeginningDateTime>1998-02-24T22:20:41-05:00</BeginningDateTime>
  <EndingDateTime>1999-03-24T22:20:41-05:00</EndingDateTime>
  </RangeDateTime>
  <SingleDateTime>2010-01-05T05:30:30.550-05:00</SingleDateTime>
  <PeriodicDateTime>
  <Name>autumn, southwest</Name>
  <StartDate>1998-08-12T20:00:00-04:00</StartDate>
  <EndDate>1998-09-22T21:32:00-04:00</EndDate>
  <DurationUnit>DAY</DurationUnit>
  <DurationValue>3</DurationValue>
  <PeriodCycleDurationUnit>MONTH</PeriodCycleDurationUnit>
  <PeriodCycleDurationValue>7</PeriodCycleDurationValue>
  </PeriodicDateTime>
  </Temporal>
  <Contacts>
  <Contact>
  <Role>INVESTIGATOR</Role>
  <OrganizationName>Undefined</OrganizationName>
  <OrganizationAddresses>
  <Address>
  <StreetAddress>Laboratory for Hydrospheric Processes Cryospheric Sciences Branch NASA/Goddard Space Flight Center Code 614 </StreetAddress>
  <City>Greenbelt</City>
  <StateProvince>MD</StateProvince>
  <PostalCode>20771</PostalCode>
  <Country>USA</Country>
  </Address>
  </OrganizationAddresses>
  <OrganizationPhones>
  <Phone>
  <Number>301 614-5708</Number>
  <Type>Telephone</Type>
  </Phone>
  </OrganizationPhones>
  <OrganizationEmails>
  <Email>josefino.c.comiso@nasa.gov</Email>
  </OrganizationEmails>
  <ContactPersons>
  <ContactPerson>
  <FirstName>JOSEPHINO 'JOEY'</FirstName>
  <LastName>COMISO</LastName>
  </ContactPerson>
  </ContactPersons>
  </Contact>
  </Contacts>
  <ScienceKeywords>
  <ScienceKeyword>
  <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
  <TopicKeyword>CRYOSPHERE</TopicKeyword>
  <TermKeyword>SNOW/ICE</TermKeyword>
  <VariableLevel1Keyword>
  <Value>ALBEDO</Value>
  <VariableLevel2Keyword>
  <Value>BETA</Value>
  <VariableLevel3Keyword>GAMMA</VariableLevel3Keyword>
  </VariableLevel2Keyword>
  </VariableLevel1Keyword>
  <DetailedVariableKeyword>DETAILED</DetailedVariableKeyword>
  </ScienceKeyword>
  <ScienceKeyword>
  <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
  <TopicKeyword>CRYOSPHERE</TopicKeyword>
  <TermKeyword>SEA ICE</TermKeyword>
  <VariableLevel1Keyword>
  <Value>REFLECTANCE</Value>
  </VariableLevel1Keyword>
  </ScienceKeyword>
  </ScienceKeywords>
  <AdditionalAttributes>
  <AdditionalAttribute>
  <Name>String add attrib</Name>
  <DataType>STRING</DataType>
  <Description>something string</Description>
  <ParameterRangeBegin>alpha</ParameterRangeBegin>
  <ParameterRangeEnd>bravo</ParameterRangeEnd>
  <Value>alpha1</Value>
  </AdditionalAttribute>
  <AdditionalAttribute>
  <Name>Float add attrib</Name>
  <DataType>FLOAT</DataType>
  <Description>something float</Description>
  <ParameterRangeBegin>0.1</ParameterRangeBegin>
  <ParameterRangeEnd>100.43</ParameterRangeEnd>
  <Value>12.3</Value>
  </AdditionalAttribute>
  </AdditionalAttributes>
  <Platforms>
  <Platform>
  <ShortName>RADARSAT-1</ShortName>
  <LongName>RADARSAT-LONG-1</LongName>
  <Type>Spacecraft</Type>
  <Instruments>
  <Instrument>
  <LongName>SAR long name</LongName>
  <ShortName>SAR</ShortName>
  <Technique>itechnique</Technique>
  <Sensors>
  <Sensor>
  <LongName>SNA long name</LongName>
  <ShortName>SNA</ShortName>
  <Technique>technique</Technique>
  </Sensor>
  <Sensor>
  <ShortName>SNB</ShortName>
  </Sensor>
  </Sensors>
  </Instrument>
  <Instrument>
  <ShortName>MAR</ShortName>
  </Instrument>
  </Instruments>
  </Platform>
  <Platform>
  <ShortName>RADARSAT-2</ShortName>
  <LongName>RADARSAT-LONG-2</LongName>
  <Type>Spacecraft-2</Type>
  </Platform>
  </Platforms>
  <Campaigns>
  <Campaign>
  <ShortName>ESI</ShortName>
  <LongName>Environmental Sustainability Index</LongName>
  </Campaign>
  <Campaign>
  <ShortName>EVI</ShortName>
  <LongName>Environmental Vulnerability Index</LongName>
  </Campaign>
  <Campaign>
  <ShortName>EPI</ShortName>
  <LongName>Environmental Performance Index</LongName>
  </Campaign>
  </Campaigns>
  <TwoDCoordinateSystems>
  <TwoDCoordinateSystem>
  <TwoDCoordinateSystemName>name0</TwoDCoordinateSystemName>
  <Coordinate1>
  <MinimumValue>0</MinimumValue>
  <MaximumValue>11</MaximumValue>
  </Coordinate1>
  <Coordinate2>
  <MinimumValue>0</MinimumValue>
  <MaximumValue>100</MaximumValue>
  </Coordinate2>
  </TwoDCoordinateSystem>
  <TwoDCoordinateSystem>
  <TwoDCoordinateSystemName>name1</TwoDCoordinateSystemName>
  <Coordinate1>
  <MinimumValue>1</MinimumValue>
  <MaximumValue>12</MaximumValue>
  </Coordinate1>
  <Coordinate2>
  <MinimumValue>-1</MinimumValue>
  <MaximumValue>101</MaximumValue>
  </Coordinate2>
  </TwoDCoordinateSystem>
  </TwoDCoordinateSystems>
  <OnlineAccessURLs>
  <OnlineAccessURL>
  <URL>http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac</URL>
  </OnlineAccessURL>
  </OnlineAccessURLs>
  <OnlineResources>
  <OnlineResource>
  <URL>http://camex.nsstc.nasa.gov/camex3/</URL>
  <Type>DATA ACCESS</Type>
  </OnlineResource>
  <OnlineResource>
  <URL>http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html</URL>
  <Type>Guide</Type>
  </OnlineResource>
  <OnlineResource>
  <URL>ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/</URL>
  <Description>Some description.</Description>
  <Type>Browse</Type>
  </OnlineResource>
  </OnlineResources>
  <AssociatedDIFs>
  <DIF>
  <EntryId>DIF-255</EntryId>
  </DIF>
  <DIF>
  <EntryId>DIF-256</EntryId>
  </DIF>
  <DIF>
  <EntryId>DIF-257</EntryId>
  </DIF>
  </AssociatedDIFs>
  </Collection>")

(def valid-collection-xml
  "<Collection>
  <ShortName>MINIMAL</ShortName>
  <VersionId>1</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <LongName>A minimal valid collection</LongName>
  <DataSetId>A minimal valid collection V 1</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
  </Collection>")

(deftest parse-collection-test
  (let [expected (umm-c/map->UmmCollection
                   {:entry-id "MINIMAL_1"
                    :entry-title "A minimal valid collection V 1"
                    :summary "A minimal valid collection"
                    :product (umm-c/map->Product
                               {:short-name "MINIMAL"
                                :long-name "A minimal valid collection"
                                :version-id "1"
                                :processing-level-id "1B"
                                :collection-data-type "NEAR_REAL_TIME"})
                    :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                                {:insert-time (p/parse-datetime "1999-12-30T19:00:00-05:00")
                                                 :update-time (p/parse-datetime "1999-12-31T19:00:00-05:00")
                                                 :delete-time (p/parse-datetime "2000-12-31T19:00:00-05:00")})
                    :spatial-keywords ["Word-2" "Word-1" "Word-0"]
                    :temporal
                    (umm-c/map->Temporal
                      {:time-type "Universal Time"
                       :date-type "Eastern Daylight"
                       :temporal-range-type "Long Range"
                       :precision-of-seconds 1
                       :ends-at-present-flag false
                       :range-date-times
                       [(umm-c/map->RangeDateTime
                          {:beginning-date-time (p/parse-datetime "1996-02-24T22:20:41-05:00")
                           :ending-date-time (p/parse-datetime "1997-03-24T22:20:41-05:00")})
                        (umm-c/map->RangeDateTime
                          {:beginning-date-time (p/parse-datetime "1998-02-24T22:20:41-05:00")
                           :ending-date-time (p/parse-datetime "1999-03-24T22:20:41-05:00")})]
                       :single-date-times
                       [(p/parse-datetime "2010-01-05T05:30:30.550-05:00")]
                       :periodic-date-times
                       [(umm-c/map->PeriodicDateTime
                          {:name "autumn, southwest"
                           :start-date (p/parse-datetime "1998-08-12T20:00:00-04:00")
                           :end-date (p/parse-datetime "1998-09-22T21:32:00-04:00")
                           :duration-unit "DAY"
                           :duration-value 3
                           :period-cycle-duration-unit "MONTH"
                           :period-cycle-duration-value 7})]})
                    :science-keywords
                    [(umm-c/map->ScienceKeyword
                       {:category "EARTH SCIENCE"
                        :topic "CRYOSPHERE"
                        :term "SNOW/ICE"
                        :variable-level-1 "ALBEDO"
                        :variable-level-2 "BETA"
                        :variable-level-3 "GAMMA"
                        :detailed-variable "DETAILED"})
                     (umm-c/map->ScienceKeyword
                       {:category "EARTH SCIENCE"
                        :topic "CRYOSPHERE"
                        :term "SEA ICE"
                        :variable-level-1 "REFLECTANCE"})]
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
                    :platforms
                    [(umm-c/map->Platform
                       {:short-name "RADARSAT-1"
                        :long-name "RADARSAT-LONG-1"
                        :type "Spacecraft"
                        :instruments [(umm-c/->Instrument
                                        "SAR"
                                        "SAR long name"
                                        "itechnique"
                                        [(umm-c/->Sensor "SNA" "SNA long name" "technique")
                                         (umm-c/->Sensor "SNB" nil nil)])
                                      (umm-c/->Instrument "MAR" nil nil nil)]})
                     (umm-c/map->Platform
                       {:short-name "RADARSAT-2"
                        :long-name "RADARSAT-LONG-2"
                        :type "Spacecraft-2"
                        :instruments nil})]
                    :projects
                    [(umm-c/map->Project
                       {:short-name "ESI"
                        :long-name "Environmental Sustainability Index"})
                     (umm-c/map->Project
                       {:short-name "EVI"
                        :long-name "Environmental Vulnerability Index"})
                     (umm-c/map->Project
                       {:short-name "EPI"
                        :long-name "Environmental Performance Index"})]
                    :two-d-coordinate-systems
                    [(umm-c/map->TwoDCoordinateSystem {:name "name0"})
                     (umm-c/map->TwoDCoordinateSystem {:name "name1"})]
                    :related-urls
                    [(umm-c/map->RelatedURL
                       {:type "GET DATA"
                        :url "http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac"})
                     (umm-c/map->RelatedURL
                       {:type "GET DATA"
                        :title "(DATA ACCESS)"
                        :url "http://camex.nsstc.nasa.gov/camex3/"})
                     (umm-c/map->RelatedURL
                       {:type "VIEW RELATED INFORMATION"
                        :sub-type "USER'S GUIDE"
                        :title "(Guide)"
                        :url "http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html"})
                     (umm-c/map->RelatedURL
                       {:type "GET RELATED VISUALIZATION"
                        :url "ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/"
                        :description "Some description."
                        :title "Some description. (Browse)"})]
                    :associated-difs ["DIF-255" "DIF-256" "DIF-257"]
                    :organizations
                    [(umm-c/map->Organization
                       {:org-type :processing-center
                        :org-name "SEDAC PC"})
                     (umm-c/map->Organization
                       {:org-type :archive-center
                        :org-name "SEDAC AC"})
                     (umm-c/map->Organization
                       {:org-type :INVESTIGATOR
                        :org-name "Undefined"
                        :personnel [(umm-c/map->ContactPerson {:roles ["INVESTIGATOR"]
                                                               :first-name "JOSEPHINO 'JOEY'"
                                                               :last-name "COMISO"
                                                               :phones [(umm-c/map->Phone
                                                                          {:number "301 614-5708"
                                                                           :number-type "Telephone"})]
                                                               :addresses [(umm-c/map->Address {:city "Greenbelt",
                                                                                                :country "USA",
                                                                                                :postal-code "20771",
                                                                                                :state-province "MD",
                                                                                                :street-address-lines
                                                                                                ["Laboratory for Hydrospheric Processes Cryospheric Sciences Branch NASA/Goddard Space Flight Center Code 614 "]})]
                                                               :emails ["josefino.c.comiso@nasa.gov"]})]})]})
        actual (c/parse-collection all-fields-collection-xml)]
    (is (= expected actual))))

(deftest validate-xml
  (testing "valid xml"
    (is (= 0 (count (c/validate-xml valid-collection-xml)))))
  (testing "invalid xml"
    (is (= ["Line 4 - cvc-datatype-valid.1.2.1: 'XXXX-12-31T19:00:00-05:00' is not a valid value for 'dateTime'."
            "Line 4 - cvc-type.3.1.3: The value 'XXXX-12-31T19:00:00-05:00' of element 'InsertTime' is not valid."
            "Line 5 - cvc-datatype-valid.1.2.1: 'XXXX-12-31T19:00:00-05:00' is not a valid value for 'dateTime'."
            "Line 5 - cvc-type.3.1.3: The value 'XXXX-12-31T19:00:00-05:00' of element 'LastUpdate' is not valid."]
           (c/validate-xml (s/replace valid-collection-xml "1999" "XXXX"))))))

(comment
  ;;;;;;;;;;;;;
  (let [collection (last (gen/sample coll-gen/collections 1))
        xml (echo10/umm->echo10-xml collection)
        parsed (c/parse-collection xml)]
    (println (= parsed collection))
    (clojure.data/diff parsed collection))
  ;;;;;;;;;;;;'
  )

