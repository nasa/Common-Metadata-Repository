(ns cmr.ingest.services.granule-bulk-update.mime-type.echo10
  "Contains functions to update ECHO10 granule xml for OnlineResource
   and OnlineAccessURL MimeType in bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.utils.echo10 :as echo10-utils]))

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

(defn- update-online-access-urls
  "Return an OnlineAccessURLs node where MimeType is updated where the URL has a matching entry in the url-map."
  [tree url-map]
  (let [online-access-urls (cx/elements-at-path
                            tree
                            [:OnlineAccessURLs :OnlineAccessURL])]
    (when (seq online-access-urls)
      (update-accesses online-access-urls url-map))))

(defn update-mime-type
  "Update the the MimeType for elements within OnlineResources and OnlineAccess in echo10
   granule metadata and return granule."
  [concept links]
  (let [parsed (xml/parse-str (:metadata concept))
        url-map (apply merge (map #(hash-map (:URL %) (:MimeType %)) links))

        existing-urls (map #(cx/string-at-path % [:URL])
                           (concat (cx/elements-at-path parsed [:OnlineAccessURLs :OnlineAccessURL])
                                   (cx/elements-at-path parsed [:OnlineResources :OnlineResource])))

        _ (when-not (set/superset? (set existing-urls) (set (keys url-map)))
            (errors/throw-service-errors
             :invalid-data
             [(str "Update failed - please only specify URLs contained in the"
                   " existing granule OnlineResources or OnlineAccessURLs ["
                   (string/join ", " (set/difference (set (keys url-map)) (set existing-urls)))
                   "] were not found")]))

        _ (when-not (= (count (set (keys url-map))) (count links))
            (errors/throw-service-errors
             :invalid-data
             [(str "Update failed - duplicate URLs provided for granule update ["
                   (string/join ", " (for [[v freq] (frequencies (map :URL links)) :when (> freq 1)] v))
                   "]")]))

        online-resources (echo10-utils/update-online-resources parsed :url :mime-type url-map)
        access-urls (update-online-access-urls parsed url-map)

        updated-metadata (-> parsed
                             (replace-in-tree :OnlineResources online-resources)
                             (replace-in-tree :OnlineAccessURLs access-urls)
                             xml/indent-str)]
    (assoc concept :metadata updated-metadata)))
