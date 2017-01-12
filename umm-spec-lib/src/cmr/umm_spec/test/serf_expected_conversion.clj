(ns cmr.umm-spec.test.serf-expected-conversion
  "SERF specific expected conversion functionality"
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [cmr.common.util :as util :refer [update-in-each]]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.models.umm-common-models :as cmn]
   [cmr.umm-spec.related-url :as ru-gen]
   [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

(def serf-organization-role
  "UMM-S Role that corresponds to SERVICE PROVIDER CONTACT role in SERF"
  "RESOURCEPROVIDER")

(defn- default-serf-required-additional-attributes
  "Populate a default not-provided value for additional attributes if none exist"
  [aas attribute-name]
  (if (seq (filter #(= attribute-name (:Name %)) aas))
    aas
    (conj aas (cmn/map->AdditionalAttributeType {:Name attribute-name
                                                 :Description (format "Root SERF %s Object" attribute-name)
                                                 :Value su/not-provided}))))

(defn- default-serf-additional-attributes
  "Modifies attributes in serf from expected-conversion"
  [aa]
  (-> aa
      (select-keys [:Description :Name :Value :Group :UpdateDate :DataType :Value])
      (assoc :Name (get aa :Name su/not-provided))
      (cmn/map->AdditionalAttributeType)))

(defn- fix-serf-aa-update-date-format
  "Fixes SERF update-date format to conform to a specific rule"
  [aa]
  (if-let [u-date (:UpdateDate aa)]
    (assoc aa :UpdateDate (t/date-time (t/year u-date) (t/month u-date) (t/day u-date)))
    aa))

(defn- fix-expected-serf-additional-attributes
  "Check and see if Metadata_Name and Metadata_Version are in serf additional attributes.
  If not, you need to inject them so that a comparison will work"
  [aas]
  (-> aas
      (default-serf-required-additional-attributes "Metadata_Name")
      (default-serf-required-additional-attributes "Metadata_Version")))

(defn- convert-serf-additional-attributes
  [additional-attributes]
  (fix-expected-serf-additional-attributes
    (vec
      (for [attribute additional-attributes]
        (-> attribute
            default-serf-additional-attributes
            fix-serf-aa-update-date-format)))))

(defn- filter-unused-serf-datetypes
  [dates]
  (remove #(= "DELETE" (:Type %)) dates))

(defn- filter-unique-serf-dates
  [dates]
  (let [dates-by-type (group-by :Type dates)]
    (keep #(first (get dates-by-type %))
          ["CREATE" "UPDATE" "REVIEW"])))


(defn- expected-metadata-dates-for-serf
  [dates]
  (-> dates
      filter-unused-serf-datetypes
      filter-unique-serf-dates
      seq))

(defn- fix-publication-reference-url
  [some-url]
  (when some-url
    (cmn/map->RelatedUrlType {:URLs (->> some-url
                                          :URLs
                                          (take 1)
                                          (map #(url/format-url % true)))})))

(defn- expected-serf-service-citation
  [citation]
  (assoc citation
         :DOI nil
         :ReleasePlace nil
         :SeriesName nil
         :DataPresentationForm nil
         :IssueIdentification nil
         :Editor nil
         :ReleaseDate nil
         :OtherCitationDetails nil
         :RelatedUrl (fix-publication-reference-url (:RelatedUrl citation))))

(defn- remove-empty-objects
  "Required to remove some extraneous mappings from ResourceCitation that are not used
  in ServiceCitation for the comparison engine."
  [objects]
  (filter #(some val %) objects))

(defn- fix-serf-doi
  [pubref]
  (if (:DOI pubref)
    (assoc-in pubref [:DOI :Authority] nil)
    pubref))

(defn- expected-online-resource
  "Sanitize the linkage in OnlineResource"
  [online-resource]
  (when-let [url (:Linkage online-resource)]
    (cmn/map->OnlineResourceType {:Linkage url})))

(defn- expected-publication-reference
 "Fix the DOI and Online Resouce linkage in Publication Reference"
 [pubref]
 (-> pubref
     fix-serf-doi
     (update :OnlineResource expected-online-resource)))

(defn- fix-access-constraints
  [access-constraint]
  (if access-constraint
    (assoc access-constraint :Value nil)
    access-constraint))

(defn- fix-serf-project
  [project]
  (assoc project :EndDate nil :StartDate nil :Campaigns nil))

(defn- fix-metadata-associations
  [metadata-association]
  (if-let [ma (seq (take 1 metadata-association))]
    ma
    metadata-association))

(defn umm-expected-conversion-serf
  [umm-service]
  (-> umm-service
      (update-in [:AdditionalAttributes] convert-serf-additional-attributes)
      (update :RelatedUrls conversion-util/expected-related-urls-for-dif-serf)
      (update-in [:MetadataDates] expected-metadata-dates-for-serf)
      (update-in-each [:ServiceCitation] expected-serf-service-citation)
      (update-in [:ServiceCitation] remove-empty-objects)
      (update-in [:ServiceCitation] seq)
      (update-in-each [:Projects] fix-serf-project)
      (update-in [:AccessConstraints] fix-access-constraints)
      (update-in-each [:MetadataAssociations] assoc :Description nil :Type nil :Version nil)
      (update-in [:MetadataAssociations] fix-metadata-associations)
      (update-in-each [:PublicationReferences] expected-publication-reference)
      (assoc :Platforms nil)
      (dissoc :DataCenters)
      (update-in-each [:PublicationReferences] #(update % :ISBN su/format-isbn))
      (assoc :CollectionProgress su/not-provided)))
