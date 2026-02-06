(ns cmr.ingest.services.granule-bulk-update.mime-type.echo10
  "Contains functions to update ECHO10 granule xml for OnlineResource
   and OnlineAccessURL MimeType in bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.utils.echo10 :as echo10-utils]))

(defn update-mime-type
  "Update the the MimeType for elements within OnlineResources and OnlineAccess in echo10
   granule metadata and return granule."
  [concept links]
  (let [parsed (cx/parse-str (:metadata concept))
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

        r-urls (cx/elements-at-path parsed [:OnlineResources :OnlineResource])
        a-urls (cx/elements-at-path parsed [:OnlineAccessURLs :OnlineAccessURL])
        online-resources (echo10-utils/update-online-resources r-urls :url :mime-type url-map)
        access-urls (echo10-utils/update-online-access-urls a-urls :url :mime-type url-map)

        updated-metadata (-> parsed
                             (echo10-utils/replace-in-tree :OnlineResources online-resources)
                             (echo10-utils/replace-in-tree :OnlineAccessURLs access-urls)
                             xml/indent-str)]
    (assoc concept :metadata updated-metadata)))
