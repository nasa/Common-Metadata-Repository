(ns cmr.ingest.services.granule-bulk-update.mime-type.umm-g
  "Contains functions to update UMM-G granule metadata for MimeType granule bulk update."
  (:require
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]))

(defn- update-link-mime-type
  "Adds or updates MimeType for a given link, if a matching link was input"
  [link input-links-map]
  (if-let [input-link (get input-links-map (:URL link))]
    (merge link {:MimeType (:MimeType input-link)})
    link))

(defn- updated-mime-type-links
  "Prepares the related urls for updating, checks for error cases. If the granule and input links
   are suitable for updates, maps the transform function to every related url."
  [granule-links input-links]
  (let [granule-urls (mapv :URL granule-links)
        input-urls (mapv :URL input-links)
        input-link-2-vectors (map (juxt :URL identity) input-links)
        input-links-map (into (sorted-map) (vec input-link-2-vectors))]
    (when-not (every? (set granule-urls) input-urls)
      (errors/throw-service-errors :invalid-data
                                   [(str "Update failed - please only specify URLs contained in the"
                                         " existing granule RelatedURLs")]))
    (when-not (= (count (set input-urls)) (count input-urls))
      (errors/throw-service-errors :invalid-data
                                   ["Update failed - duplicate URLs provided for granule update"]))

    (map #(update-link-mime-type % input-links-map) granule-links)))

(defn update-mime-type
  "Updates MimeTypes in RelaredUrls, returns the granule"
  [_context umm-gran input-links]
  (update umm-gran :RelatedUrls #(updated-mime-type-links % input-links)))
