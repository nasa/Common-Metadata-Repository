(ns cmr.umm-spec.test.iso-shared
  "ISO 19115 specific expected conversion functionality"
  (:require
    [clojure.string :as string]
    [cmr.common.util :as util :refer [update-in-each]]
    [cmr.umm-spec.iso19115-2-util :as iso-util]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as cmn]
    [cmr.umm-spec.util :as su]
    [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact :as xml-to-umm-data-contact]
    [cmr.umm-spec.xml-to-umm-mappings.iso-shared.iso-topic-categories :as iso-topic-categories])
  (:use
   [cmr.umm-spec.models.umm-collection-models]
   [cmr.umm-spec.models.umm-common-models]))

(defn expected-use-constraints
  "Returns the expected UseConstraints."
  [use-constraints]
  (if-let [linkage (get-in use-constraints [:LicenseURL :Linkage])]
    (assoc use-constraints :LicenseURL (cmn/map->OnlineResourceType {:Linkage linkage}))
    use-constraints))

(defn- create-contact-person
  "Creates a contact person given the info of a creator, editor and publisher"
  [person]
  (when person
    (let [person-names (xml-to-umm-data-contact/parse-individual-name person nil)]
      (cmn/map->ContactPersonType
        {:Roles ["Technical Contact"]
         :FirstName (:FirstName person-names)
         :MiddleName (:MiddleName person-names)
         :LastName (:LastName person-names)}))))

(defn update-contact-persons-from-collection-citation
  "CollectionCitation introduces additional contact persons from creator, editor and publisher.
   They need to be added to the ContactPersons in the expected umm after converting the umm
   to the xml and back to umm.  Returns the updated ContactPersons."
  [contact-persons collection-citation]
  (let [{:keys [Creator Editor Publisher]} collection-citation
        creator-contact-person (create-contact-person Creator)
        editor-contact-person (when Editor
                                (assoc (create-contact-person Editor) :NonDataCenterAffiliation "editor"))
        publisher-contact-person (create-contact-person Publisher)]
    (remove nil?  (conj contact-persons
                        (util/remove-nil-keys creator-contact-person)
                        (util/remove-nil-keys editor-contact-person)
                        (util/remove-nil-keys publisher-contact-person)))))

(defn trim-collection-citation
  "Returns CollectionCitation with Creator, Editor, Publisher and ReleasePlace fields trimmed."
  [collection-citation]
  (let [{:keys [Creator Editor Publisher ReleasePlace]} collection-citation]
    (util/remove-nil-keys
      (assoc collection-citation
             :Creator (when Creator (string/trim Creator))
             :Editor (when Editor (string/trim Editor))
             :Publisher (when Publisher (string/trim Publisher))
             :ReleasePlace (when ReleasePlace (string/trim ReleasePlace))))))

(defn split-temporals
  "Returns a seq of temporal extents with a new extent for each value under key
  k (e.g. :RangeDateTimes) in each source temporal extent."
  [k temporal-extents]
  (reduce (fn [result extent]
            (if-let [values (get extent k)]
              (concat result (map #(assoc extent k [%])
                                  values))
              (conj (vec result) extent)))
          []
          temporal-extents))

(defn sort-by-date-type-iso
  "Returns temporal extent records to match the order in which they are generated in ISO XML."
  [extents]
  (let [ranges (filter :RangeDateTimes extents)
        singles (filter :SingleDateTimes extents)]
    (seq (concat ranges singles))))

(defn fixup-iso-ends-at-present
  "Updates temporal extents to be true only when they have both :EndsAtPresentFlag = true AND values
  in RangeDateTimes, otherwise nil."
  [temporal-extents]
  (for [extent temporal-extents]
    (let [ends-at-present (:EndsAtPresentFlag extent)
          rdts (seq (:RangeDateTimes extent))]
      (-> extent
          (update-in-each [:RangeDateTimes]
                          update-in [:EndingDateTime] (fn [x]
                                                        (when-not ends-at-present
                                                          x)))
          (assoc :EndsAtPresentFlag
                 (boolean (and rdts ends-at-present)))))))

(defn expected-iso-topic-categories
  "Update ISOTopicCategories values to a default value if it's not one of the specified values."
  [categories]
  (->> categories
       (map iso-topic-categories/umm->xml-iso-topic-category-map)
       (map iso-topic-categories/xml->umm-iso-topic-category-map)
       (remove nil?)
       seq))

(defn expected-doi
  "Returns the expected DOI."
  [doi]
  (let [explanation (when (:Explanation doi)
                      (string/trim (:Explanation doi)))
        updated-doi (util/remove-nil-keys (assoc doi :Explanation explanation))]
    (cmn/map->DoiType
      (if (or (:DOI updated-doi)
              (:MissingReason updated-doi))
        updated-doi
        {:MissingReason "Unknown"
         :Explanation "It is unknown if this record has a DOI."}))))

(defn- expected-dist-media
  "Creates expected Media for FileDistributionInformation."
  [media]
  (when-let [media (first media)]
    [media]))

(defn- expected-file-archive-description
  "Creates expected Description for FileArchiveInformation"
  [description]
  (when description
    (string/trim description)))

(defn- expected-file-dist-info
  "Created expected FileDistributionInformation for ArchiveAndDistributionInformation map."
  [file-dist-infos]
  (when file-dist-infos
    (for [file-dist-info file-dist-infos]
      (-> file-dist-info
          (dissoc :TotalCollectionFileSizeBeginDate)
          (update :Media expected-dist-media)
          (update :FormatDescription expected-file-archive-description)
          umm-c/map->FileDistributionInformationType))))

(defn- expected-file-archive-info
  "Created expected FileArchiveInformation for ArchiveAndDistributionInformation map."
  [file-archive-infos]
  (when file-archive-infos
    (for [file-archive-info file-archive-infos]
      (-> file-archive-info
          (dissoc :TotalCollectionFileSizeBeginDate)
          (update :Description expected-file-archive-description)
          (update :FormatDescription expected-file-archive-description)
          umm-c/map->FileArchiveInformationType))))

(defn expected-archive-dist-info
  "Creates expected ArchiveAndDistributionInformation for dif10."
  [archive-dist-info]
  (some-> archive-dist-info
      (update :FileDistributionInformation expected-file-dist-info)
      (update :FileArchiveInformation expected-file-archive-info)
      umm-c/map->ArchiveAndDistributionInformationType))

(defn expected-related-url-get-data
  "Returns related-url with the expected GetData"
  [related-url]
  (if (and (= "DistributionURL" (:URLContentType related-url))
           (or (= "GET DATA" (:Type related-url))
               (= "GET CAPABILITIES" (:Type related-url))))
    (if (nil? (:GetData related-url))
      (assoc related-url :GetData (cmn/map->GetDataType
                                   {:Format su/not-provided
                                    :Size 0.0
                                    :Unit "KB"}))
      related-url)
    related-url))

(defn expected-related-url-get-service
  "Returns related-url with the expected GetService"
  [related-url]
  (let [URI (if (empty? (get-in related-url [:GetService :URI]))
              [(:URL related-url)]
              (get-in related-url [:GetService :URI]))]
      (if (and (= "DistributionURL" (:URLContentType related-url))
               (= "USE SERVICE API" (:Type related-url)))
          (if (nil? (:GetService related-url))
            (assoc related-url :GetService (cmn/map->GetServiceType
                                              {:MimeType su/not-provided
                                               :Protocol su/not-provided
                                               :FullName su/not-provided
                                               :DataID su/not-provided
                                               :DataType su/not-provided
                                               :URI URI}))
            (assoc-in related-url [:GetService :URI] URI))
          (dissoc related-url :GetService))))
