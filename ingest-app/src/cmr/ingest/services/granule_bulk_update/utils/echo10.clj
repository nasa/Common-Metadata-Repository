(ns cmr.ingest.services.granule-bulk-update.utils.echo10
  "Contains functions for updating ECHO10 granule xml metadata."
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]))

(defn xml-elem->online-resource
  "Parses and returns XML element for OnlineResource."
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
  [online-resources locator-field value-field value-map]
  (let [edn-resources (map xml-elem->online-resource online-resources)
        resources (map #(merge %
                               (when-let [replacement (get value-map (get % locator-field))]
                                 (hash-map value-field replacement)))
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
  "Return an OnlineResources node where UPDATE-FIELD is updated where the
  LOCATOR-FIELD has a matching key in the VALUE-MAP."
  [xml-tree locator-field update-field value-map]
  (let [online-resources (cx/elements-at-path
                          xml-tree
                          [:OnlineResources :OnlineResource])]
    (when (seq online-resources)
      (update-resources online-resources locator-field update-field value-map))))
