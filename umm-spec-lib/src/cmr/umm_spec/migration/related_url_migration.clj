(ns cmr.umm-spec.migration.related-url-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require
   [cmr.common.util :refer [update-in-each]]
   [cmr.umm-spec.util :as util]))

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

(defn url->array-of-urls
  "UMM spec versions 1.8 and lower's RelatedUrls contain an array of URLs.
  This function changes the :URL url of a RelatedUrl to :URLs [urls]"
  [related-urls]
  (mapv (fn [related-url]
         (if-let [url (:URL related-url)]
          (-> related-url
           (assoc :URLs [url])
           (dissoc :URL)))
         related-url)
        related-urls))

(defn array-of-urls->url
  "UMM spec version 1.9's RelatedUrls contain a single URL.
  This function changes the :URLs [url] of a RelatedUrl to :URL url"
  [related-urls]
  (mapv (fn [related-url]
         (if-let [url (first (:URLs related-url))]
          (-> related-url
           (assoc :URL url)
           (dissoc :URLs :Title))
          related-url))
        related-urls))

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
          (update-in contact [:ContactInformation :RelatedUrls] array-of-urls->url)
          contact))
        contacts))

(defn migrate-contacts-down
  "Migrate ContactPersons to UMM spec v1.8 and lower"
  [contacts]
  (for [contact contacts]
   (if (seq (:ContactInformation contact))
    (update-in contact [:ContactInformation :RelatedUrls] url->array-of-urls)
    contact)))

(defn migrate-data-centers-up
  "Get RelatedUrls from DataCenters and dissoc the Title from any RelatedUrl"
  [data-centers]
  (mapv (fn [data-center]
          (-> data-center
              (update :ContactGroups migrate-contacts-up)
              (update :ContactPersons migrate-contacts-up)
              (update :ContactInformation dissoc-titles-from-contact-information)
              (update-in [:ContactInformation :RelatedUrls] array-of-urls->url)))
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
               (update-in [:ContactInformation :RelatedUrls] url->array-of-urls)))
         data-centers))
  data-centers)

(defn migrate-down-from-1_9
  ":RelatedUrl {:URL url} -> :RelatedUrl {:URLs [url]} for a given collection"
  [collection]
  (-> collection
   (update :RelatedUrls url->array-of-urls)
   (update :ContactGroups migrate-contacts-down)
   (update :ContactPersons migrate-contacts-down)
   (update :DataCenters migrate-data-centers-down)))
