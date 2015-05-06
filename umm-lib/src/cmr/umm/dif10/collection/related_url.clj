(ns cmr.umm.dif10.collection.related-url
  "Provide functions to parse and generate DIF Related_URL elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

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