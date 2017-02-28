(ns cmr.umm-spec.xml-to-umm-mappings.dif10.related-url
  (:require
   [clojure.string :as str]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.dif-util :as dif-util]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

(defn- multimedia->RelatedUrl
  [multimedia-sample sanitize?]
  {:URLs (map #(url/format-url % sanitize?) (values-at multimedia-sample "URL"))
   :Description (value-of multimedia-sample "Description")
   :Relation ["GET RELATED VISUALIZATION"]
   :URLContentType "VisualizationURL"
   :Type "GET RELATED VISUALIZATION"})

(defn parse-related-urls
  "Extracts urls from both Related_URL and Multimedia_Sample from DIF10 XML and includes both
  concatenated together as UMM RelatedUrls"
  [doc sanitize?]
  (let [multimedia-urls (mapv #(multimedia->RelatedUrl % sanitize?) (select doc "/DIF/Multimedia_Sample"))
        related-urls (for [related-url (select doc "/DIF/Related_URL")
                           :let [type (value-of related-url "URL_Content_Type/Type")
                                 subtype (value-of related-url "URL_Content_Type/Subtype")
                                 url-types (dif-util/dif-url-content-type->umm-url-types [type subtype])]]
                       (merge
                        url-types
                        {:URLs (if-let [urls (seq
                                              (remove nil?
                                                (map #(url/format-url % sanitize?) (values-at related-url "URL"))))]
                                  urls
                                 [su/not-provided-url])
                         :Description (value-of related-url "Description")
                         :MimeType (value-of related-url "Mime_Type")}))]
    (seq (into multimedia-urls related-urls))))
