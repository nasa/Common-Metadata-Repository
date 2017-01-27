(ns cmr.umm-spec.migration.related-url-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require
   [cmr.common.util :refer [update-in-each]]
   [cmr.umm-spec.util :as util]))

(defn migrate-related-url-to-online-resource
  "Migrate the RelatedUrl in the element to Online Resource"
  [element]
  (if-let [related-url (:RelatedUrl element)]
    (-> element
        (assoc :OnlineResource {:Linkage (first (:URLs related-url))
                                :Name (util/with-default (:Title related-url) true)
                                :Description (util/with-default (:Description related-url) true)})
        (dissoc :RelatedUrl))
    element))

(defn migrate-online-resource-to-related-url
  "Migrate the OnlineResource in the element to RelatedUrl"
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
  (if (not-empty (:ContactInformation contacts))
   (update contacts :ContactInformation dissoc-titles-from-contact-information)
   contacts))

(defn migrate-data-centers-up
  "Get RelatedUrls from DataCenters and dissoc the Title from any RelatedUrl"
  [data-centers]
  (mapv (fn [data-center]
          (-> data-center
              (update-in-each [:ContactGroups] dissoc-titles-from-contact-information)
              (update-in-each [:ContactPersons] migrate-contacts-up)
              (update :ContactInformation dissoc-titles-from-contact-information)))
        data-centers))
