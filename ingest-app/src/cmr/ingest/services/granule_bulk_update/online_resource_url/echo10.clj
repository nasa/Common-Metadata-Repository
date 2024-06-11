(ns cmr.ingest.services.granule-bulk-update.online-resource-url.echo10
  "Contains functions to update ECHO10 granule xml for OnlineResource
   and OnlineAccessURL in bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.utils.echo10 :as echo10-utils]))

(defn update-url
  "Update the the URL for elements within the passed in node-path-vector - for example
  [:OnlineResources :OnlineResource] - in echo10 granule metadata and return metadata."
  [concept links node-path-vector user-id] 
  (let [parsed (xml/parse-str (:metadata concept))
        url-map (apply merge (map #(hash-map (:from %) (:to %)) links))

        existing-urls (map #(cx/string-at-path % [:URL])
                           (cx/elements-at-path parsed node-path-vector))
        node-name-key (first node-path-vector)
        node-name (name node-name-key)
        _ (when-not (set/superset? (set existing-urls) (set (keys url-map)))
            (errors/throw-service-errors
             :invalid-data
             [(str "Update failed - please only specify URLs contained in the"
                   " existing granule " node-name " ["
                   (string/join ", " (set/difference (set (keys url-map)) (set existing-urls)))
                   "] were not found")]))

        _ (when-not (= (count (set (keys url-map))) (count links))
            (errors/throw-service-errors
             :invalid-data
             [(str "Update failed - duplicate URLs provided for granule update ["
                   (string/join ", " (for [[v freq] (frequencies (map :URL links)) :when (> freq 1)] v))
                   "]")]))
        urls (cx/elements-at-path parsed node-path-vector)
        elem (last node-path-vector)
        fx (case node-name-key
             :OnlineResources echo10-utils/update-online-resources
             :OnlineAccessURLs echo10-utils/update-online-access-urls
             :AssociatedBrowseImageUrls echo10-utils/update-browse-image-urls
             (errors/throw-service-error
              :invalid-data
              (format "Updating %s is not supported for format [%s]" elem (:format concept))))
        resources (when (seq urls)
                    (fx urls :url :url url-map))
        updated-metadata (-> parsed
                             (echo10-utils/replace-in-tree node-name-key resources)
                             xml/indent-str)]
    (-> concept
        (assoc :metadata updated-metadata
               :user-id user-id
               :revision-date (time-keeper/now))
        (update :revision-id inc))))

(defn add-url
  "Add or Append the the URL for elements within the passed in node-path-vector - for example
  [:OnlineResources :OnlineResource] - in echo10 granule metadata and return metadata."
  [concept links node-path-vector user-id]
  (let [parsed (xml/parse-str (:metadata concept))
        node-name-key (first node-path-vector)
        resources (case node-name-key
                    :OnlineResources (echo10-utils/links->online-resources links)
                    :OnlineAccessURLs (echo10-utils/links->online-access-urls links)
                    :AssociatedBrowseImageUrls (echo10-utils/links->provider-browse-urls links))
        updated-metadata (-> parsed
                             (echo10-utils/add-in-tree node-name-key resources)
                             xml/indent-str)]
    (-> concept
        (assoc :metadata updated-metadata
               :user-id user-id
               :revision-date (time-keeper/now))
        (update :revision-id inc))))

(defn remove-url
  "Remove the URL from the passed in node-path-vector"
  [concept links node-path-vector user-id]
  (let [parsed (xml/parse-str (:metadata concept))
        updated-metadata (-> parsed
                             (echo10-utils/remove-from-tree node-path-vector links)
                             xml/indent-str)]
    (-> concept
        (assoc :metadata updated-metadata
               :user-id user-id
               :revision-date (time-keeper/now))
        (update :revision-id inc))))
