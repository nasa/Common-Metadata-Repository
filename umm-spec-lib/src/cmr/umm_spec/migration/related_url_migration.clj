(ns cmr.umm-spec.migration.related-url-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require
   [cmr.common.util :refer [update-in-each remove-nil-keys]]
   [cmr.umm-spec.util :as util]
   [cmr.umm-spec.migration.related-url-migration-maps :as ru-maps]))

(defn- migrate-to-get-data
  "migrate from 1.8 to 1.9 GetData"
  [related-url]
  (let [size (get-in related-url [:FileSize :Size])
        unit (get-in related-url [:FileSize :Unit])]
    (if (or size unit)
      (-> related-url
          (dissoc related-url :MimeType :FileSize)
          (assoc-in [:GetData :Size] size)
          (assoc-in [:GetData :Unit] unit)
          (assoc-in [:GetData :Format] util/not-provided))
      (-> related-url
          (dissoc related-url :MimeType :FileSize)))))

(defn- migrate-to-get-service
  "migrate from 1.8 to 1.9 GetService"
  [related-url]
  (if-let [mime-type (get related-url :MimeType)]
    (-> related-url
        (dissoc related-url :MimeType :FileSize)
        (assoc-in [:GetService :MimeType] mime-type)
        (assoc-in [:GetService :FullName] util/not-provided)
        (assoc-in [:GetService :DataID] util/not-provided)
        (assoc-in [:GetService :DataType] util/not-provided)
        (assoc-in [:GetService :Protocol] util/not-provided))
    (-> related-url
        (dissoc related-url :MimeType :FileSize))))

(defn- migrate-from-get-data
  "migrate from 1.9 to 1.8 GetData"
  [related-url]
  (let [size (get-in related-url [:GetData :Size])
        unit (get-in related-url [:GetData :Unit])]
    (if (and size unit)
      (-> related-url
          (assoc-in [:FileSize :Size] size)
          (assoc-in [:FileSize :Unit] unit)
          (dissoc :GetData :GetService))
      (-> related-url
          (dissoc :GetData :GetService)))))

(defn- migrate-from-get-service
  "migrate from 1.9 to 1.8 GetService"
  [related-url]
  (let [mime-type (get-in related-url [:GetService :MimeType])]
    (-> related-url
        (assoc :MimeType mime-type)
        (dissoc :GetService :GetData))))

(defn migrate-url-content-types-up
  "migrate from 1.9 to 1.8 based on URLContentType and Type"
  [related-url]
  (let [url-content-type (get related-url :URLContentType)
        type (get related-url :Type)]
    (if (= "DistributionURL" url-content-type)
      (case type
        "GET DATA" (migrate-to-get-data related-url)
        "GET SERVICE" (migrate-to-get-service related-url))
      (dissoc related-url :MimeType :FileSize))))

(defn migrate-url-content-types-down
  "migrate from 1.9 to 1.8 based on URLContentType and Type"
  [related-url]
  (let [url-content-type (get related-url :URLContentType)
        type (get related-url :Type)]
    (if (= "DistributionURL" url-content-type)
      (case type
        "GET DATA" (migrate-from-get-data related-url)
        "GET SERVICE" (migrate-from-get-service related-url))
      related-url)))

(defn- url-content-type->relation
 "Convert the Type and Subtype of the URLContentType to Relation"
 [related-url]
 (let [{:keys [Type Subtype]} related-url
       relation (if Subtype
                 [Type Subtype]
                 [Type])]
  (-> related-url
      (assoc :Relation relation)
      (dissoc :URLContentType :Type :Subtype :GetData :GetService))))

(defn migrate-online-resource-down
  "Migrate online-resource from version 1.10 to 1.9.
   Need to remove MimeType, add default for Name and Description if they don't exist."
  [element]
  (if-let [ol-resource (:OnlineResource element)]
    (assoc element :OnlineResource {:Linkage (:Linkage ol-resource)
                                    :Name (util/with-default (:Name ol-resource) true)
                                    :Description (util/with-default (:Description ol-resource) true)})
    element))

(defn- remove-default-name-description
  "Remove default Name and Description from online-resource."
  [online-resource]
  (apply dissoc online-resource (for [[k v] (select-keys online-resource [:Name :Description])
                                      :when (= util/not-provided v)]
                                  k)))

(defn migrate-online-resource-up
  "Migrate online-resource from version 1.9 to 1.10.
   Need to remove Name and Description if their values are util/not-provided
   because they are artificially added when migrating from 1.10 to 1.9."
  [element]
  (if-let [ol-resource (:OnlineResource element)]
    (assoc element :OnlineResource (remove-default-name-description ol-resource))
    element))

(defn migrate-related-url-to-online-resource
  "Migrate the RelatedUrl in the element to Online Resource.
  Applies to RelatedUrls in UMM spec v1.8 and lower."
  [element]
  (if-let [related-url (:RelatedUrl element)]
    (-> element
        (assoc :OnlineResource {:Linkage (first (:URLs related-url))
                                :Name (util/with-default (:Title related-url) true)
                                :Description (util/with-default (:Description related-url) true)})
        (dissoc :RelatedUrl))
    element))

(defn migrate-online-resource-to-related-url
  "Migrate the OnlineResource in the element to RelatedUrl.
  Complies with UMM spec v1.8 and lower."
  [element]
  (if-let [online-resource (:OnlineResource element)]
    (-> element
        (assoc :RelatedUrl {:URLs [(:Linkage online-resource)]
                            :Title (:Name online-resource)
                            :Description (:Description online-resource)
                            :Relation ["VIEW RELATED INFORMATION" "Citation"]
                            :MimeType "text/html"})
        (dissoc :OnlineResource))
    element))

(def valid-url-content-types-map-version-to-1-9
  {"DistributionURL" {"GET DATA" ["DATACAST URL"
                                  "EARTHDATA SEARCH"
                                  "ECHO"
                                  "EDG"
                                  "EOSDIS DATA POOL"
                                  "GDS"
                                  "GIOVANNI"
                                  "KML"
                                  "LAADS"
                                  "LANCE"
                                  "LAS"
                                  "MIRADOR"
                                  "MODAPS"
                                  "NOAA CLASS"
                                  "ON-LINE ARCHIVE"
                                  "REVERB"]
                      "GET SERVICE" ["ACCESS MAP VIEWER"
                                     "ACCESS MOBILE APP"
                                     "ACCESS WEB SERVICE"
                                     "DIF"
                                     "MAP SERVICE"
                                     "NOMADS"
                                     "OPENDAP DATA"
                                     "OPENDAP DATA (DODS)"
                                     "OPENDAP DIRECTORY (DODS)"
                                     "OpenSearch"
                                     "SERF"
                                     "SOFTWARE PACKAGE"
                                     "SSW"
                                     "SUBSETTER"
                                     "THREDDS CATALOG"
                                     "THREDDS DATA"
                                     "THREDDS DIRECTORY"
                                     "WEB COVERAGE SERVICE (WCS)"
                                     "WEB FEATURE SERVICE (WFS)"
                                     "WEB MAP FOR TIME SERIES"
                                     "WEB MAP SERVICE (WMS)"
                                     "WORKFLOW (SERVICE CHAIN)"]}
   "VisualizationURL" {"GET RELATED VISUALIZATION" ["GIBS" "GIOVANNI" "MAP"]}
   "CollectionURL" {"DATA SET LANDING PAGE" []
                    "DOI" []
                    "EXTENDED METADATA" []
                    "PROFESSIONAL HOME PAGE" []
                    "PROJECT HOME PAGE" []}
   "PublicationURL" {"VIEW RELATED INFORMATION" ["ALGORITHM THEORETICAL BASIS DOCUMENT"
                                                 "CALIBRATION DATA DOCUMENTATION"
                                                 "CASE STUDY"
                                                 "DATA QUALITY"
                                                 "DATA USAGE"
                                                 "DELIVERABLES CHECKLIST"
                                                 "GENERAL DOCUMENTATION"
                                                 "HOW-TO"
                                                 "PI DOCUMENTATION"
                                                 "PROCESSING HISTORY"
                                                 "PRODUCTION VERSION HISTORY"
                                                 "PRODUCT QUALITY ASSESSMENT"
                                                 "PRODUCT USAGE"
                                                 "PRODUCT HISTORY"
                                                 "PUBLICATIONS"
                                                 "RADIOMETRIC AND GEOMETRIC CALIBRATION METHODS"
                                                 "READ-ME"
                                                 "RECIPE"
                                                 "REQUIREMENTS AND DESIGN"
                                                 "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"
                                                 "SCIENCE DATA PRODUCT VALIDATION"
                                                 "USER FEEDBACK"
                                                 "USER'S GUIDE"]}
   "DataCenterURL" {"HOME PAGE" []}
   "DataContactURL" {"HOME PAGE" []}})

(defn valid-types-for-url-content-type
  "Returns all valid Types for URLContentType"
  [url-content-type]
  (keys (get valid-url-content-types-map-version-to-1-9 url-content-type)))

(defn type->url-content-type
  "Get the URLContentType from the type"
  [type]
  (first
    (for [url-content-type (keys valid-url-content-types-map-version-to-1-9)
          :when (some #(= type %) (valid-types-for-url-content-type url-content-type))]
      url-content-type)))

(defn relation->url-content-type
 "Get the URLContentType from relation or use default if a conversion is not
 possible"
 [related-url]
 (let [[type subtype] (:Relation related-url)
       url-content-type (type->url-content-type type)
       url-content-type (if url-content-type
                         {:URLContentType url-content-type
                          :Type type
                          :Subtype subtype}
                         util/default-url-type)
       related-url (dissoc related-url :Relation)]
   (merge related-url url-content-type)))

(defn url->array-of-urls
  "UMM spec versions 1.8 and lower's RelatedUrls contain an array of URLs.
  This function changes the :URL url of a RelatedUrl to :URLs [urls]"
  [related-urls]
  (mapv (fn [related-url]
         (if-let [url (:URL related-url)]
          (-> related-url
           (assoc :URLs [url])
           (dissoc :URL))
          related-url))
        related-urls))

(defn array-of-urls->url
  "UMM spec version 1.9's RelatedUrls contain a single URL.
  This function changes the :URLs [url] of a RelatedUrl to :URL url"
  [related-urls]
  (for [related-url related-urls
        url (:URLs related-url)]
   (-> related-url
       (assoc :URL url)
       (dissoc :URLs :Title))))

(defn dissoc-titles-from-contact-information
  "Remove :Title from a given vector of ContactInformation
  to comply with UMM spec v1.9"
  [contact-information]
  (if-let [related-urls (:RelatedUrls contact-information)]
   (update-in-each contact-information [:RelatedUrls] #(dissoc % :Title))
   contact-information))

(defn migrate-contacts-up
  "Migrate ContactPersons to comply with UMM spec v1.9"
  [contacts]
  (mapv (fn [contact]
         (if (seq (:ContactInformation contact))
          (-> contact
           (update-in [:ContactInformation :RelatedUrls] array-of-urls->url)
           (update-in-each [:ContactInformation :RelatedUrls]
             (fn [related-url]
               (-> related-url
                   (assoc :URLContentType "DataContactURL")
                   (assoc :Type "HOME PAGE")
                   (dissoc :Relation :GetData :GetService :FileSize :MimeType)))))
          contact))
        contacts))

(defn migrate-contacts-down
  "Migrate ContactPersons to UMM spec v1.8 and lower"
  [contacts]
  (for [contact contacts]
   (if (seq (:ContactInformation contact))
    (-> contact
        (update-in [:ContactInformation :RelatedUrls] url->array-of-urls)
        (update-in-each [:ContactInformation :RelatedUrls]
          #(dissoc % :URLContentType :Type :Subtype)))
    contact)))

(defn migrate-data-centers-up
  "Get RelatedUrls from DataCenters and dissoc the Title from any RelatedUrl"
  [data-centers]
  (mapv (fn [data-center]
          (-> data-center
              (update :ContactGroups migrate-contacts-up)
              (update :ContactPersons migrate-contacts-up)
              (update :ContactInformation dissoc-titles-from-contact-information)
              (update-in [:ContactInformation :RelatedUrls] array-of-urls->url)
              (update-in-each [:ContactInformation :RelatedUrls]
                (fn [related-url]
                  (-> related-url
                      (assoc :URLContentType "DataCenterURL")
                      (assoc :Type "HOME PAGE")
                      (dissoc :Relation :GetData :GetService :FileSize :MimeType))))))
        data-centers))

(defn migrate-data-centers-down
  ":RelatedUrl {:URL url} -> :RelatedUrl {:URLs [url]} for a given Data Center.
   Complies with UMM spec v1.8 and lower"
  [data-centers]
  (if (not-empty data-centers)
   (mapv (fn [data-center]
           (-> data-center
               (update :ContactGroups migrate-contacts-down)
               (update :ContactPersons migrate-contacts-down)
               (update-in [:ContactInformation :RelatedUrls] url->array-of-urls)
               (update-in-each [:ContactInformation :RelatedUrls]
                 #(dissoc % :URLContentType :Type :Subtype))))
         data-centers)
   data-centers))

(defn migrate-down-from-1_9
  ":RelatedUrl {:URL url} -> :RelatedUrl {:URLs [url]} for a given collection"
  [collection]
  (-> collection
   (update :RelatedUrls url->array-of-urls)
   (update-in-each [:RelatedUrls] migrate-url-content-types-down)
   (update-in-each [:RelatedUrls] url-content-type->relation)
   (update :ContactGroups migrate-contacts-down)
   (update :ContactPersons migrate-contacts-down)
   (update :DataCenters migrate-data-centers-down)))

(defn replace-existing-related-url-keywords
  "Pass in a related url and UMM version 1.10 or 1.11. Replace the existing UMMM-C version 1.11/1.10
   with 1.10/1.11 keywords. If a bad version is given just use the original keywords."
  [related-url toVersion]
  (let [{:keys [URLContentType Type Subtype]} related-url
        keywords (remove-nil-keys {:URLContentType URLContentType
                                   :Type Type
                                   :Subtype Subtype})
        result (case toVersion
                     "1.10" (ru-maps/umm-1-11-umm-url-types->umm-1-10-umm-url-types keywords)
                     "1.11" (ru-maps/umm-1-10-umm-url-types->umm-1-11-umm-url-types keywords)
                     keywords)]
    (remove-nil-keys
     (assoc related-url :URLContentType (:URLContentType result)
                        :Type (:Type result)
                        :Subtype (:Subtype result)))))

(defn migrate-up-to-1_11
  ":RelatedUrl Type and Subtypes have changed in UMM-C version 1.11 to correspond
   to GCMD Related URL Keyword updates 8.6."
  [collection]
  (def cc collection)
  (if (nil? (:RelatedUrls collection))
    collection
    (assoc collection :RelatedUrls
      (into []
       (:RelatedUrls
        (-> collection
          (update-in-each [:RelatedUrls] replace-existing-related-url-keywords "1.11")))))))

(defn migrate-down-from-1_11
  ":RelatedUrl Type and Subtypes have changed in UMM-C version 1.11 to correspond
   to GCMD Related URL Keyword updates 8.6, so we need to translate the values back to the old
   8.5 keyword version"
  [collection]
  (if (nil? (:RelatedUrls collection))
    collection
    (assoc collection :RelatedUrls
      (into []
       (:RelatedUrls
        (-> collection
          (update-in-each [:RelatedUrls] replace-existing-related-url-keywords "1.10")))))))
