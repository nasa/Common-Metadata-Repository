(ns cmr.ingest.services.granule-bulk-update.online-resource-url.echo10
  "Contains functions to update ECHO10 granule xml for OnlineResource
   and OnlineAccessURL in bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.utils.echo10 :as echo10-utils]))

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
        (echo10-utils/replace-in-tree :OnlineResources online-resources)
        xml/indent-str)))

(defn update-online-access-url
  "Update the the URL for elements within OnlineAccesses in echo10
  granule metadata and return metadata."
  [concept links]
  (let [parsed (xml/parse-str (:metadata concept))
        url-map (apply merge (map #(hash-map (:from %) (:to %)) links))

        existing-urls (map #(cx/string-at-path % [:URL])
                           (cx/elements-at-path parsed [:OnlineAccessURLs :OnlineAccessURL]))
        _ (when-not (set/superset? (set existing-urls) (set (keys url-map)))
            (errors/throw-service-errors
             :invalid-data
             [(str "Update failed - please only specify URLs contained in the"
                   " existing granule OnlineAccessURLs ["
                   (string/join ", " (set/difference (set (keys url-map)) (set existing-urls)))
                   "] were not found")]))

        _ (when-not (= (count (set (keys url-map))) (count links))
            (errors/throw-service-errors
             :invalid-data
             [(str "Update failed - duplicate URLs provided for granule update ["
                   (string/join ", " (for [[v freq] (frequencies (map :URL links)) :when (> freq 1)] v))
                   "]")]))

        online-access-urls (echo10-utils/update-online-access-urls parsed :url :url url-map)]
    (-> parsed
        (echo10-utils/replace-in-tree :OnlineAccessURLs online-access-urls)
        xml/indent-str)))
