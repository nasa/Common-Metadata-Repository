(ns cmr.umm-spec.migration.related-url-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require [cmr.umm-spec.util :as u]))

(defn migrate-publication-reference-to-online-resource
  "Migrate the RelatedUrl in Publication Reference to Online Resource"
  [pub-ref]
  (-> pub-ref
      (assoc :OnlineResource (when-let [urls (get-in pub-ref [:RelatedUrl :URLs])]
                               {:Linkage (first urls)}))
      (dissoc :RelatedUrl)))

(defn migrate-publication-reference-to-related-url
  "Migrate the OnlineResource in Publication Reference to RelatedUrl"
  [pub-ref]
  (-> pub-ref
      (assoc :RelatedUrl (when-let [url (get-in pub-ref [:OnlineResource :Linkage])]
                           {:URLs [url]}))
      (dissoc :OnlineResource)))
