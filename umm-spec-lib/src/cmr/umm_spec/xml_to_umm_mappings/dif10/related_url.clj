(ns cmr.umm-spec.xml-to-umm-mappings.dif10.related-url
  (:require
   [clojure.string :as str]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.url :as url]))

(defn- multimedia->RelatedUrl
  [multimedia-sample sanitize?]
  {:URLs (map #(url/format-url % sanitize?) (values-at multimedia-sample "URL"))
   :Title (value-of multimedia-sample "Caption")
   :Description (value-of multimedia-sample "Description")
   :Relation ["GET RELATED VISUALIZATION"]})

(defn parse-related-urls
  "Extracts urls from both Related_URL and Multimedia_Sample from DIF10 XML and includes both
  concatenated together as UMM RelatedUrls"
  [doc sanitize?]
  (let [multimedia-urls (mapv #(multimedia->RelatedUrl % sanitize?) (select doc "/DIF/Multimedia_Sample"))
        related-urls (for [related-url (select doc "/DIF/Related_URL")]
                       {:URLs (map #(url/format-url % sanitize?) (values-at related-url "URL"))
                        :Description (value-of related-url "Description")
                        :Relation [(value-of related-url "URL_Content_Type/Type")
                                   (value-of related-url "URL_Content_Type/Subtype")]
                        :MimeType (value-of related-url "Mime_Type")})]
    (seq (into multimedia-urls related-urls))))
