(ns cmr.umm-spec.models.collection
    "Defines UMM-C clojure records.")

(defrecord UMM-C
  [
   ;; This includes any metadata related dates. All metadata dates will have a Lineage/Scope of
   ;; ‘metadata’.
   MetadataLineage

   ;; This attribute specifies a word or phrase that describes the temporal resolution of the
   ;; dataset.
   TemporalKeyword

   ;; This element is used to identify the keywords from the EN ISO 19115-1:2014 Geographic
   ;; Information – Metadata – Part 1: Fundamentals (http://www.isotc211.org/) Topic Category Code
   ;; List. It is a high-level thematic classification to assist in the grouping and search of
   ;; available services.
   ISOTopicCategory

   ;; This element allows authors to provide words or phrases to further describe the data.
   AncillaryKeyword

   ;; Defines a named two-dimensional tiling system for the collection.
   TilingIdentificationSystem

   ;; This entity stores the data’s distinctive attributes (i.e. attributes used to describe the
   ;; unique characteristics of the service which extend beyond those defined).
   Distribution

   ;; This element enables specification of Earth science keywords.
   ScienceKeyword

   ;; Abstract provides a brief description of the data or service the metadata represents.
   Abstract

   ;; The language used in the metadata record.
   MetadataLanguage

   ;; This element permits the user to properly cite the provider and specifies how the data should
   ;; be cited in professional scientific literature. This element provides a citation for the item
   ;; itself, and is not designed for listing bibliographic references of scientific research
   ;; articles arising from search results. A list of references related to the research results
   ;; should be in the Publication Reference element. A DOI that specifically identifies the service
   ;; is listed here.
   CollectionCitation

   ;; This includes any data related dates. All of these dates will have a Lineage/Scope of ‘data’.
   DataLineage

   ;; This includes any personnel responsible for this data and metadata through the party element.
   ;; The role (distributing, archiving, providing, and/or maintaining the data) is placed in the
   ;; role sub element. To support components or xlinks in the future the role was split from the
   ;; party sub elements, that way the data in the party element can be resued and stored
   ;; independently. This allows UMM to reuse the Responsiblilty element within other elements to
   ;; document responsibility for a specific reason. It can reuse the same person or organization
   ;; with different roles throughout the metadata.
   ResponsiblePersonnel

   ;; This element contains suggested usage or purpose for the data or service.
   Purpose

   ;; This element is used to identify other services, collections, visualizations, granules, and
   ;; other metadata types and resources that are associated with or dependent on the data described
   ;; by the metadata. This element is also used to identify a parent metadata record if it exists.
   ;; This usage should be reserved for instances where a group of metadata records are subsets that
   ;; can be better represented by one parent metadata record, which describes the entire set. In
   ;; some instances, a child may point to more than one parent. The EntryId is the same as the
   ;; element described elsewhere in this document where it contains and ID, and Version.
   MetadataAssociation

   ;; This element with the description field allows the author to provide information about any
   ;; constraints for accessing the service. This includes any special restrictions, legal
   ;; prerequisites, limitations and/or warnings on obtaining the service. Some words that may be
   ;; used in this element's value include: Public, In-house, Limited, None. The value field is used
   ;; for special ACL rules (Access Control Lists
   ;; (http://en.wikipedia.org/wiki/Access_control_list)). For example it can be used to hide
   ;; metadata when it isn't ready for public consumption.
   AccessConstraints

   ;; This class specifies the name of a place on Earth, a location within the Earth, a vertical
   ;; location, or a location outside of Earth.
   SpatialKeyword

   ;; Specifies the geographic and vertical (altitude, depth) coverage of the data.
   SpatialExtent

   ;; The entry ID of the service described by the metadata.
   EntryId

   ;; This element permits the author to provide the following information about an item described
   ;; in the metadata: 1) Quality of the item; and 2) Any quality assurance procedures followed in
   ;; producing the item. Examples of appropriate element information include: A) succinct
   ;; description; B) indicators of item quality or quality flags - both validated or invalidated;
   ;; C) recognized or potential problems with quality; D) established quality control mechanisms;
   ;; and E) established quantitative quality measurements.
   Quality

   ;; The title of the service described by the metadata.
   EntryTitle

   ;; This entity stores the data’s distinctive attributes (i.e. attributes used to describe the
   ;; unique characteristics of the service which extend beyond those defined).
   AdditionalAttribute

   ;; Describes the production status of the data set. There are three choices: PLANNED refers to
   ;; data sets to be collected in the future and are thus unavailable at the present time. For
   ;; Example: The Hydro spacecraft has not been launched, but information on planned data sets may
   ;; be available. IN WORK refers to data sets currently in production or data that is continuously
   ;; being collected or updated. For Example: data from the AIRS instrument on Aqua is being
   ;; collected continuously. COMPLETE refers to data sets in which no updates or further data
   ;; collection will be made. For Example: Nimbus-7 SMMR data collection has been completed.
   CollectionProgress

   ;; This includes any organizations responsible for this data and metadata through the party
   ;; element. The role (distributing, archiving, providing, and/or maintaining the data) is placed
   ;; in the role sub element. To support components or xlinks in the future the role was split from
   ;; the party sub elements, that way the data in the party element can be resued and stored
   ;; independently. This allows UMM to reuse the Responsiblilty element within other elements to
   ;; document responsibility for a specific reason. It can reuse the same person or organization
   ;; with different roles throughout the metadata.
   ResponsibleOrganization

   ;; This element describes the metadata standard name and version as received by the CMR.
   MetadataStandard

   ;; This element describes the relevant platforms used to acquire the data related to the service.
   ;; Platform types are controlled and include Spacecraft, Aircraft, Vessel, Buoy, Platform,
   ;; Station, Network, Human, etc.
   Platform

   ;; This element describes any data/service related URLs that include project home pages,
   ;; services, related data archives/servers, metadata extensions, direct links to online software
   ;; packages, web mapping services, links to images, or other data.
   RelatedUrl

   ;; This class stores the reference frame or system in which altitudes (elevations) are given. The
   ;; information contains the datum name, distance units and encoding method, which provide the
   ;; definition for the system. This field also stores the characteristics of the reference frame
   ;; or system from which depths are measured. The additional information in the field is geometry
   ;; reference data etc.
   SpatialInformation

   ;; This element identifies non-science-quality products such as Near-Real-Time collections. If a
   ;; collection does not contain this field, it will be assumed to be of science-quality.
   CollectionDataType

   ;; The Use Constraints element is designed to protect privacy and/or intellectual property by
   ;; allowing the author to specify how the item may or may not be used after access is granted.
   ;; This includes any special restrictions, legal prerequisites, terms and conditions, and/or
   ;; limitations on using the item. Providers may request acknowledgement of the item from users
   ;; and claim no responsibility for quality and completeness. Note: Use Constraints describe how
   ;; the item may be used once access has been granted; and is distinct from Access Constraints,
   ;; which refers to any constraints in accessing the item.
   UseConstraints

   ;; Formerly called Internal Directory Name (IDN) Node (IDN_Node). This element has been used
   ;; historically by the GCMD internally to identify association, responsibility and/or ownership
   ;; of the dataset, service or supplemental information. Note: This field only occurs in the DIF.
   ;; When a DIF record is retrieved in the ECHO10 or ISO 19115 formats, this element will not be
   ;; translated.\
   DirectoryName

   ;; This element contains the level identifier as described here:
   ;; https://earthdata.nasa.gov/data/standards-and-references/processing-levels
   ProcessingLevel

   ;; This element describes key bibliographic citations pertaining to the data.
   PublicationReference

   ;; This class contains attributes, which describe the temporal range of a specific collection.
   ;; This extent can be represented in a variety of ways: Range Date Time Single Date Time Periodic
   ;; Date Time
   TemporalExtent

   ;; For paleoclimate or geologic data, PaleoTemporalCoverage is the length of time represented by
   ;; the data collected. PaleoTemporalCoverage should be used when the data spans time frames
   ;; earlier than yyyy-mm-dd = 0001-01-01.
   PaleoTemporalCoverage

   ;; Describes the language used in the preparation, storage, and description of the service It is
   ;; the language of the information object, not the language used to describe or interact with the
   ;; metadata record. It does not refer to the language of the metadata.
   DataLanguage

   ;; This element describes the relevant platforms used to acquire the data related to the service.
   ;; Platform types are controlled and include Spacecraft, Aircraft, Vessel, Buoy, Platform,
   ;; Station, Network, Human, etc.
   Project
  ])

;; This element describes the relevant platforms used to acquire the data related to the service.
;; Platform types are controlled and include Spacecraft, Aircraft, Vessel, Buoy, Platform, Station,
;; Network, Human, etc. This platform includes zero or more instruments
(defrecord PlatformType
  [

  ])

;; For paleoclimate or geologic data, PaleoTemporalCoverage is the length of time represented by the
;; data collected. PaleoTemporalCoverage should be used when the data spans time frames earlier than
;; yyyy-mm-dd = 0001-01-01.
(defrecord PaleoTemporalCoverageType
  [
   ;; The chronostratigraphic units are selected from a UMM builder. The vocabulary is controlled.
   ChronostratigraphicUnit

   ;; The number of years furthest back in time including units Ga, Ma, ka or ybp.
   StartDate

   ;; The number of years closest to the present time including units Ga, Ma, ka or ybp.
   EndDate
  ])

(defrecord LocalCoordinateSystemType
  [
   ;; A description of the information provided to register the local system to the Earth (e.g.
   ;; control points, satellite ephemeral data, and inertial navigation data).
   GeoReferenceInformation

   ;; This class contains a description of the coordinate system and geo-reference information.
   Description
  ])

(defrecord ChronostratigraphicUnitType
  [
   Eon

   Era

   Epoch

   Stage

   Detailed_Classification

   Period
  ])

;; Describes a list of resolutions.
(defrecord ResolutionsType
  [
   ;; This element describes the minimum distance possible between two adjacent values, expressed in
   ;; distance units of measure for collection.
   Resolution
  ])

;; This element contains the level identifier as described here:
;; https://earthdata.nasa.gov/data/standards-and-references/processing-levels
(defrecord ProcessingLevelType
  [
   ;; Description of Processing Level.
   ProcessingLevelDescription

   ;; The processing level class contains the level identifier and level description of the
   ;; collection.
   Id
  ])

(defrecord GeographicCoordinateSystemType
  [
   ;; Units of measure used for the geodetic latitude and longitude resolution values. For lat, a 2
   ;; digit decimal number from 0-90; for lon, a 3 digit decimal number from 0-180. + or absence of
   ;; - for values north of equator or values west of prime meridian; - for all others.
   GeographicCoordinateUnits

   ;; The minimum difference between two adjacent latitude values expressed in Geographic Coordinate
   ;; Units of measure.
   LatitudeResolution

   ;; The minimum difference between two adjacent longitude values expressed in Geographic
   ;; Coordinate Units of measure.
   LongitudeResolution
  ])

;; The reference frame or system from which altitude or depths are measured. The term 'altitude' is
;; used instead of the common term 'elevation' to conform to the terminology in Federal Information
;; Processing Standards 70-1 and 173. The information contains the datum name, distance units and
;; encoding method, which provide the definition for the system.
(defrecord VerticalSystemDefinitionType
  [
   ;; The identification given to the level surface taken as the surface of reference from which
   ;; measurements are compared.
   DatumName

   ;; The recorded units
   DistanceUnits

   ;; The means used to encode measurements.
   EncodingMethod

   ;; Describes a list of resolutions.
   Resolution
  ])

(defrecord GeodeticModelType
  [
   ;; The identification given to the reference system used for defining the coordinates of points.
   HorizontalDatumName

   ;; Identification given to established representation of the Earth's shape.
   EllipsoidName

   ;; Radius of the equatorial axis of the ellipsoid.
   SemiMajorAxis

   ;; The ratios of the Earth's major axis to the difference between the major and the minor.
   DenominatorOfFlatteningRatio
  ])

;; This entity stores the reference frame or system from which altitudes (elevations) are measured.
;; The information contains the datum name, distance units and encoding method, which provide the
;; definition for the system. This table also stores the characteristics of the reference frame or
;; system from which depths are measured. The additional information in the table are geometry
;; reference data etc.
(defrecord SpatialInformationType
  [
   VerticalCoordinateSystem

   HorizontalCoordinateSystem

   ;; This attribute denotes whether the locality/coverage requires horizontal, vertical, or both in
   ;; the spatial domain and coordinate system definitions.
   SpatialCoverageType
  ])

(defrecord HorizontalCoordinateSystemType
  [
   GeodeticModel

   TODOMultiChoice
  ])

;; Formerly called Internal Directory Name (IDN) Node (IDN_Node). This element has been used
;; historically by the GCMD internally to identify association, responsibility and/or ownership of
;; the dataset, service or supplemental information. Note: This field only occurs in the DIF. When a
;; DIF record is retrieved in the ECHO10 or ISO 19115 formats, this element will not be translated.
(defrecord DirectoryNameType
  [
   ShortName

   LongName
  ])

(defrecord VerticalCoordinateSystemType
  [
   AltitudeSystemDefinition

   DepthSystemDefinition
  ])