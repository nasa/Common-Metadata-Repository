(ns cmr.umm.dif.collection.related-url
  "Provide functions to parse and generate DIF Related_URL elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]))

;; There is no required element in Multimedia_Sample according to the schema
;; We only deal with DIF Related_URL for now

(defn xml-elem->RelatedURL
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
  (seq (mapcat xml-elem->RelatedURL
               (cx/elements-at-path collection-element [:Related_URL]))))


(defn generate-related-urls
  [related-urls]
  (when (seq related-urls)
    (for [related-url related-urls]
      (let [{:keys [url type sub-type description]} related-url]
        (x/element :Related_URL {}
                   (when type
                     (x/element :URL_Content_Type {}
                                (x/element :Type {} type)
                                (when sub-type (x/element :Subtype {} sub-type))))
                   (x/element :URL {} url)
                   (when description
                     (x/element :Description {} description)))))))
