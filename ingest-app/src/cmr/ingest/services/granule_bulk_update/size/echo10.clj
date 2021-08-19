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

(def ^:private tags-after-size-in-bytes
  "Defines the element tags that come after size in a DataGranule element"
  #{:SizeMBDataGranule :Checksum :ReprocessingPlanned :ReprocessingActual :ProducerGranuleId :DayNightFlag :ProductionDateTime
    :LocalVersionId :AdditionalFile})

(def ^:private tags-after-size-in-mb
  "Defines the element tags that come after size in a DataGranule element"
  #{:Checksum :ReprocessingPlanned :ReprocessingActual :ProducerGranuleId :DayNightFlag :ProductionDateTime
    :LocalVersionId :AdditionalFile})

(defn- no-data-granule-error
  "Throws an error indicating the size parent element (DataGranule) is missing"
  []
  (errors/throw-service-errors :invalid-data
   ["Can't update Size: no parent <DataGranule> element"]))

(defn- get-sizes
  "Returns a map containing the MB value and the byte value for a granule's input"
  [size]
  (let [sizes (map string/trim (string/split size #","))
        mb-vals (remove nil? (map #(re-find #"^[0-9]+\.[0-9]+$" %) sizes))
        byte-vals (remove nil? (map #(re-find #"^[0-9]+$" %) sizes))
        size-map (merge (when (seq mb-vals) {:SizeMBDataGranule (first mb-vals)})
                        (when (seq byte-vals) {:DataGranuleSizeInBytes (first byte-vals)}))]
    (when (or (< 2 (count sizes)) ;;too many values
              (< 1 (count mb-vals)) ;;too many MB values
              (< 1 (count byte-vals)) ;;too many byte values
              (not= (+ (count byte-vals) (count mb-vals))
                    (count sizes)) ;;extraneous values (don't match int or double) specified
              (not (or (seq mb-vals) (seq byte-vals)))) ;;no valid values specified
      (errors/throw-service-errors :invalid-data
       [(str "Can't update Size: invalid data specified. Please include at most one value for "
             "DataGranuleSizeInBytes, and one value for SizeMBDataGranule, seperated by a comma. "
             "DataGranuleSizeInBytes must be an integer value, while SizeMBDataGranule must be a "
             "double value with a decimal point.")]))
    size-map))

(defn- size-in-mb-element
  "Returns an XML element for size in MB"
  [val]
  (xml/element :SizeMBDataGranule {} val))

(defn- update-size-in-mb
  "Takes a loc of the leftmost child loc of the DataGranule element, adds/updates MB value before returning root"
  [start-loc sizes]
  (let [{:keys [SizeMBDataGranule]} sizes]
    (loop [loc start-loc done (if SizeMBDataGranule false true)]
      (if done
        (zip/root loc)
        (cond
          ;;Already exists, replace
          (= :SizeMBDataGranule (-> loc zip/node :tag))
          (recur (zip/replace loc (size-in-mb-element SizeMBDataGranule)) true)

          ;;adding in new element, insert to left
          (some tags-after-size-in-mb [(-> loc zip/node :tag)])
          (recur (zip/insert-left loc (size-in-mb-element SizeMBDataGranule)) true)

          ;;this shouldn't be possible, as there are required tags after size elements
          (nil? loc)
          (recur nil true)

          ;; no action needs to be taken, move to the next node
          :else
          (recur (zip/right loc) false))))))

(defn- size-in-bytes-element
  "Returns an XML element for size in bytes"
  [val]
  (xml/element :DataGranuleSizeInBytes {} val))

(defn- update-size-in-bytes
  "Takes a loc of the leftmost child loc of the DataGranule element, adds/updated size in byte-vals
   before calling (update-size-in-mb..) which performs the same operation on size in MB and then returns the root"
  [start-loc sizes]
  (let [{:keys [DataGranuleSizeInBytes]} sizes]
    (loop [loc start-loc done (if DataGranuleSizeInBytes false true)]
      (if done
        (update-size-in-mb (zip/leftmost loc) sizes)
        (cond
          ;;Already exists, replace
          (= :DataGranuleSizeInBytes (-> loc zip/node :tag))
          (recur (zip/replace loc (size-in-bytes-element DataGranuleSizeInBytes)) true)

          ;;adding in new element, insert to left
          (some tags-after-size-in-bytes [(-> loc zip/node :tag)])
          (recur (zip/insert-left loc (size-in-bytes-element DataGranuleSizeInBytes)) true)

          ;;this shouldn't be possible, as there are required tags after size elements
          (nil? loc)
          (recur nil true)

          ;; no action needs to be taken, move to the next node
          :else
          (recur (zip/right loc) false))))))

(defn- update-data-granule-element
  "Take a parsed granule xml, locate the DataGranule element, and updates sizes within"
  [parsed sizes]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc]
      (let [right-loc (zip/right loc)]
        (cond
          ;; at a DataGranule element, attempt to update/add size inside this node
          (= :DataGranule (-> right-loc zip/node :tag))
          (update-size-in-bytes (zip/down right-loc) sizes)

          ;; at an element after DataGranule, cannot update granule
          (or (nil? right-loc) (some tags-after-data-granule [(-> right-loc zip/node :tag)]))
          (no-data-granule-error)

          ;; no action needs to be taken, move to the next node
          :else
          (recur right-loc))))))

(defn- update-size-metadata
  "Takes the granule ECHO10 xml, parses the input values, and returns the updated xml"
  [gran-xml size]
  (let [size-map (get-sizes size)
        parsed (xml/parse-str gran-xml)]
    (xml/indent-str (update-data-granule-element parsed size-map))))

(defn update-size
  "Updates the ECHO10 granule metadata"
  [concept size]
  (let [updated-metadata (update-size-metadata (:metadata concept) size)]
    (assoc concept :metadata updated-metadata)))
