(ns cmr.umm-spec.test.echo10-expected-conversion
 "ECHO 10 specific expected conversion functionality"
 (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.string :as str]
   [cmr.common.util :as util :refer [update-in-each]]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.models.umm-common-models :as cmn]
   [cmr.umm-spec.related-url :as ru-gen]
   [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-to-xml-mappings.echo10.data-contact :as dc]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

(defn- fixup-echo10-data-dates
  [data-dates]
  (seq
    (remove #(= "REVIEW" (:Type %))
            (conversion-util/fixup-dif10-data-dates data-dates))))

(defn- echo10-expected-fees
  "Returns the fees if it is a number string, i.e., can be converted to a decimal, otherwise nil."
  [fees]
  (when fees
    (try
      (format "%9.2f" (Double. fees))
      (catch NumberFormatException e))))

(defn- echo10-expected-distributions
  "Returns the ECHO10 expected distributions for comparing with the distributions in the UMM-C
  record. ECHO10 only has one Distribution, so here we just pick the first one."
  [distributions]
  (some-> distributions
          first
          (assoc :Sizes nil :DistributionMedia nil)
          (update-in [:Fees] echo10-expected-fees)
          su/convert-empty-record-to-nil
          vector))

(defn- get-url-type-by-type
 "Get the url-type based on type. Return default there is no applicable
 url content type for the type."
 [type subtype]
 (if-let [url-content-type (su/type->url-content-type type)]
   {:URLContentType url-content-type
    :Type type
    :Subtype subtype}
   su/default-url-type))

(defn- expected-related-url-type
 "Expected related url URLContentType, Type, and Subtype based on whether an
 access url, browse url, or online resource url"
 [related-url]
 (let [{:keys [URLContentType Type Subtype]} related-url]
  (condp = URLContentType
   "DistributionURL" (if (= "GET DATA" Type)
                       (dissoc related-url :Subtype)
                       (if Subtype
                        (merge related-url (get-url-type-by-type Type Subtype))
                        related-url))
   "VisualizationURL" (-> related-url
                          (assoc :Type "GET RELATED VISUALIZATION")
                          (dissoc :Subtype))
   (if Subtype
    (merge related-url (get-url-type-by-type Type Subtype))
    related-url))))

(defn- expected-related-url-get-service
  "Returns related-url with the expected GetService"
  [related-url]
  (if (and (:GetService related-url)
           (= "DistributionURL" (:URLContentType related-url))
           (= "GET SERVICE" (:Type related-url))
           ;;MimeType is the only value that maps from echo10, so we can assume the GetService
           ;;map should be empty if MimeType is not provided.
           (not (= "Not provided" (get-in related-url [:GetService :MimeType]))))
    (-> related-url
        ;;The following fields are not applicable to ECHO10 format, and are always filled with default values.
        (assoc-in [:GetService :URI] nil)
        (assoc-in [:GetService :Protocol] su/not-provided)
        (assoc-in [:GetService :DataID] su/not-provided)
        (assoc-in [:GetService :DataType] su/not-provided)
        (assoc-in [:GetService :FullName] su/not-provided))
    (dissoc related-url :GetService)))

(defn- expected-echo10-related-urls
 [related-urls]
 (when (seq related-urls)
   (for [related-url related-urls]
    (cmn/map->RelatedUrlType
     (-> related-url
         expected-related-url-type
         (dissoc :Relation :FileSize :MimeType :GetData)
         expected-related-url-get-service
         (update :URL url/format-url true))))))

(defn- expected-echo10-reorder-related-urls
  "returns the RelatedUrls reordered - based on the order when echo10 is generated from umm."
  [umm-coll]
  (seq (concat (ru-gen/downloadable-urls (:RelatedUrls umm-coll))
               (ru-gen/resource-urls (:RelatedUrls umm-coll))
               (ru-gen/browse-urls (:RelatedUrls umm-coll)))))

(defn- expected-echo10-spatial-extent
  "Returns the expected ECHO10 SpatialExtent for comparison with the umm model."
  [spatial-extent]
  (let [spatial-extent (conversion-util/prune-empty-maps spatial-extent)]
    (if (get-in spatial-extent [:HorizontalSpatialDomain :Geometry])
      (update-in spatial-extent
                 [:HorizontalSpatialDomain :Geometry]
                 conversion-util/geometry-with-coordinate-system)
      spatial-extent)))

(defn- expected-echo10-platform-longname-with-default-value
  "Returns the expected ECHO10 LongName with default value."
  [platform]
  (-> platform
      (assoc :LongName (su/with-default (:LongName platform)))))

(defn- expected-echo10-additional-attribute
  [attribute]
  (-> attribute
      (assoc :Group nil)
      (assoc :UpdateDate nil)
      (assoc :MeasurementResolution nil)
      (assoc :ParameterUnitsOfMeasure nil)
      (assoc :ParameterValueAccuracy nil)
      (assoc :ValueAccuracyExplanation nil)
      (assoc :Description (su/with-default (:Description attribute)))))

(defn- expected-contact-mechanisms
   "Remove contact mechanisms with a type that is not supported by ECHO10. ECHO10 contact mechanisms
   are split up by phone and email in ECHO10 and will come back from XML in that order so make sure
   they are in the correct order."
  [mechanisms]
  (seq (concat
          (remove #(contains? dc/echo10-non-phone-contact-mechanisms (:Type %)) mechanisms)
          (filter #(= "Email" (:Type %)) mechanisms))))

(defn- expected-echo10-address
  "Expected address. All address fields are required in ECHO10, so replace with default
  when necessary"
  [address]
  (-> address
      (assoc :StreetAddresses [(dc/join-street-addresses (:StreetAddresses address))])
      (update :City su/with-default)
      (update :StateProvince su/with-default)
      (update :PostalCode su/with-default)
      (update :Country su/country-with-default)))

(defn- expected-echo10-contact-information
  "Expected contact information"
  [contact-information]
  (let [contact-information (-> contact-information
                                (assoc :RelatedUrls nil)
                                (update :Addresses #(when (seq %)
                                                      (mapv expected-echo10-address %)))
                                (update :ContactMechanisms expected-contact-mechanisms))]
    ;; Check for nil after updates because contact mechanisms could have been dropped making
    ;; contact information nil
    (when (seq (util/remove-nil-keys contact-information))
      (cmn/map->ContactInformationType contact-information))))

(defn- expected-echo10-contact-person
  "Returns an expected contact person for each role. ECHO10 only allows for 1 role per
  ContactPerson, so when converted to UMM a contact person is created for each role with
  the rest of the info copied."
  [contact-person]
  (when contact-person
   (for [role (:Roles contact-person)]
     (-> contact-person
         (assoc :ContactInformation nil)
         (assoc :Uuid nil)
         (assoc :NonDataCenterAffiliation nil)
         (update :FirstName su/with-default)
         (update :LastName su/with-default)
         (assoc :Roles [role])))))

(defn- expected-echo10-contact-persons
  "Returns the list of expected contact persons"
  [contact-persons]
  (when (seq contact-persons)
    (flatten
     (conversion-util/expected-contact-information-urls
       (mapv expected-echo10-contact-person contact-persons)
       "DataContactURL"))))

(defn- expected-echo10-data-center
  "Returns an expected data center for each role. ECHO10 only allows for 1 role per
  data center, so when converted to UMM a data center is created for each role with
  the rest of the info copied."
  [data-center]
  (for [role (:Roles data-center)]
    (-> data-center
        (assoc :ContactGroups nil)
        (update :ContactPersons expected-echo10-contact-persons)
        (assoc :Uuid nil)
        (assoc :LongName nil)
        (assoc :Roles [role])
        (assoc :ContactInformation (expected-echo10-contact-information (:ContactInformation data-center))))))

(defn- expected-echo10-data-centers
  "Returns the list of expected data centers"
  [data-centers]
  (if (seq data-centers)
    (flatten
     (conversion-util/expected-contact-information-urls
       (mapv expected-echo10-data-center data-centers)
       "DataCenterURL"))
    [su/not-provided-data-center]))

(defn- expected-metadata-dates
  "Return the update date since that's the only date persisted in ECHO10. Update date is
  represented as a date-time."
  [umm-coll]
  (when-let [update-date (date/metadata-update-date umm-coll)]
    [(cmn/map->DateType {:Date update-date
                         :Type "UPDATE"})]))

(defn- expected-science-keywords
  "Return science keywords if not null, otherwise the default science keywords"
  [science-keywords]
  (if (seq science-keywords)
    science-keywords
    su/not-provided-science-keywords))

(defn- expected-temporal-extents
  "ECHO10 only has 1 temporal extent so all of the data is in that, so on conversion to UMM we will
  return one extent with either all range date times, all single date times, or all periodic
  date times respectively. ECHO10 only allows for 1 date time type in a Temporal."
  [temporal-extents]
  (let [range-date-times (mapcat :RangeDateTimes temporal-extents)
        single-date-times (mapcat :SingleDateTimes temporal-extents)
        periodic-date-times (mapcat :PeriodicDateTimes temporal-extents)
        temporal-extent {:TemporalRangeType (:TemporalRangeType (first temporal-extents))
                         :PrecisionOfSeconds (:PrecisionOfSeconds (first temporal-extents))
                         :EndsAtPresentFlag (boolean (some :EndsAtPresentFlag temporal-extents))}]
    (cond
      (seq range-date-times)
      [(cmn/map->TemporalExtentType (assoc temporal-extent :RangeDateTimes range-date-times))]

      (seq single-date-times)
      [(cmn/map->TemporalExtentType (assoc temporal-extent :SingleDateTimes single-date-times))]

      (seq periodic-date-times)
      [(cmn/map->TemporalExtentType (assoc temporal-extent :PeriodicDateTimes periodic-date-times))])))

(defn- expected-collection-citations
  "Returns expected CollectionCitations, should only include there first CollectionCitation with
   a OtherCitationDetails value"
  [collection-citations]
  (when-not (empty? collection-citations)
    (when-let [other-citation-details (first (map :OtherCitationDetails collection-citations))]
      [(cmn/map->ResourceCitationType
        {:OtherCitationDetails other-citation-details})])))

(defn umm-expected-conversion-echo10
  [umm-coll]
  (-> umm-coll
      (assoc :DirectoryNames nil)
      ;; CMR 3523. DIF10 data makes the order inside the :RelatedUrls important:
      ;; access urls first, resource urls second, browse urls last - which is
      ;; the order used when umm is converted to echo10.
      (assoc :RelatedUrls (expected-echo10-reorder-related-urls umm-coll))
      (update :TemporalExtents expected-temporal-extents)
      (update :DataDates fixup-echo10-data-dates)
      (assoc :DataLanguage nil)
      (assoc :Quality nil)
      (assoc :UseConstraints nil)
      (assoc :PublicationReferences nil)
      (assoc :AncillaryKeywords nil)
      (assoc :ISOTopicCategories nil)
      (update-in [:DataCenters] expected-echo10-data-centers)
      (assoc :ContactGroups nil)
      (update-in [:ContactPersons] expected-echo10-contact-persons)
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in [:Distributions] echo10-expected-distributions)
      (update-in-each [:SpatialExtent :HorizontalSpatialDomain :Geometry :GPolygons]
                      conversion-util/fix-echo10-dif10-polygon)
      (update-in [:SpatialExtent] expected-echo10-spatial-extent)
      (update-in-each [:AdditionalAttributes] expected-echo10-additional-attribute)
      (update-in-each [:Projects] assoc :Campaigns nil)
      (update :RelatedUrls expected-echo10-related-urls)
      ;; We can't restore Detailed Location because it doesn't exist in the hierarchy.
      (update-in [:LocationKeywords] conversion-util/fix-location-keyword-conversion)
      ;; CMR 3253 This is added because it needs to support DIF10 umm. when it does roundtrip,
      ;; dif10umm-echo10(with default)-umm(without default needs to be removed)
      (update-in-each [:Platforms] expected-echo10-platform-longname-with-default-value)
      ;; CMR 2716 Getting rid of SpatialKeywords but keeping them for legacy purposes.
      (assoc :SpatialKeywords nil)
      (assoc :PaleoTemporalCoverages nil)
      (update :CollectionCitations expected-collection-citations)
      (assoc :MetadataDates (expected-metadata-dates umm-coll))
      (update :ScienceKeywords expected-science-keywords)
      (update :AccessConstraints conversion-util/expected-access-constraints)
      (update :CollectionProgress su/with-default)))
