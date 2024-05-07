(ns cmr.umm.dif10.collection.related-url
  "Provide functions to parse and generate DIF10 Related_URL elements."
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]))

(defn multimedia->RelatedUrl
  [elem]
  (coll/map->RelatedURL
    {:url (cx/string-at-path elem [:URL])
     :description (cx/string-at-path elem [:Description])
     :title (cx/string-at-path elem [:Caption])
     ;; assume all Multimedia_Samples are browse
     :type "GET RELATED VISUALIZATION"}))

(defn build-RelatedURLs
  "Returns a function to build UMM RelatedURL records from the corresponding DIF10 xml element: Related_URL.
  Note that a single Related_URL element in DIF10 can have several urls within it."
  [related-url-elem]
  (let [urls (cx/strings-at-path related-url-elem [:URL])
        description (cx/string-at-path related-url-elem [:Description])
        type (cx/string-at-path related-url-elem [:URL_Content_Type :Type])
        sub-type (cx/string-at-path related-url-elem [:URL_Content_Type :Subtype])
        mime-type (cx/string-at-path related-url-elem [:Mime_Type])]
    (map (fn [url]
           (coll/map->RelatedURL
             {:url url
              :description description
              :title description
              :type type
              :sub-type sub-type
              :mime-type mime-type}))
         urls)))

(defn xml-elem->RelatedURLs
  [coll-elem]
  (let [multimedia-urls (mapv multimedia->RelatedUrl
                              (cx/elements-at-path coll-elem [:Multimedia_Sample]))
        related-urls (mapcat build-RelatedURLs
                             (cx/elements-at-path coll-elem [:Related_URL]))]
    (seq (into multimedia-urls related-urls))))

(defn generate-related-urls
  [related-urls]
  (if (seq related-urls)
    (for [{:keys [url type sub-type description mime-type]} related-urls]
      (xml/element :Related_URL {}
                 (when type
                   (xml/element :URL_Content_Type {}
                              (xml/element :Type {} type)
                              (when sub-type (xml/element :Subtype {} sub-type))))
                 (xml/element :URL {} url)
                 (when description
                   (xml/element :Description {} description))
                 (when mime-type
                   (xml/element :Mime_Type {} mime-type))))
    ;; Added since Related_URL is a required field in DIF10. CMRIN-79
    (xml/element :Related_URL {}
               (xml/element :URL {} coll/not-provided-url))))
