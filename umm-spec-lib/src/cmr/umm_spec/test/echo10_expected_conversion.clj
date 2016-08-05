(ns cmr.umm-spec.test.echo10-expected-conversion
 "ECHO 10 specific expected conversion functionality"
 (:require [clj-time.core :as t]
           [clj-time.format :as f]
           [clojure.string :as str]
           [cmr.umm-spec.util :as su]
           [cmr.common.util :as util :refer [update-in-each]]
           [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
           [cmr.umm-spec.related-url :as ru-gen]
           [cmr.umm-spec.location-keywords :as lk]
           [cmr.umm-spec.test.location-keywords-helper :as lkt]
           [cmr.umm-spec.models.collection :as umm-c]
           [cmr.umm-spec.umm-to-xml-mappings.echo10.data-contact :as dc]))


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

(defn- expected-echo10-related-urls
  [related-urls]
  (seq (for [related-url related-urls
             :let [[rel] (:Relation related-url)]
             url (:URLs related-url)]
         (-> related-url
             (assoc :Title nil :URLs [url])
             (update-in [:FileSize] (fn [file-size]
                                      (when (and file-size
                                                 (= rel "GET RELATED VISUALIZATION"))
                                        (when-let [byte-size (ru-gen/convert-to-bytes
                                                               (:Size file-size) (:Unit file-size))]
                                          (assoc file-size :Size (/ (int byte-size) 1024) :Unit "KB")))))
             (update-in [:Relation] (fn [[rel]]
                                      (when (conversion-util/relation-set rel)
                                        [rel])))))))

(defn- expected-echo10-spatial-extent
  "Returns the expected ECHO10 SpatialExtent for comparison with the umm model."
  [spatial-extent]
  (let [spatial-extent (conversion-util/prune-empty-maps spatial-extent)]
    (if (get-in spatial-extent [:HorizontalSpatialDomain :Geometry])
      (update-in spatial-extent
                 [:HorizontalSpatialDomain :Geometry]
                 conversion-util/geometry-with-coordinate-system)
      spatial-extent)))

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
      (assoc-in [:StreetAddresses] [(dc/join-street-addresses (:StreetAddresses address))])
      (update-in [:City] su/with-default)
      (update-in [:StateProvince] su/with-default)
      (update-in [:PostalCode] su/with-default)
      (update-in [:Country] dc/country-with-default)))

(defn- expected-echo10-contact-information
  "Expected contact information"
  [contact-information]
  (when (and contact-information
             (or (:ServiceHours contact-information)
                 (:ContactMechanisms contact-information)
                 (:ContactInstruction contact-information)
                 (:Addresses contact-information)))
   (-> contact-information
       (assoc :RelatedUrls nil)
       (update-in [:Addresses] #(when (seq %)
                                  (mapv expected-echo10-address %)))
       (update-in [:ContactMechanisms] expected-contact-mechanisms))))


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
         (update-in [:FirstName] su/with-default)
         (update-in [:LastName] su/with-default)
         (assoc-in [:Roles] [role])))))

(defn- expected-echo10-contact-persons
  "Returns the list of expected contact persons"
  [contact-persons]
  (when (seq contact-persons)
    (flatten (mapv expected-echo10-contact-person contact-persons))))

(defn- expected-echo10-data-center
  "Returns an expected data center for each role. ECHO10 only allows for 1 role per
  data center, so when converted to UMM a data center is created for each role with
  the rest of the info copied."
  [data-center]
  (for [role (:Roles data-center)]
    (-> data-center
        (assoc :ContactGroups nil)
        (update-in [:ContactPersons] expected-echo10-contact-persons)
        (assoc :Uuid nil)
        (assoc :LongName nil)
        (assoc :Roles [role])
        (assoc-in [:ContactInformation] (expected-echo10-contact-information (:ContactInformation data-center))))))

(defn- expected-echo10-data-centers
  "Returns the list of expected data centers"
  [data-centers]
  (if (seq data-centers)
    (flatten (mapv expected-echo10-data-center data-centers))
    [su/not-provided-data-center]))

(defn umm-expected-conversion-echo10
  [umm-coll]
  (-> umm-coll
      (update-in [:TemporalExtents] (comp seq (partial take 1)))
      (update-in [:DataDates] fixup-echo10-data-dates)
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
      (update-in [:RelatedUrls] expected-echo10-related-urls)
      ;; We can't restore Detailed Location because it doesn't exist in the hierarchy.
      (update-in [:LocationKeywords] conversion-util/fix-location-keyword-conversion)
      ;; CMR 2716 Getting rid of SpatialKeywords but keeping them for legacy purposes.
      (assoc :SpatialKeywords nil)
      (assoc :PaleoTemporalCoverages nil)))
