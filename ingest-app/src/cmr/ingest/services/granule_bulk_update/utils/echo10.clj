(ns cmr.ingest.services.granule-bulk-update.utils.echo10
  "Contains functions for updating ECHO10 granule xml metadata."
  (:require
   [clojure.data.xml :as xml]
   [clojure.zip :as zip]
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

(defn- xml-elem->online-access-url
  "Parses and returns XML element for OnlineAccessURL"
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:URLDescription])
        mime-type (cx/string-at-path elem [:MimeType])]
    {:url url
     :url-description description
     :mime-type mime-type}))

(defn- update-accesses
  "Constructs the new OnlineAccessURLs node in zipper representation"
  [online-access-urls locator-field value-field value-map]
  (let [edn-access-urls (map xml-elem->online-access-url online-access-urls)
        access-urls (map #(merge %
                               (when-let [replacement (get value-map (get % locator-field))]
                                 (hash-map value-field replacement)))
                       edn-access-urls)]
    (xml/element
     :OnlineAccessURLs {}
     (for [r access-urls]
       (let [{:keys [url url-description mime-type]} r]
         (xml/element :OnlineAccessURL {}
                      (xml/element :URL {} url)
                      (when url-description (xml/element :URLDescription {} url-description))
                      (when mime-type (xml/element :MimeType {} mime-type))))))))

(defn update-online-access-urls
  "Return an OnlineAccessURLs node where UPDATE-FIELD is updated where the
  LOCATOR-FIELD has a matching key in the VALUE-MAP."
  [xml-tree locator-field value-field value-map]
  (let [online-access-urls (cx/elements-at-path
                            xml-tree
                            [:OnlineAccessURLs :OnlineAccessURL])]
    (when (seq online-access-urls)
      (update-accesses online-access-urls locator-field value-field value-map))))

(defn replace-in-tree
  "Take a parsed granule xml, replace the given node with the provided replacement
  Returns the zipper representation of the updated xml."
  [tree element-tag replacement]
  (let [zipper (zip/xml-zip tree)
        start-loc (zip/down zipper)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (if-let [right-loc (zip/right loc)]
          (cond
            ;; at an OnlineResources element, replace the node with updated value
            (= element-tag (-> right-loc zip/node :tag))
            (recur (zip/replace right-loc replacement) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false))
          (recur loc true))))))
