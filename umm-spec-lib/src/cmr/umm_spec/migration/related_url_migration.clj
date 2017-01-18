(ns cmr.umm-spec.migration.related-url-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require
   [cmr.umm-spec.util :as util]))

(defn migrate-publication-reference-to-online-resource
  "Migrate the RelatedUrl in Publication Reference to Online Resource"
  [pub-ref]
  (if-let [related-url (:RelatedUrl pub-ref)]
    (-> pub-ref
        (assoc :OnlineResource {:Linkage (first (:URLs related-url))
                                :Name (util/with-default (:Title related-url) true)
                                :Description (util/with-default (:Description related-url) true)})
        (dissoc :RelatedUrl))
    pub-ref))

(defn migrate-publication-reference-to-related-url
  "Migrate the OnlineResource in Publication Reference to RelatedUrl"
  [pub-ref]
  (if-let [online-resource (:OnlineResource pub-ref)]
    (-> pub-ref
        (assoc :RelatedUrl {:URLs [(:Linkage online-resource)]
                            :Title (:Name online-resource)
                            :Description (:Description online-resource)
                            :Relation ["VIEW RELATED INFORMATION" "Citation"]
                            :MimeType "text/html"})
        (dissoc :OnlineResource))
    pub-ref))
