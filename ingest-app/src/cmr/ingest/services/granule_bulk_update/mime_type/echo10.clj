(ns cmr.ingest.services.granule-bulk-update.mime-type.echo10
  "Contains functions to update ECHO10 granule xml for OnlineResource
   and OnlineAccessURL MimeType in bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.zip :as zip]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]))

(defn- xml-elem->online-resource
  "Parses and returns XML element for OnlineResource"
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

(defn- xml-elem->online-access-url
  "Parses and returns XML element for OnlineAccessURL"
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:URLDescription])
        mime-type (cx/string-at-path elem [:MimeType])]
    {:url url
     :url-description description
     :mime-type mime-type}))

(defn- update-access
  "Constructs the new OnlineAccessURLs node in zipper representation"
  [online-access-urls url-map]
  (let [edn-access-urls (map xml-elem->online-access-url online-access-urls)
        access-urls (map #(merge %
                                 (when-let [mime-type (get url-map (:url %))]
                                   {:mime-type mime-type}))
                         edn-access-urls)]
    (xml/element
     :OnlineAccessURLs {}
     (for [r access-urls]
       (let [{:keys [url url-description mime-type]} r]
         (xml/element :OnlineAccessURL {}
                      (xml/element :URL {} url)
                      (when url-description (xml/element :URLDescription {} url-description))
                      (when mime-type (xml/element :MimeType {} mime-type))))))))

(defn- replace-in-tree
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

(defn- update-online-resources
  "Return an OnlineResources node where MimeType is updated where the URL has a matching entry in the url-map."
  [tree url-map]
  (let [online-resources (cx/elements-at-path
                          tree
                          [:OnlineResources :OnlineResource])]
    (when (seq online-resources)
      (update-resources online-resources url-map))))

(defn- update-online-access-urls
  "Return an OnlineAccessURLs node where MimeType is updated where the URL has a matching entry in the url-map."
  [tree url-map]
  (let [online-access-urls (cx/elements-at-path
                            tree
                            [:OnlineAccessURLs :OnlineAccessURL])]
    (when (seq online-access-urls)
      (update-access online-access-urls url-map))))

(defn update-mime-type
  "Update the the MimeType for elements within OnlineResources and OnlineAccess in echo10
   granule metadata and return granule."
  [concept links]
  (let [parsed (xml/parse-str (:metadata concept))
        url-map (apply merge (map #(hash-map (:URL %) (:MimeType %)) links))
        _ (when-not (= (count (set (keys url-map))) (count links))
            (errors/throw-service-errors :invalid-data
                                         ["Update failed - duplicate URLs provided for granule update"]))

        online-resources (update-online-resources parsed url-map)
        access-urls (update-online-access-urls parsed url-map)

        updated-metadata (-> parsed
                             (replace-in-tree :OnlineResources online-resources)
                             (replace-in-tree :OnlineAccessURLs access-urls)
                             xml/indent-str)]
    (assoc concept :metadata updated-metadata)))
