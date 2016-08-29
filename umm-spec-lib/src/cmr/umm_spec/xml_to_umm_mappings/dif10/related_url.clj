(ns cmr.umm-spec.xml-to-umm-mappings.dif10.related-url
  (:require [cmr.common.xml.simple-xpath :refer [select]]
            [cmr.common.xml.parse :refer :all]))

(defn multimedia->RelatedUrl
  [multimedia-sample]
  {:URLs (values-at multimedia-sample "URL")
   :Title (value-of multimedia-sample "Caption")
   :Description (value-of multimedia-sample "Description")
   :Relation ["GET RELATED VISUALIZATION"]})

(defn parse-related-urls
  "Extracts urls from both Related_URL and Multimedia_Sample from DIF10 XML and includes both
  concatenated together as UMM RelatedUrls"
  [doc]
  (let [multimedia-urls (mapv multimedia->RelatedUrl (select doc "/DIF/Multimedia_Sample"))
        related-urls (for [related-url (select doc "/DIF/Related_URL")]
                       ;; CMR 3253 remove the spaces in the urls
                       {:URLs (url-values-at related-url "URL")
                        :Description (value-of related-url "Description")
                        :Relation [(value-of related-url "URL_Content_Type/Type")
                                   (value-of related-url "URL_Content_Type/Subtype")]
                        :MimeType (value-of related-url "Mime_Type")})]
    (seq (into multimedia-urls related-urls))))
