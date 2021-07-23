(ns cmr.ingest.services.granule-bulk-update.checksum.echo10
  "Contains functions to update ECHO10 granule xml for S3 url bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]
   [clojure.pprint :as p]))

;;tags for identifying xml elements within zipper structure

(def ^:private tags-after-data-granule
  "Defines the element tags that come after DataGranule in ECHO10 Granule xml schema"
  #{:PGEVersionClass :Temporal :Spatial :OrbitCalculatedSpatialDomains :MeasuredParameters
    :Platforms :Campaigns :AdditionalAttributes :InputGranules :TwoDCoordinateSystem :Price
    :OnlineAccessURLs :OnlineResources :Orderable :DataFormat :Visible :CloudCover
    :MetadataStandardName :MetadataStandardVersion :AssociatedBrowseImages :AssociatedBrowseImageUrls})

(def ^:private tags-after-checksum
  "Defines the element tags that come after Checksum in ECHO10 Granule xml schema"
  #{:ReprocessingPlanned :ReprocessingActual :ProducerGranuleId :DayNightFlag :ProductionDateTime
    :LocalVersionId :AdditionalFile})

(defn- no-data-granule-error
  "Throws an error indicating the checksum parent element (DataGranule) is missing"
  []
  (errors/throw-service-errors :invalid-data
   ["Can't update <Checksum>: no parent <DataGranule> element"]))

(defn- checksum-element
  "Returns the checksum element with the provided value, and optionally the algorithm"
  [value algorithm]
  (xml/element :Checksum {}
               (xml/element :Value {} value)
               (xml/element :Algorithm {} algorithm)))

(defn- update-checksum-element
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [loc insert-fn checksum]
  (let [[value algorithm] (string/split checksum #",")]
    (if (and (= insert-fn zip/insert-left)
             (not algorithm))
      (println "error yo")
      (insert-fn loc (checksum-element value algorithm)))))

(defn- update-checksum-in-data-granule
  "Takes the zipper loc of a DataGranule element in the xml, and updates the checksum element with
   the provided values before returning the root"
  [data-granule-loc checksum]
  (let [start-loc (zip/down data-granule-loc)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (cond
          ;; at a Checksum element, replace the node with updated value
          (= :Checksum (-> loc zip/node :tag))
          (recur (update-checksum-element loc zip/replace checksum) true)

         ;; at an element after Checksum, add to the left
         (or (nil? loc) (some tags-after-checksum [(-> loc zip/node :tag)]))
         (recur (update-checksum-element loc zip/insert-left checksum) true)

          ;; no action needs to be taken, move to the next node
         :else
         (recur (zip/right loc) false))))))

(defn- find-data-granule-element
  "Take a parsed granule xml, update the <Checksum> value and optionally the algorithm.
  Returns the zipper representation of the updated xml."
  [parsed checksum]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc]
      (let [right-loc (zip/right loc)]
        (cond
          ;; at a DataGranule element, replace the node with updated value
          (= :DataGranule (-> right-loc zip/node :tag))
          (update-checksum-in-data-granule right-loc checksum)

          ;; at an element after DataGranule, cannot update granule
          (or (nil? right-loc) (some tags-after-data-granule [(-> right-loc zip/node :tag)]))
          (no-data-granule-error)

          ;; no action needs to be taken, move to the next node
          :else
          (recur right-loc))))))

(defn- update-checksum-metadata
  "Takes the granule ECHO10 xml and a checksum with optional algorithm.
  Update the ECHO10 granule metadata with the checksum (and value, if supplied).
  Returns the updated metadata."
  [gran-xml checksum]
  (let [parsed (xml/parse-str gran-xml)
        result (find-data-granule-element parsed checksum)]
    (xml/indent-str (find-data-granule-element parsed checksum))))

(defn update-checksum
  "Update the ECHO10 granule metadata with checksum and optional algorithm.
  Returns the granule concept with the updated metadata."
  [concept checksum]
  (let [updated-metadata (update-checksum-metadata (:metadata concept) checksum)]
    (assoc concept :metadata updated-metadata)))
