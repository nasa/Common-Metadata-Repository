(ns cmr.umm-spec.test.iso19115-expected-conversion
  "ISO 19115 specific expected conversion functionality"
  (:require
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clojure.string :as string]
    [cmr.common.util :as util :refer [update-in-each]]
    [cmr.spatial.mbr :as m]
    [cmr.umm-spec.date-util :as date-util]
    [cmr.umm-spec.iso19115-2-util :as iso-util]
    [cmr.umm-spec.json-schema :as js]
    [cmr.umm-spec.location-keywords :as lk]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as cmn]
    [cmr.umm-spec.related-url :as ru-gen]
    [cmr.umm-spec.spatial-conversion :as spatial-conversion]
    [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
    [cmr.umm-spec.test.iso-shared :as iso-shared]
    [cmr.umm-spec.test.location-keywords-helper :as lkt]
    [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso]
    [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.additional-attribute :as iso-aa]
    [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.data-contact :as data-contact]
    [cmr.umm-spec.url :as url]
    [cmr.umm-spec.util :as su]
    [cmr.umm-spec.xml-to-umm-mappings.characteristics-data-type-normalization :as char-data-type-normalization]
    [cmr.umm-spec.xml-to-umm-mappings.iso-shared.iso-topic-categories :as iso-topic-categories]
    [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact :as xml-to-umm-data-contact]))

(defn- propagate-first
  "Returns coll with the first element's value under k assoc'ed to each element in coll.

  Example: (propagate-first :x [{:x 1} {:y 2}]) => [{:x 1} {:x 1 :y 2}]"
  [k coll]
  (let [v (get (first coll) k)]
    (for [x coll]
      (assoc x k v))))

(defn expected-iso-19115-2-temporal
  [temporal-extents]
  (->> temporal-extents
       (propagate-first :PrecisionOfSeconds)
       iso-shared/fixup-iso-ends-at-present
       (iso-shared/split-temporals :RangeDateTimes)
       (iso-shared/split-temporals :SingleDateTimes)
       iso-shared/sort-by-date-type-iso
       (#(or (seq %) su/not-provided-temporal-extents))))

(defn- expected-online-resource
 "Sanitize the values in online resource"
 [online-resource]
 (when online-resource
  (-> online-resource
      (update :Linkage #(url/format-url % true))
      (assoc :MimeType nil)
      (update :Description #(when % (str  %  " PublicationReference:"))))))

(defn- expected-doi-in-publication-reference
  "Returns the expected DOI field in a publication reference."
  [doi]
  (let [updated-doi (util/remove-nil-keys (dissoc doi :Authority))]
    (when (seq updated-doi)
      (cmn/map->DoiDoiType updated-doi))))

(defn- iso-19115-2-publication-reference
  "Returns the expected value of a parsed ISO-19115-2 publication references"
  [pub-refs]
  (seq (for [pub-ref pub-refs
             :when (and (:Title pub-ref) (:PublicationDate pub-ref))]
         (-> pub-ref
             (assoc :ReportNumber nil :Volume nil :PublicationPlace nil)
             (update-in [:DOI] expected-doi-in-publication-reference)
             (update-in [:PublicationDate] conversion-util/date-time->date)
             (update :ISBN su/format-isbn)
             (update :OnlineResource expected-online-resource)))))

(defn- expected-iso-19115-2-distributions
  "Returns the expected ISO19115-2 distributions for comparison."
  [distributions]
  (some->> distributions
           su/remove-empty-records
           vec))

(defn expected-iso-19115-2-related-urls
  [related-urls]
  (when (seq related-urls)
    (seq (for [related-url related-urls]
          (-> related-url
              (update :URL #(url/format-url % true)))))))

(defn- expected-collection-related-urls
 "Update the collection top level RelatedUrls. Do processing not applicable
 for data center/data contact RelatedUrls. DataCenter and DataContact URL
 types are not applicable here, so remove."
 [related-urls]
 (let [related-urls (expected-iso-19115-2-related-urls related-urls)]
   (seq (for [related-url
              (remove #(#{"DataCenterURL" "DataContactURL"} (:URLContentType %))
                      related-urls)]
          (cmn/map->RelatedUrlType
           (-> related-url
               (dissoc :FileSize :MimeType)
               iso-shared/expected-related-url-get-service
               iso-shared/expected-related-url-get-data
               (update :Description #(when % (string/trim %)))))))))

(defn- fix-iso-vertical-spatial-domain-values
  [vsd]
  (let [fix-val (fn [x]
                  (when x
                    ;; Vertical spatial domain values are encoded in a comma-separated string in ISO
                    ;; XML, so the values must be updated to match what we expect in the resulting
                    ;; XML document.
                    (string/trim x)))]
    (-> vsd
        (update-in [:Type] fix-val)
        (update-in [:Value] fix-val))))

(defn- update-iso-spatial
  [spatial-extent]
  (-> spatial-extent
      (update-in-each [:VerticalSpatialDomains] fix-iso-vertical-spatial-domain-values)
      conversion-util/prune-empty-maps))

(defn- group-metadata-assocations
  [mas]
  (let [{input-types true other-types false} (group-by (fn [ma] (= "INPUT" (:Type ma))) mas)]
    (seq (concat other-types input-types))))

(defn- normalize-bounding-rectangle
  [{:keys [WestBoundingCoordinate NorthBoundingCoordinate
           EastBoundingCoordinate SouthBoundingCoordinate]}]
  (let [{:keys [west north east south]} (m/mbr WestBoundingCoordinate
                                               NorthBoundingCoordinate
                                               EastBoundingCoordinate
                                               SouthBoundingCoordinate)]
    (umm-c/map->BoundingRectangleType
      {:WestBoundingCoordinate west
       :NorthBoundingCoordinate north
       :EastBoundingCoordinate east
       :SouthBoundingCoordinate south})))

(defn- fix-bounding-rectangles
  "Bounding rectangles in UMM JSON during conversion will be passed to the MBR namespace which does
  some normalization on them. The result is still the same area but the values will not be identical."
  [umm]
  (if-let [brs (seq (get-in umm conversion-util/bounding-rectangles-path))]
    (assoc-in umm conversion-util/bounding-rectangles-path (mapv normalize-bounding-rectangle brs))
    umm))

(defn- expected-iso19115-additional-attribute
  [attribute]
  (-> attribute
      (assoc :UpdateDate nil)
      (assoc :Description (su/with-default (:Description attribute)))))

(defn- expected-iso19115-additional-attributes
  "Update each additional attribute and re-order the additional attributes to be non data Quality
  then data quality additional attributes"
  [additional-attributes]
  (let [aas (map expected-iso19115-additional-attribute additional-attributes)]
    (concat
      (seq (remove #(iso-aa/data-quality-info-attributes (:Name %)) aas))
      (seq (filter #(iso-aa/data-quality-info-attributes (:Name %)) aas)))))

(defn- expected-iso19115-data-dates
  "Returns the expected ISO19115 DataDates"
  [data-dates]
  (if data-dates
    data-dates
    [(cmn/map->DateType {:Date (f/parse date-util/default-date-value)
                         :Type "CREATE"})]))

(defn- expected-science-keywords
  "Returns science keywords if not nil, otherwise default"
  [science-keywords]
  (if (seq science-keywords)
    science-keywords
    su/not-provided-science-keywords))

(defn expected-iso-contact-mechanisms
 "Returns expected contact mechanisms with not translated types removed and ordered by phone,
 fax, email"
 [contact-mechanisms]
 (when-let [contact-mechanisms (seq
                                (remove #(nil? (:Type %))
                                 (map #(assoc % :Type (get data-contact/translated-contact-mechanism-types (:Type %)))
                                      contact-mechanisms)))]
  (let [groups (group-by :Type contact-mechanisms)] ; Group for ordering
    (concat
     (get groups "Telephone")
     (get groups "Fax")
     (get groups "Email")))))

(defn expected-contact-info-related-urls
  "Returns expected related url - take the first related url and the first url in related urls"
  [related-urls]
  (when related-urls
    (expected-iso-19115-2-related-urls (take 1 related-urls))))

(defn- expected-iso-contact-information
 "Returns expected contact information - 1 address, only certain contact mechanisms are mapped"
 [contact-info url-content-type]
 (-> contact-info
     (update :RelatedUrls expected-contact-info-related-urls)
     (update-in-each [:RelatedUrls] #(assoc % :URLContentType url-content-type))
     (update-in-each [:RelatedUrls] #(assoc % :Type "HOME PAGE"))
     (update-in-each [:RelatedUrls] #(dissoc % :Subtype :GetData :GetService))
     (update :ContactMechanisms expected-iso-contact-mechanisms)
     (update :Addresses #(take 1 %))))

(defn update-short-and-long-name
 "ISO only has 1 field for both short and long and they get combined with a delimeter. combined
 and then parse the short and long name here to mimic what UMM -> ISO -> UMM will do."
 [data-center]
 (let [{:keys [ShortName LongName]} data-center
       organization-name (if LongName
                          (str ShortName " &gt; " LongName)
                          ShortName)
       name-split (string/split organization-name #"&gt;|>")]
   (if (> (count name-split) 0)
    (-> data-center
        (assoc :ShortName (string/trim (first name-split)))
        (assoc :LongName (when (> (count name-split) 1)
                          (string/join " " (map string/trim (rest name-split))))))
    (-> data-center
        (assoc :ShortName su/not-provided)
        (assoc :LongName nil)))))

(defn update-person-names
 "ISO only has one field for the whole name. When we go from UMM -> ISO, we combine the names into
 one field then on ISO -> UMM we split them up. Need to do this processing to handle spaces in names
 as well as leading/trailing spaces."
 [person]
 (let [{:keys [FirstName MiddleName LastName]} person
       combined-name (string/trim (string/join " " [FirstName MiddleName LastName]))
       names (string/split combined-name #" {1,}")
       num-names (count names)]
  (if (= 1 num-names)
    (-> person
        (assoc :LastName (first names))
        (dissoc :FirstName)
        (dissoc :MiddleName))
    (-> person
        (assoc :FirstName (first names))
        (assoc :MiddleName (string/join " " (subvec names 1 (dec num-names))))
        (update :MiddleName #(when (seq %) %)) ; nil if empty
        (assoc :LastName (last names))))))

(defn- iso-contact-person-roles
  "If not a metadata author, return default role"
  [roles role-default]
  (if (contains? (set roles) "Metadata Author")
    ["Metadata Author"]
    [role-default]))

(defn- expected-contact-person
 "Return an expected ISO contact person. Role is based on whether it is a contact person associated
 with a data center or standalone contact person"
 [person role]
 (-> person
     (dissoc :Uuid)
     (update :Roles iso-contact-person-roles role)
     update-person-names
     (update :ContactInformation #(expected-iso-contact-information % "DataContactURL"))))

(defn- expected-contact-group
 "Return an expected ISO contact group"
 [group]
 (-> group
     (dissoc :Uuid)
     (assoc :Roles ["User Services"])
     (update :ContactInformation #(expected-iso-contact-information % "DataContactURL"))))

(defn- expected-iso-data-center
 "Expected data center - trim whitespace from short name and long name, update contact info"
 [data-center]
 (-> data-center
     (update-in-each [:ContactPersons] #(expected-contact-person % "Data Center Contact"))
     (assoc :ContactGroups nil)
     (assoc :Uuid nil)
     update-short-and-long-name
     (update :ContactInformation #(expected-iso-contact-information % "DataCenterURL"))
     cmn/map->DataCenterType))

(defn- expected-data-center-contacts
 "In ISO, data centers, persons, and groups are all contacts and only relate by name. If we have
 multiple data centers with the same name, make sure they have the same ContactPersons, because
 they will after ISO -> UMM translation.

 Group the data centers by name and for each group (DC's with the same name) create a list of all
 persons for that DC name and set each DC's ContactPersons to that list. Returns a list of updated
 data centers"
 [data-centers]
 (let [data-center-groups (group-by #(select-keys % [:ShortName :LongName]) data-centers)]
  (apply concat
   (for [group (vals data-center-groups)
         :let [persons (apply concat (map :ContactPersons group))]] ; All persons for DC's with that name
     (map #(assoc % :ContactPersons persons) group)))))

(defn- expected-iso-data-centers
 "For each data center, if there are multiple roles make a copy of the data center for each role"
 [data-centers]
 (let [data-centers (map expected-iso-data-center data-centers)
       data-centers (expected-data-center-contacts data-centers)]
  (for [data-center data-centers
        role (:Roles data-center)]
   (assoc data-center :Roles [role]))))

(defn- keep-first-collection-citation
  "Returns collection-citations with only the first collection-citation in it, trimmed.
   When collection-citations is nil, return [{}]."
  [collection-citations]
  (if collection-citations
    (conj [] (cmn/map->ResourceCitationType
               (-> collection-citations
                   first
                   iso-shared/trim-collection-citation
                   (as-> cc (if (:OnlineResource cc)
                              (update cc :OnlineResource #(assoc % :MimeType nil))
                              cc)))))
    [{}]))

(defn- expected-geodetic-model
  "Trims all the string values, returns model version of map."
  [geodetic-model]
  (when geodetic-model
    (-> geodetic-model
        (update :EllipsoidName iso-util/safe-trim)
        (update :HorizontalDatumName iso-util/safe-trim)
        umm-c/map->GeodeticModelType)))

(defn- expected-local-coordinate-system
  "Trims all the string values, returns model version of map."
  [local-coordinate-system]
  (when local-coordinate-system
    (-> local-coordinate-system
        (update :Description iso-util/safe-trim)
        (update :GeoReferenceInformation iso-util/safe-trim)
        umm-c/map->LocalCoordinateSystemType)))

(defn update-horizontal-data-resolutions
  "Add the type and remove nil keys in HorizontalDataResolution."
  [c]
  (if (seq (get-in c [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution]))
    (-> c
        (util/update-in-each
          [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution :NonGriddedResolutions]
          util/remove-nil-keys)
        (util/update-in-each
          [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution :NonGriddedRangeResolutions]
          util/remove-nil-keys)
        (util/update-in-each
          [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution :GenericResolutions]
          util/remove-nil-keys)
        (util/update-in-each
          [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution :GriddedResolutions]
          util/remove-nil-keys)
        (util/update-in-each
          [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution :GriddedRangeResolutions]
          util/remove-nil-keys)
        ;; The order here is important because calling umm-c/map->HorizontalDataResolutionType will put back the nil values if
        ;; remove-nil-keys is called first.
        (update-in
          [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution]
          umm-c/map->HorizontalDataResolutionType)
        (update-in
          [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution]
          util/remove-nil-keys))
    c))

(defn umm-expected-conversion-iso19115
  [umm-coll]
  (-> umm-coll
      (assoc :DirectoryNames nil)
      fix-bounding-rectangles
      (update :SpatialExtent update-iso-spatial)
      (update-in [:SpatialExtent :VerticalSpatialDomains]
                 spatial-conversion/drop-invalid-vertical-spatial-domains)
      (update :TemporalExtents expected-iso-19115-2-temporal)
      (update :DataDates expected-iso19115-data-dates)
      (update :DataLanguage #(or % "eng"))
      (update :ProcessingLevel su/convert-empty-record-to-nil)
      (update :PublicationReferences iso-19115-2-publication-reference)
      (update :RelatedUrls expected-collection-related-urls)
      (update :AdditionalAttributes expected-iso19115-additional-attributes)
      (update :MetadataAssociations group-metadata-assocations)
      (update :ISOTopicCategories iso-shared/expected-iso-topic-categories)
      (assoc :SpatialKeywords nil)
      (assoc :PaleoTemporalCoverages nil)
      ;;add contact persons introduced by CollectionCitation.
      (assoc :ContactPersons (iso-shared/update-contact-persons-from-collection-citation
                               (map #(expected-contact-person % "Technical Contact") (:ContactPersons umm-coll))
                               (iso-shared/trim-collection-citation (first (:CollectionCitations umm-coll)))))
      ;; CollectionCitation's Title and UMM's EntryTitle share the same xml element, So do CollectionCitation's
      ;; Version and UMM's Version. When translate to xml file, we use the UMM's EntryTitle and Version. The values
      ;; could be different.
      ;; If the original CollectionCitation is nil, we should expect it to contain Title and Version.
      ;; If there are multiple CollectionCitations, we should only expect the first one.
      (assoc :CollectionCitations (map #(assoc % :Title (:EntryTitle umm-coll) :Version (:Version umm-coll))
                                       (keep-first-collection-citation (:CollectionCitations umm-coll))))
      (assoc :ContactGroups (map expected-contact-group (:ContactGroups umm-coll)))
      (update :DataCenters expected-iso-data-centers)
      (update :ScienceKeywords expected-science-keywords)
      (update :AccessConstraints conversion-util/expected-access-constraints)
      (assoc :CollectionProgress (conversion-util/expected-coll-progress umm-coll))
      (update :TilingIdentificationSystems spatial-conversion/expected-tiling-id-systems-name)
      (update-in-each [:Platforms] char-data-type-normalization/normalize-platform-characteristics-data-type)
      (update :DOI iso-shared/expected-doi)
      (update :UseConstraints iso-shared/expected-use-constraints)
      (update :ArchiveAndDistributionInformation iso-shared/expected-archive-dist-info)
      (update-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :Description] iso-util/safe-trim)
      (update-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :GeodeticModel] expected-geodetic-model)
      (update-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :LocalCoordinateSystem] expected-local-coordinate-system)
      (update-horizontal-data-resolutions)
      js/parse-umm-c))
