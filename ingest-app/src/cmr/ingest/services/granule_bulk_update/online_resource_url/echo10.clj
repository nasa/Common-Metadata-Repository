(ns cmr.ingest.services.granule-bulk-update.online-resource-url.echo10
  "Contains functions to update ECHO10 granule xml for OnlineResource
   and OnlineAccessURL in bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.log :refer [info debug]]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.utils.echo10 :as echo10-utils]))

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

(defn update-online-resource-url
  "Update the the URL for elements within OnlineResources in echo10
  granule metadata and return metadata."
  [concept links]
  (let [parsed (xml/parse-str (:metadata concept))
        url-map (apply merge (map #(hash-map (:from %) (:to %)) links))

        existing-urls (map #(cx/string-at-path % [:URL])
                           (cx/elements-at-path parsed [:OnlineResources :OnlineResource]))
        _ (when-not (set/superset? (set existing-urls) (set (keys url-map)))
            (errors/throw-service-errors
             :invalid-data
             [(str "Update failed - please only specify URLs contained in the"
                   " existing granule OnlineResources ["
                   (string/join ", " (set/difference (set (keys url-map)) (set existing-urls)))
                   "] were not found")]))

        _ (when-not (= (count (set (keys url-map))) (count links))
            (errors/throw-service-errors
             :invalid-data
             [(str "Update failed - duplicate URLs provided for granule update ["
                   (string/join ", " (for [[v freq] (frequencies (map :URL links)) :when (> freq 1)] v))
                   "]")]))

        online-resources (echo10-utils/update-online-resources parsed :url :url url-map)]
    (-> parsed
        (replace-in-tree :OnlineResources online-resources)
        xml/indent-str)))
