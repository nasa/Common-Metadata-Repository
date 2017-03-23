(ns cmr.umm.dif10.collection.related-url
  "Provide functions to parse and generate DIF10 Related_URL elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.dif.dif-core :as dif]
            [cmr.umm.umm-collection :as c]))

(defn multimedia->RelatedUrl
  [elem]
  (c/map->RelatedURL
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
           (c/map->RelatedURL
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
      (x/element :Related_URL {}
                 (when type
                   (x/element :URL_Content_Type {}
                              (x/element :Type {} type)
                              (when sub-type (x/element :Subtype {} sub-type))))
                 (x/element :URL {} url)
                 (when description
                   (x/element :Description {} description))
                 (when mime-type
                   (x/element :Mime_Type {} mime-type))))
    ;; Added since Related_URL is a required field in DIF10. CMRIN-79
    (x/element :Related_URL {}
               (x/element :URL {} c/not-provided-url))))
