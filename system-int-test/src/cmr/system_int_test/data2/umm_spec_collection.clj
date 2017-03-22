(ns cmr.system-int-test.data2.umm-spec-collection
  "Contains data generators for example based testing in system integration tests."
  (:require 
    [clj-time.core :as t]
    [clj-time.format :as f]
    [cmr.common.date-time-parser :as p]
    [cmr.common.util :as util]
    [cmr.system-int-test.data2.core :as d]
    [cmr.umm-spec.additional-attribute :as aa]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.temporal :as umm-spec-temporal]
    [cmr.umm-spec.util :as u]))
(defn psa
  "Creates a product specific attribute."
  [psa]
  (let [{:keys [name group description data-type value min-value max-value]} psa]
    (if (or (some? min-value) (some? max-value))
      (umm-cmn/map->AdditionalAttributeType
        {:Name name
         :Group group
         :Description (or description "Generated")
         :DataType data-type
         :ParameterRangeBegin (aa/gen-value data-type min-value)
         :ParameterRangeEnd (aa/gen-value data-type max-value)})
      (umm-cmn/map->AdditionalAttributeType
        {:Name name
         :Group group
         :Description (or description "Generated")
         :DataType data-type
         :Value (aa/gen-value data-type value)}))))

(defn two-d
  "Creates two-d-coordinate-system specific attribute"
  [name]
  (umm-cmn/map->TilingIdentificationSystemType
    {:TilingIdentificationSystemName name
     :Coordinate1 {:MinimumValue 0.0 :MaximumValue 0.0}
     :Coordinate2 {:MinimumValue 0.0 :MaximumValue 0.0}}))

(defn two-ds
  "Returns a sequence of two-d-coordinate-systems with the given names"
  [& names]
  (map two-d names))

(defn data-provider-timestamps 
  "Returns DataDates field of umm-spec collection"
  [datadates]
  (for [datadate datadates]
    (umm-cmn/map->DateType {:Date (p/parse-datetime (:Date datadate))
                            :Type (:Type datadate)}))) 

(defn temporal
  "Return a temporal with range date time of the given date times"
  [attribs]
  (let [{:keys [beginning-date-time ending-date-time single-date-time ends-at-present?]} attribs
        begin (when beginning-date-time (p/parse-datetime beginning-date-time))
        end (when ending-date-time (p/parse-datetime ending-date-time))
        single (when single-date-time (p/parse-datetime single-date-time))]
    (cond
      (or begin end)
      (umm-spec-temporal/temporal {:RangeDateTimes [(umm-cmn/->RangeDateTimeType begin end)]
                                   :EndsAtPresentFlag ends-at-present?})
      single
      (umm-spec-temporal/temporal {:SingleDateTimes [single]}))))

(defn science-keyword
  "Return a science keyword based on the given attributes." 
  [attribs]
  (umm-cmn/map->ScienceKeywordType attribs))

(defn sensor
  "Return a child instrument based on short-name"
  [attribs]
  (umm-cmn/map->InstrumentType (merge {:ShortName (d/unique-str "short-name")}
                                      attribs)))

(defn sensors
  "Return a sequence of child instruments with the given short names"
  [& short-names]
  (map #(umm-cmn/map->InstrumentType
          {:ShortName %
           :LongName (d/unique-str "long-name")})
       short-names))

(defn instrument
  "Return an instrument based on instrument attribs"
  [attribs]
  (umm-cmn/map->InstrumentType (merge {:ShortName (d/unique-str "short-name")}
                                      attribs)))

(defn instruments
  "Return a sequence of instruments with the given short names"
  [& short-names]
  (map #(umm-cmn/map->InstrumentType
          {:ShortName %
           :LongName (d/unique-str "long-name")})
       short-names))

(defn instrument-with-sensors
  "Return an instrument, with a sequence of child instruments"
  [short-name & child-instrument-short-names]
  (let [childinstruments (apply sensors child-instrument-short-names)]
    (umm-cmn/map->InstrumentType {:ShortName short-name
                                  :LongName (d/unique-str "long-name")
                                  :ComposedOf childinstruments})))

(defn characteristic
  "Returns a platform characteristic"
  [attribs]
  (umm-cmn/map->CharacteristicType (merge {:Name (d/unique-str "name")
                                           :Description "dummy"
                                           :DataType "dummy"
                                           :Unit "dummy"
                                           :Value "dummy"}
                                     attribs)))
(defn platform
  "Return a platform based on platform attribs"
  [attribs]
  (umm-cmn/map->PlatformType (merge {:ShortName (d/unique-str "short-name")
                                     :LongName (d/unique-str "long-name")
                                     :Type (d/unique-str "Type")}
                                    attribs)))
(defn platforms
  "Return a sequence of platforms with the given short names"
  [& short-names]
  (map #(umm-cmn/map->PlatformType
          {:ShortName %
           :LongName (d/unique-str "long-name")
           :Type (d/unique-str "Type")})
       short-names))

(defn platform-with-instruments
  "Return a platform with a list of instruments"
  [short-name & instr-short-names]
  (let [instruments (apply instruments instr-short-names)]
    (umm-cmn/map->PlatformType {:ShortName short-name
                                :LongName (d/unique-str "long-name")
                                :Type (d/unique-str "Type")
                                :Instruments instruments})))

(defn platform-with-instrument-and-sensors
  "Return a platform with an instrument and a list of sensors"
  [plat-short-name instr-short-name & child-instrument-short-names]
  (let [instr-with-sensors (apply instrument-with-sensors instr-short-name child-instrument-short-names)]
    (umm-cmn/map->PlatformType {:ShortName plat-short-name
                                :LongName (d/unique-str "long-name")
                                :Type (d/unique-str "Type")
                                :Instruments [instr-with-sensors]})))

(defn projects
  "Return a sequence of projects with the given short names"
  [& short-names]
  (map #(umm-cmn/map->ProjectType
          {:ShortName %
           :LongName (d/unique-str "long-name")})
       short-names))

(defn project
  "Return a project with the given short name and long name"
  [project-sn long-name]
  (umm-cmn/map->ProjectType
    {:ShortName project-sn
     :LongName (d/unique-str long-name)}))

(defn org
  "Return archive/ processing center"
  [roles center-name]
  (umm-cmn/map->DataCenterType
    {:Roles roles 
     :ShortName center-name}))

(defn related-url
  "Creates related url for online_only test"
  ([]
   (related-url nil))
  ([attribs]
   (umm-cmn/map->RelatedUrlType (merge {:URL (d/unique-str "http://example.com/file")
                                        :Description (d/unique-str "description")}
                                       attribs))))
(defn spatial
  [attributes]
  (let [{:keys [sc hsd vsds gsr orbit]} attributes]
    (umm-cmn/map->SpatialExtentType {:SpatialCoverageType sc
                                     :HorizontalSpatialDomain 
                                       (when hsd (umm-cmn/map->HorizontalSpatialDomainType hsd))
                                     :VerticalSpatialDomains 
                                       (when vsds (map umm-cmn/map->VerticalSpatialDomainType vsds))
                                     :GranuleSpatialRepresentation gsr
                                     :OrbitParameters (when orbit (umm-cmn/map->OrbitParametersType orbit))})))

(defn personnel
  "Creates a Personnel record for the opendata tests."
  ([first-name last-name email]
   (personnel first-name last-name email "dummy"))
  ([first-name last-name email role]
   (let [contacts (when email
                    [(umm-cmn/map->ContactInformationType 
                       {:ContactMechanisms [(umm-cmn/map->ContactMechanismType
                                              {:Type :email
                                               :Value email})]})])]                                            
     (umm-cmn/map->ContactPersonType {:FirstName first-name
                                      :LastName last-name
                                      :ContactInformation contacts
                                      :Roles [role]}))))

(def minimal-umm-c 
  "This is the minimum valid UMM-C."
  {:Platforms [(umm-cmn/map->PlatformType
                 {:ShortName "Platform"
                  :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
   :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
   :RelatedUrls [(umm-cmn/map->RelatedUrlType {:URL "http://google.com"
                                               :URLContentType "DistributionURL"
                                               :Type "GET DATA"})]
   :DataCenters [u/not-provided-data-center]
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
   :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})

   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :CollectionProgress "COMPLETE"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"
   :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(t/date-time 2012)]})]})

(defn collection
  "Returns a UmmCollection from the given attribute map."
  ([]
   (collection {}))
  ([attribs]
   (let [umm-c (merge minimal-umm-c attribs)]
     (umm-c/map->UMM-C (merge minimal-umm-c attribs)))))

(defn collection-concept
  "Returns the collection for ingest with the given attributes"
  ([attribs]
   (collection-concept attribs :echo10))
  ([attribs concept-format]
   (let [{:keys [provider-id native-id]} attribs]
     (-> attribs
         collection
         (assoc :provider-id provider-id :native-id native-id)
         (d/item->concept concept-format)))))
