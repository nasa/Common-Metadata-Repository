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
    [cmr.umm-spec.util :as u]))

(defn archive-and-distrution-information
  "Returns an ArchiveAndDistributionInformation based on given attributes"
  [attribs]
  (umm-c/map->ArchiveAndDistributionInformationType attribs))

(defn file-distribution-information
  "Returns a FileDistributionInformation based on given attributes"
  [attribs]
  (umm-c/map->FileDistributionInformationType attribs))

(defn location-keyword
  "Return a location keyword based on the given attributes."
  [attribs]
  (umm-c/map->LocationKeywordType attribs))

(defn instrument
  "Return an instrument based on instrument attribs"
  [attribs]
  (umm-cmn/map->InstrumentType (merge {:ShortName (d/unique-str "short-name")}
                                      attribs)))

(defn access-constraints
  "Return an access constraint based on passed in attribs"
  [attribs]
  (umm-cmn/map->AccessConstraintsType {:Value (:Value attribs)
                                       :Description (:Description attribs)}))

(defn instruments
  "Return a sequence of instruments with the given short names"
  [& short-names]
  (map #(umm-cmn/map->InstrumentType
          {:ShortName %
           :LongName (d/unique-str "long-name")})
       short-names))

(defn instrument-with-childinstruments
  "Return an instrument, with a sequence of child instruments"
  [short-name & child-instrument-short-names]
  (let [childinstruments (apply instruments child-instrument-short-names)]
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

(defn platform-with-instrument-and-childinstruments
  "Return a platform with an instrument and a list of child instruments"
  [plat-short-name instr-short-name & child-instrument-short-names]
  (let [instr-with-sensors (apply instrument-with-childinstruments instr-short-name child-instrument-short-names)]
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

(defn data-center
  "Returns data center"
  [attribs]
  (umm-cmn/map->DataCenterType attribs))

(defn directory-name
  "Returns directory name"
  [attribs]
  (umm-c/map->DirectoryNameType attribs))

(defn related-url
  "Creates related url for online_only test"
  ([]
   (related-url nil))
  ([attribs]
   (umm-cmn/map->RelatedUrlType (merge {:URL (d/unique-str "http://example.com/file")
                                        :Description (d/unique-str "description")}
                                       attribs))))

(defn resource-citation
  "Returns ResourceCitation"
  [attribs]
  (umm-cmn/map->ResourceCitationType attribs))

(defn tiling-identification-system
  "Creates TilingIdentificationSystem specific attribute"
  [name]
  (umm-c/map->TilingIdentificationSystemType
    {:TilingIdentificationSystemName name
     :Coordinate1 {:MinimumValue 0.0 :MaximumValue 0.0}
     :Coordinate2 {:MinimumValue 0.0 :MaximumValue 0.0}}))

(defn tiling-identification-systems
  "Returns a sequence of tiling-identification-systems with the given names"
  [& names]
  (map tiling-identification-system names))

(defn spatial
  [attributes]
  (let [{:keys [sc hsd vsds gsr orbit]} attributes]
    (umm-c/map->SpatialExtentType {:SpatialCoverageType sc
                                   :HorizontalSpatialDomain
                                     (when hsd (umm-c/map->HorizontalSpatialDomainType hsd))
                                   :VerticalSpatialDomains
                                     (when vsds (map umm-c/map->VerticalSpatialDomainType vsds))
                                   :GranuleSpatialRepresentation gsr
                                   :OrbitParameters (when orbit (umm-c/map->OrbitParametersType orbit))})))

(defn contact-person
  "Creates a Personnel record for the opendata tests."
  ([first-name last-name email]
   (contact-person first-name last-name email "dummy"))
  ([first-name last-name email role]
   (let [contact-info (when email
                        (umm-cmn/map->ContactInformationType
                          {:ContactMechanisms [(umm-cmn/map->ContactMechanismType
                                                 {:Type "Email"
                                                  :Value email})]}))]
     (umm-cmn/map->ContactPersonType {:FirstName first-name
                                      :LastName last-name
                                      :ContactInformation contact-info
                                      :Roles [role]}))))

;; Used for testing warnings when reqired properties are missing.
(def umm-c-missing-properties
  "This is the UMM-C that misses required properties."
  {:ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"})

(def umm-c-missing-properties-dif
  "This is the minimal valid UMM-C."
  {:DataCenters [(umm-cmn/map->DataCenterType
                   {:Roles ["ARCHIVER"]
                    :ShortName "AARHUS-HYDRO"
                    :LongName "Hydrogeophysics Group, Aarhus University "})]
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "EARTH SCIENCE SERVICES"
                       :Topic "DATA ANALYSIS AND VISUALIZATION"
                       :Term "GEOGRAPHIC INFORMATION SYSTEMS"})]
   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"})

(def umm-c-missing-properties-dif10
  "This is the minimal valid UMM-C."
  {:Platforms [(umm-cmn/map->PlatformType
                 {:ShortName "A340-600" :LongName "Airbus A340-600"})]
   :DataCenters [(umm-cmn/map->DataCenterType
                   {:Roles ["ARCHIVER"]
                    :ShortName "AARHUS-HYDRO"
                    :LongName "Hydrogeophysics Group, Aarhus University "})]
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "EARTH SCIENCE SERVICES"
                       :Topic "DATA ANALYSIS AND VISUALIZATION"
                       :Term "GEOGRAPHIC INFORMATION SYSTEMS"})]
   :SpatialExtent (umm-c/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})
   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"
   :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(t/date-time 2012)]})]})

(def minimal-umm-c
  "This is the minimal valid UMM-C."
  {:Platforms [(umm-cmn/map->PlatformType
                 {:ShortName "A340-600" :LongName "Airbus A340-600" :Type "Aircraft"})]
   :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
   :DataCenters [(umm-cmn/map->DataCenterType
                   {:Roles ["ARCHIVER"]
                    :ShortName "AARHUS-HYDRO"
                    :LongName "Hydrogeophysics Group, Aarhus University "})]
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "EARTH SCIENCE SERVICES"
                       :Topic "DATA ANALYSIS AND VISUALIZATION"
                       :Term "GEOGRAPHIC INFORMATION SYSTEMS"})]
   :SpatialExtent (umm-c/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})
   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :CollectionProgress "COMPLETE"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"
   :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(t/date-time 2012)]})]})

(defn collection-missing-properties
  "Returns a UmmCollection missing reqired properties"
  ([]
   (collection-missing-properties {}))
  ([attribs]
   (umm-c/map->UMM-C (merge umm-c-missing-properties attribs))))

(defn collection-missing-properties-dif
  "Returns a UmmCollection missing reqired properties"
  ([]
   (collection-missing-properties {}))
  ([attribs]
   (umm-c/map->UMM-C (merge umm-c-missing-properties-dif attribs))))

(defn collection-missing-properties-dif10
  "Returns a UmmCollection missing reqired properties"
  ([]
   (collection-missing-properties {}))
  ([attribs]
   (umm-c/map->UMM-C (merge umm-c-missing-properties-dif10 attribs))))

(defn collection-without-minimal-attribs
  "Return a UMM Collection that hasn't been merged with the minimal values.
   Used only for testing purposes"
  [index attribs]
  (umm-c/map->UMM-C
   (merge
    {:ShortName (str "Short Name " index)
     :Version (str "V" index)
     :EntryTitle (str "Entry Title " index)}
    attribs)))

(defn collection
  "Returns a UmmCollection from the given attribute map."
  ([]
   (collection {}))
  ([attribs]
   (umm-c/map->UMM-C (merge minimal-umm-c attribs)))
  ([index attribs]
   (umm-c/map->UMM-C
    (merge
     minimal-umm-c
     {:ShortName (str "Short Name " index)
      :Version (str "V" index)
      :EntryTitle (str "Entry Title " index)}
     attribs))))

(defn collection-concept
  "Returns the collection for ingest with the given attributes"
  ([attribs]
   (collection-concept attribs :echo10))
  ([attribs concept-format]
   (let [{:keys [provider-id native-id]} attribs]
     (-> attribs
         collection
         (assoc :provider-id provider-id :native-id native-id)
         (d/umm-c-collection->concept concept-format)))))
