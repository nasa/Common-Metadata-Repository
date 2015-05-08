(ns cmr.umm.dif10.collection.related-url
  "Provide functions to parse and generate DIF10 Related_URL elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn build-RelatedURLs
  "Returns a function to build UMM RelatedURL records from the corresponding DIF10 xml element: Related_URL.
  Note that a single Related_URL element in DIF10 can have several urls within it."
  [related-url-elem]
  (let [urls (cx/strings-at-path related-url-elem [:URL])
        description (cx/string-at-path related-url-elem [:Description])
        type (cx/string-at-path related-url-elem [:URL_Content_Type :Type])
        sub-type (cx/string-at-path related-url-elem [:URL_Content_Type :Subtype])]
    (map (fn [url]
           (c/map->RelatedURL
             {:url url
              :description description
              :title description
              :type type
              :sub-type sub-type}))
         urls)))

(defn xml-elem->RelatedURLs
  [collection-element]
  (seq (mapcat build-RelatedURLs
               (cx/elements-at-path collection-element [:Related_URL]))))

(defn generate-related-urls
  [related-urls]
  (if (seq related-urls)
    (for [related-url related-urls]
      (let [{:keys [url type sub-type description]} related-url]
        (x/element :Related_URL {}
                   (when type
                     (x/element :URL_Content_Type {}
                                (x/element :Type {} type)
                                (when sub-type (x/element :Subtype {} sub-type))))
                   (x/element :URL {} url)
                   (when description
                     (x/element :Description {} description)))))
    ;;Added since Related_URL is a required field in DIF10
    (x/element :Related_URL {}
               (x/element :URL {} ""))))