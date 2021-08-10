(ns cmr.ingest.services.granule-bulk-update.checksum.echo10
  "Contains functions to update ECHO10 granule xml for checksum bulk granule update."
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

(def ^:private tags-after-checksum
  "Defines the element tags that come after Checksum in a DataGranule element"
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
               (xml/element :Value {} (string/trim value))
               (xml/element :Algorithm {} (string/trim algorithm))))

(defn- update-checksum-element
  "Takes in a zipper location, call the given insert function to build a new Checksum element.
   This new checksum element will either be added to the DataGranule or replace an existing Checksum element."
  [loc insert-fn checksum]
  (let [[new-value new-algorithm] (string/split checksum #",")
        current-algorithm (cx/string-at-path (zip/node loc) [:Algorithm])]
    (if (and (= insert-fn zip/insert-left) ;inserting new <Checksum> node, aka not just replacing values, so algorithm is required.
             (not new-algorithm))
      (errors/throw-service-errors :invalid-data
       ["Cannot add new <Checksum> element: please specify a checksum value as well as an algorithm."])
      (insert-fn loc (checksum-element new-value (or new-algorithm current-algorithm))))))

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
          (some tags-after-checksum [(-> loc zip/node :tag)])
          (recur (update-checksum-element loc zip/insert-left checksum) true)

          ;have reached the end of the DataGranule without encountering any of the tags-after-checksum.
          ;this condition should be impossible to actually trigger, as there are required fields after checksum.
          (nil? loc)
          (errors/throw-service-errors :invalid-data
            ["Cannot update checksum: invalid DataGranule element detected."])

          ;; no action needs to be taken, move to the next node
          :else
          (recur (zip/right loc) false))))))

(defn- update-data-granule-element
  "Take a parsed granule xml, update the <Checksum> value and optionally the algorithm.
  Returns the zipper representation of the updated xml."
  [parsed checksum]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc]
      (let [right-loc (zip/right loc)]
        (cond
          ;; at a DataGranule element, attempt to update/add checksum inside this node
          (= :DataGranule (-> right-loc zip/node :tag))
          (update-checksum-in-data-granule right-loc checksum)

          ;; at an element after DataGranule, cannot update granule
          (or (nil? right-loc) (some tags-after-data-granule [(-> right-loc zip/node :tag)]))
          (no-data-granule-error)

          ;; no action needs to be taken, move to the next node
          :else
          (recur right-loc))))))

(defn- update-checksum-metadata
  "Takes the granule ECHO10 xml and a checksum with optionally an algorithm.
  Update the ECHO10 granule metadata with the checksum. Returns the updated metadata."
  [gran-xml checksum]
  (let [parsed (xml/parse-str gran-xml)]
    (xml/indent-str (update-data-granule-element parsed checksum))))

(defn update-checksum
  "Update the ECHO10 granule metadata with checksum and optional algorithm.
  Returns the granule concept with the updated metadata."
  [concept checksum]
  (let [updated-metadata (update-checksum-metadata (:metadata concept) checksum)]
    (assoc concept :metadata updated-metadata)))
