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
   ["Can't update <Checksum>: no parent <DataGranule> element"]))

(defn- update-size-element
  "Takes in a zipper location, call the given insert function to build a new Size element.
   This new size element will either be added to the DataGranule or replace an existing size element."
  [loc insert-fn size])
  ;;if SizeMB, might need to add to the left

(defn- update-size-in-data-granule
  "Takes the zipper loc of a DataGranule element in the xml, and updates the size element with
   the provided values before returning the root"
  [data-granule-loc size]
  (let [start-loc (zip/down data-granule-loc)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (cond
          ;; at a Size element, replace the node with updated value
          (= :DataGranuleSizeInBytes (-> loc zip/node :tag))
          (recur (update-size-element loc zip/replace size) true)

          (= :SizeMBDataGranule (-> loc zip/node :tag))
          (recur (update-size-element loc zip/replace size) true)

          ;; at an element after size, add to the left
          (some tags-after-size [(-> loc zip/node :tag)])
          (recur (update-size-element loc zip/insert-left size) true)

          ;have reached the end of the DataGranule without encountering any of the tags-after-size.
          ;this condition should be impossible to actually trigger, as there are required fields after size.
          (nil? loc)
          (errors/throw-service-errors :invalid-data
            ["Cannot update size: invalid DataGranule element detected."])

          ;; no action needs to be taken, move to the next node
          :else
          (recur (zip/right loc) false))))))

(defn- update-data-granule-element
  "Take a parsed granule xml, update size"
  [parsed size]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc]
      (let [right-loc (zip/right loc)]
        (cond
          ;; at a DataGranule element, attempt to update/add size inside this node
          (= :DataGranule (-> right-loc zip/node :tag))
          (update-size-in-data-granule right-loc size)

          ;; at an element after DataGranule, cannot update granule
          (or (nil? right-loc) (some tags-after-data-granule [(-> right-loc zip/node :tag)]))
          (no-data-granule-error)

          ;; no action needs to be taken, move to the next node
          :else
          (recur right-loc))))))

(defn- update-size-metadata
  "Takes the granule ECHO10 xml"
  [gran-xml size]
  (let [parsed (xml/parse-str gran-xml)]
    (xml/indent-str (update-data-granule-element parsed size))))

(defn update-size
  "Update the ECHO10 granule metadata"
  [concept size]
  (let [size-vec (string/split size "z")
        updated-metadata (update-size-metadata (:metadata concept) size-vec)]
    (assoc concept :metadata updated-metadata)))
