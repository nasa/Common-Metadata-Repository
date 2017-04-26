(ns cmr.umm-spec.test.iso-smap-expected-conversion
 "ISO SMAP specific expected conversion functionality"
 (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.string :as str]
   [cmr.common.util :as util :refer [update-in-each]]
   [cmr.umm-spec.date-util :as du]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.models.umm-common-models :as cmn]
   [cmr.umm-spec.related-url :as ru-gen]
   [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.data-contact :as data-contact]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

(defn- expected-iso-smap-related-urls
  "The expected RelatedUrl value when converting from umm-C to iso-smap
   and back to umm-C"
  [related-urls]
  (seq (for [related-url related-urls]
         (-> related-url
             (update :URL #(url/format-url % true))))))

(defn- expected-collection-related-urls
  "Update the collection top level RelatedUrls. Do processing not applicable
  for data center/data contact RelatedUrls. DataCenter and DataContact URL
  types are not applicable here, so remove."
  [related-urls]
  (let [related-urls (expected-iso-smap-related-urls related-urls)]
    (seq (for [related-url
                (remove #(#{"DataCenterURL" "DataContactURL"} (:URLContentType %))
                        related-urls)]
           (-> related-url
               (update :Description #(when % (str/trim %))))))))

(defn- normalize-smap-instruments
  "Collects all instruments across given platforms and returns a seq of platforms with all
  instruments under each one."
  [platforms]
  (let [all-instruments (seq (mapcat :Instruments platforms))]
    (for [platform platforms]
      (assoc platform :Instruments all-instruments))))

(defn- expected-smap-iso-spatial-extent
  "Returns the expected SMAP ISO spatial extent"
  [spatial-extent]
  (if (get-in spatial-extent [:HorizontalSpatialDomain :Geometry :BoundingRectangles])
    (-> spatial-extent
        (assoc :SpatialCoverageType "HORIZONTAL" :GranuleSpatialRepresentation "GEODETIC")
        (assoc :VerticalSpatialDomains nil :OrbitParameters nil)
        (assoc-in [:HorizontalSpatialDomain :ZoneIdentifier] nil)
        (update-in [:HorizontalSpatialDomain :Geometry]
                   assoc :CoordinateSystem "GEODETIC" :Points nil :GPolygons nil :Lines nil)
        conversion-util/prune-empty-maps)
    (cmn/map->SpatialExtentType su/not-provided-spatial-extent)))

(defn- expected-smap-data-dates
  "Returns the expected ISO SMAP DataDates."
  [data-dates]
  (if data-dates
    data-dates
    [(cmn/map->DateType {:Type "CREATE" :Date du/parsed-default-date})]))

(defn- expected-science-keywords
  "Returns the expected science keywords, default if none. ISO-SMAP checks on the Category of
  theme descriptive keywords to determine if it is a science keyword."
  [science-keywords]
  (if-let [science-keywords
           (seq (filter #(.contains kws/science-keyword-categories (:Category %)) science-keywords))]
    science-keywords
    su/not-provided-science-keywords))

(defn- distributor?
  ""
  [roles]
  (if (some #(= "DISTRIBUTOR" %) roles)
    true
    false))

(defn- archiver?
  ""
  [roles]
  (if (some #(= "ARCHIVER" %) roles)
    true
    false))

(defn- add-archiver
  ""
  [roles]
  (let [dist-index (.indexOf roles "DISTRIBUTOR")
        dist-subvec (subvec roles 0 (inc dist-index))
        rest-subvec (subvec roles (inc dist-index))]
    (concat dist-subvec ["ARCHIVER"] rest-subvec)))

(defn- add-distributor
  ""
  [roles]
  (let [arc-index (.indexOf roles "ARCHIVER")
        arc-subvec (subvec roles arc-index)
        rest-subvec (subvec roles 0 arc-index)]
    (concat rest-subvec ["DISTRIBUTOR"] arc-subvec)))

(defn- fix-archiver-distributor
  ""
  [roles]
  (distinct
   (cond
     (and (archiver? roles)
          (distributor? roles))
     (-> (remove #(= % "ARCHIVER") roles)
         vec
         add-archiver)
     (archiver? roles)
     (add-distributor roles)
     (distributor? roles)
     (add-archiver roles)
     :default roles)))

(defn- expected-data-center-roles
  ""
  [data-center]
  (let [roles (-> (:Roles data-center)
                  fix-archiver-distributor
                  distinct)]
    (assoc data-center :Roles roles)))

(defn- order-data-centers
  ""
  [data-centers]
  (let [grouped-data-centers (group-by #(= ["PROCESSOR"] (:Roles %)) data-centers)]
    (concat (get grouped-data-centers false) (get grouped-data-centers true))))

(defn- update-short-and-long-name
  "ISO only has 1 field for both short and long and they get combined with a delimeter. combined
  and then parse the short and long name here to mimic what UMM -> ISO -> UMM will do."
  [data-center]
  (let [{:keys [ShortName LongName]} data-center
        organization-name (if LongName
                           (str ShortName " &gt; " LongName)
                           ShortName)
        name-split (str/split organization-name #"&gt;|>")]
    (if (> (count name-split) 0)
     (-> data-center
         (assoc :ShortName (str/trim (first name-split)))
         (assoc :LongName (when (> (count name-split) 1)
                           (str/join " " (map str/trim (rest name-split))))))
     (-> data-center
         (assoc :ShortName su/not-provided)
         (assoc :LongName nil)))))

(defn- update-person-names
  "ISO only has one field for the whole name. When we go from UMM -> ISO, we combine the names into
  one field then on ISO -> UMM we split them up. Need to do this processing to handle spaces in names
  as well as leading/trailing spaces."
  [person]
  (let [{:keys [FirstName MiddleName LastName]} person
        combined-name (str/trim (str/join " " [FirstName MiddleName LastName]))
        names (str/split combined-name #" {1,}")
        num-names (count names)]
   (if (= 1 num-names)
     (-> person
         (assoc :LastName (first names))
         (dissoc :FirstName)
         (dissoc :MiddleName))
     (-> person
         (assoc :FirstName (first names))
         (assoc :MiddleName (str/join " " (subvec names 1 (dec num-names))))
         (update :MiddleName #(when (seq %) %)) ; nil if empty
         (assoc :LastName (last names))))))

(defn- expected-iso-contact-mechanisms
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

(defn- expected-contact-info-related-urls
  "Returns expected related url - take the first related url and the first url in related urls"
  [related-urls]
  (when related-urls
    (cmr.umm-spec.test.iso19115-expected-conversion/expected-iso-19115-2-related-urls (take 1 related-urls))))

(defn- expected-iso-contact-information
  "Returns expected contact information - 1 address, only certain contact mechanisms are mapped"
  [contact-info url-content-type]
  (let [contact-info (-> contact-info
                         (update :RelatedUrls expected-contact-info-related-urls)
                         (update-in-each [:RelatedUrls] #(assoc % :URLContentType url-content-type))
                         (update-in-each [:RelatedUrls] #(assoc % :Type "HOME PAGE"))
                         (update :ContactMechanisms expected-iso-contact-mechanisms)
                         ; (update-in-each [:RelatedUrls] #(dissoc % :Subtype :GetData :GetService))
                         (update :Addresses #(seq (take 1 %))))]
    (if (empty? (cmr.common.util/remove-nil-keys contact-info))
      nil
      contact-info)))

(defn- expected-contact-person
  ""
  [contact-person roles]
  (let [current-roles (:Roles contact-person)
        contact-person (assoc contact-person :Uuid nil)
        contact-person (update-person-names contact-person)
        contact-person (update contact-person :ContactInformation expected-iso-contact-information "DataContactURL")
        contact-person (if (some #(= "Metadata Author" %) current-roles)
                         (assoc contact-person :Roles ["Metadata Author"])
                         (assoc contact-person :Roles [roles]))]
    (cmn/map->ContactPersonType contact-person)))

(defn- expected-contact-persons
  ""
  [contact-persons roles]
  (seq
   (distinct
    (map #(expected-contact-person % roles)
         (for [contact-person contact-persons
               role (:Roles contact-person)]
           (assoc contact-person :Roles [role]))))))

(defn- expected-data-center-persons
  ""
  [data-center]
  (-> data-center
      (update-in [:ContactPersons] expected-contact-persons "Data Center Contact")
      (update-in-each [:ContactPersons] update-person-names)
      (update-in-each [:ContactPersons] assoc :Uuid nil)
      (update-in-each [:ContactPersons] cmn/map->ContactPersonType)))

(defn- expected-data-center-contact-information
  ""
  [data-center]
  (let [contact-info (get data-center :ContactInformation)
        contact-mechanisms (expected-iso-contact-mechanisms (get contact-info :ContactMechanisms))
        contact-info (assoc contact-info :ContactMechanisms contact-mechanisms)
        contact-info (expected-iso-contact-information contact-info "DataCenterURL")]
    (if (empty? (util/remove-nil-keys contact-info))
      (assoc data-center :ContactInformation nil)
      (assoc data-center :ContactInformation contact-info))))

(defn- split-data-centers-by-roles
  ""
  [data-centers]
  (for [data-center data-centers
        role (:Roles data-center)]
    (assoc data-center :Roles [role])))

(defn- expected-data-centers
  ""
  [data-centers]
  (let [data-centers (split-data-centers-by-roles data-centers)]
    (->> data-centers
         (map update-short-and-long-name)
         (map expected-data-center-roles)
         (map #(assoc % :ContactGroups nil))
         (map #(assoc % :Uuid nil))
         (map expected-data-center-contact-information)
         (map expected-data-center-persons)
         order-data-centers
         (map cmn/map->DataCenterType)
         distinct)))

(defn umm-expected-conversion-iso-smap
  [umm-coll original-brs]
  (proto-repl.saved-values/save 2)
  (-> umm-coll
        (assoc :DirectoryNames nil)
        (update-in [:SpatialExtent] expected-smap-iso-spatial-extent)
        (update-in [:DataDates] expected-smap-data-dates)
        ;; ISO SMAP does not support the PrecisionOfSeconds field.
        (update-in-each [:TemporalExtents] assoc :PrecisionOfSeconds nil)
        ;; Implement this as part of CMR-2057
        (update-in-each [:TemporalExtents] assoc :TemporalRangeType nil)
        (assoc :MetadataAssociations nil) ;; Not supported for ISO SMAP
        (update :DataCenters expected-data-centers)
        (assoc :VersionDescription nil)
        (assoc :ContactGroups nil)
        (update :ContactPersons expected-contact-persons "Technical Contact")
        (assoc :UseConstraints nil)
        (assoc :AccessConstraints nil)
        (assoc :SpatialKeywords nil)
        (assoc :TemporalKeywords nil)
        (assoc :CollectionDataType nil)
        (assoc :AdditionalAttributes nil)
        (assoc :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id su/not-provided}))
        (assoc :Distributions nil)
        (assoc :Projects nil)
        (assoc :PublicationReferences nil)
        (assoc :AncillaryKeywords nil)
        (update :RelatedUrls expected-collection-related-urls)
        (assoc :ISOTopicCategories nil)
        (update :ScienceKeywords expected-science-keywords)
        (assoc :PaleoTemporalCoverages nil)
        (assoc :MetadataDates nil)
        (update :CollectionProgress su/with-default)))
