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
  (map
   (fn [url]
     {:URL url
      :Description (value-of multimedia-sample "Description")
      :URLContentType "VisualizationURL"
      :Type "GET RELATED VISUALIZATION"})
   (values-at multimedia-sample "URL")))

(defn parse-related-urls
  "Extracts urls from both Related_URL and Multimedia_Sample from DIF10 XML and includes both
  concatenated together as UMM RelatedUrls"
  [doc sanitize?]
  (let [multimedia-urls (mapv #(multimedia->RelatedUrl % sanitize?) (select doc "/DIF/Multimedia_Sample"))
        related-urls (for [related-url (select doc "/DIF/Related_URL")
                           :let [type (value-of related-url "URL_Content_Type/Type")
                                 subtype (value-of related-url "URL_Content_Type/Subtype")
                                 url-types (get dif-util/dif-url-content-type->umm-url-types
                                             [type subtype] su/default-url-type)]]
                       (merge
                        url-types
                        {:URL (if-let [url (url/format-url (value-of related-url "URL") sanitize?)]
                                 url
                                 (when sanitize? su/not-provided-url))
                         :Description (value-of related-url "Description")
                         :MimeType (value-of related-url "Mime_Type")}))]
    (if (or multimedia-urls related-urls)
     (flatten (seq (into multimedia-urls related-urls)))
     (when sanitize? (seq su/not-provided-related-url)))))
