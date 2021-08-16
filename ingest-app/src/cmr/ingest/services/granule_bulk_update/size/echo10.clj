(ns cmr.ingest.services.granule-bulk-update.size.echo10
  "Contains functions to update ECHO10 granule xml for size bulk granule update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]))

(def ^:private tags-after-data-granule
  "Defines the element tags that come after DataGranule in ECHO10 Granule xml schema"
  #{:PGEVersionClass :Temporal :Spatial :OrbitCalculatedSpatialDomains :MeasuredParameters
    :Platforms :Campaigns :AdditionalAttributes :InputGranules :TwoDCoordinateSystem :Price
    :OnlineAccessURLs :OnlineResources :Orderable :DataFormat :Visible :CloudCover
    :MetadataStandardName :MetadataStandardVersion :AssociatedBrowseImages :AssociatedBrowseImageUrls})

(def ^:private tags-after-size
  "Defines the element tags that come after size in a DataGranule element"
  #{:Checksum :ReprocessingPlanned :ReprocessingActual :ProducerGranuleId :DayNightFlag :ProductionDateTime
    :LocalVersionId :AdditionalFile})

(defn- no-data-granule-error
  "Throws an error indicating the size parent element (DataGranule) is missing"
  []
  (errors/throw-service-errors :invalid-data
   ["Can't update Size: no parent <DataGranule> element"]))

(defn- get-sizes
  "gets sizes"
  [size]
  (let [sizes (map string/trim (string/split size #","))
        mb-vals (remove nil? (map #(re-find #"^[0-9]+\.[0-9]+$" %) sizes))
        byte-vals (remove nil? (map #(re-find #"^[0-9]+$" %) sizes))
        size-map (merge (when (seq mb-vals) {:SizeMBDataGranule (first mb-vals)})
                        (when (seq byte-vals) {:DataGranuleSizeInBytes (first byte-vals)}))]
    (when (or (< 2 (count sizes))
              (< 1 (count mb-vals))
              (< 1 (count byte-vals))
              (not (or (seq mb-vals) (seq byte-vals))))
      (errors/throw-service-errors :invalid-data
       [(str "Can't update Size: invalid data specified. Please include at most one value for "
             "DataGranuleSizeInBytes, and one value for SizeMBDataGranule, seperated by a comma.")]))
    size-map))

(defn- update-size-in-data-granule
  "Takes the zipper loc of a DataGranule element's leftmost child in the xml, and updates the size element with
   the provided values before returning the root"
  [loc sizes element]
  (let [{:keys [DataGranuleSizeInBytes SizeMBDataGranule]} sizes]

    (cond
      ;; at a Size element, replace the node with updated value
      (= :DataGranuleSizeInBytes element)
      (if DataGranuleSizeInBytes
        (let [xml-elem (xml/element :DataGranuleSizeInBytes {} DataGranuleSizeInBytes)]
          (if (= :DataGranuleSizeInBytes (-> loc zip/node :tag))
            (update-size-in-data-granule
             (zip/right (zip/replace loc xml-elem))
             sizes :SizeMBDataGranule)
            (update-size-in-data-granule
             (zip/insert-left loc xml-elem)
             sizes :SizeMBDataGranule)))
        (update-size-in-data-granule loc sizes :SizeMBDataGranule))

      (and (= :SizeMBDataGranule element) SizeMBDataGranule)
      (let [xml-elem (xml/element :SizeMBDataGranule {} SizeMBDataGranule)]
        (zip/root (if (= :SizeMBDataGranule (-> loc zip/node :tag))
                    (zip/replace loc xml-elem)
                    (zip/insert-left loc xml-elem))))

      ;; just return the root
      :else
      (zip/root loc))))

(defn- update-data-granule-element
  "Take a parsed granule xml, update size"
  [parsed sizes]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc]
      (let [right-loc (zip/right loc)]
        (cond
          ;; at a DataGranule element, attempt to update/add size inside this node
          (= :DataGranule (-> right-loc zip/node :tag))
          (update-size-in-data-granule (zip/down right-loc) sizes :DataGranuleSizeInBytes)

          ;; at an element after DataGranule, cannot update granule
          (or (nil? right-loc) (some tags-after-data-granule [(-> right-loc zip/node :tag)]))
          (no-data-granule-error)

          ;; no action needs to be taken, move to the next node
          :else
          (recur right-loc))))))

(defn- update-size-metadata
  "Takes the granule ECHO10 xml"
  [gran-xml size]
  (let [size-map (get-sizes size)
        parsed (xml/parse-str gran-xml)]
    (xml/indent-str (update-data-granule-element parsed size-map))))

(defn update-size
  "Update the ECHO10 granule metadata"
  [concept size]
  (let [updated-metadata (update-size-metadata (:metadata concept) size)]
    (assoc concept :metadata updated-metadata)))
