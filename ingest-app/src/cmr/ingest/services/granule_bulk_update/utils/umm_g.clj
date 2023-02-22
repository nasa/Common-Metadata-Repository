(ns cmr.ingest.services.granule-bulk-update.utils.umm-g
  "Contains functions to update UMM-G granule metadata for RelatedURL bulk update.")

(defn- updated-related-urls
  "Take the RelatedUrls, replace any existing s3 urls with the given urls."
  [related-urls locator-field value-field value-map]
  (mapv #(merge %
                (when-let [replacement (get value-map (get % locator-field))]
                  (hash-map value-field replacement)))
        related-urls))

(defn- remove-related-urls
  "Take the RelatedUrls and remove any URLs that match;
  returning a new list of related-urls."
  [related-urls urls]
  (loop [urls urls related-urls related-urls]
    (cond 
      (nil? (seq urls))
      (seq related-urls)
      
      :else
      (let [url (:URL (first urls))
            ru (remove #(= (:URL %) url) related-urls)]
        (recur (rest urls) ru)))))

(defn update-urls
  "Takes UMM-G record and a list of links to update.
  Returns the updated UMM-G record."
  [_context umm-gran links]
  (let [url-map (apply merge (map #(hash-map (:from %) (:to %)) links))]
    (update umm-gran :RelatedUrls #(updated-related-urls % :URL :URL url-map))))

(defn append-urls
  "Takes UMM-G record and a list of urls to append.
  Returns the updated UMM-G record."
  [_context umm-gran urls]
  (update umm-gran :RelatedUrls #(concat % urls)))

(defn remove-urls
  "Takes UMM-G record and a list of urls to remove.
  Returns the updated UMM-G record."
  [_context umm-gran urls]
  (update umm-gran :RelatedUrls #(remove-related-urls % urls)))
