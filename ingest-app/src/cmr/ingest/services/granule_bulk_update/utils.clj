(ns cmr.ingest.services.granule-bulk-update.utils
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]))

(defn xml-elem->online-resource
  "Parses and returns XML element for OnlineResource for echo10."
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:Description])
        resource-type (cx/string-at-path elem [:Type])
        mime-type (cx/string-at-path elem [:MimeType])]
    {:url url
     :description description
     :type resource-type
     :mime-type mime-type}))

(defn- update-resources
  "Constructs the new OnlineResources node in zipper representation"
  [online-resources url-map]
  (let [edn-resources (map xml-elem->online-resource online-resources)
        resources (map #(merge %
                               (when-let [mime-type (get url-map (:url %))]
                                 {:mime-type mime-type}))
                       edn-resources)]
    (xml/element
     :OnlineResources {}
     (for [r resources]
       (let [{:keys [url description type mime-type]} r]
         (xml/element :OnlineResource {}
                      (xml/element :URL {} url)
                      (when description (xml/element :Description {} description))
                      (xml/element :Type {} type)
                      (when mime-type (xml/element :MimeType {} mime-type))))))))

(defn update-online-resources
  "Return an OnlineResources node where MimeType is updated where the URL has a matching entry in the url-map."
  [tree url-map]
  (let [online-resources (cx/elements-at-path
                          tree
                          [:OnlineResources :OnlineResource])]
    (when (seq online-resources)
      (update-resources online-resources url-map))))
