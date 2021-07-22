(ns cmr.ingest.services.granule-bulk-update.checksum.echo10
  "Contains functions to update ECHO10 granule xml for S3 url bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.xml :as cx]
   [clojure.pprint :as p]))

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

(defn- checksum-element
  "Returns the OnlineAccessURL xml element for the given url"
  [checksum]
  (let [[value algorithm] (string/split checksum #",")]
    (xml/element :Checksum {}
                 (xml/element :Value {} value)
                 (xml/element :Algorithm {} algorithm))))

(defn- updated-checksum-node
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [checksum right-loc]
  (checksum-element checksum))

(defn- add-checksum
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [loc insert-fn checksum]
  (insert-fn loc (checksum-element checksum)))

(defn- updated-checksum-zipper-node
  "Take a parsed granule xml, update the <Checksum> value and optionally the algorithm.
  Returns the zipper representation of the updated xml."
  [data-granule-loc checksum]
  (if-not (zip/children data-granule-loc)
    (zip/insert-child data-granule-loc (checksum-element checksum))
    (let [start-loc (zip/down data-granule-loc)]
      (loop [loc start-loc done false]
        (if done
          (zip/root loc)
          (cond
            ;; at a Checksum element, replace the node with updated value
            (= :Checksum (-> loc zip/node :tag))
            (recur (zip/replace loc (updated-checksum-node checksum loc))
                   true)

           ;; at an element after Checksum, add to the left
           (some tags-after-checksum [(-> loc zip/node :tag)])
           (recur (add-checksum loc zip/insert-left checksum) true)

            ;; at the end of the file, add to the left
           (nil? loc)
           (recur (add-checksum loc zip/insert-left checksum) true)

            ;; no action needs to be taken, move to the next node
           :else
           (recur (zip/right loc) false)))))))

(defn- add-data-granule-with-checksum
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [loc insert-fn checksum]
  (insert-fn loc
             (xml/element :DataGranule {} (checksum-element checksum))))

(defn- update-data-granule-zipper-node
  "Take a parsed granule xml, update the <Checksum> value and optionally the algorithm.
  Returns the zipper representation of the updated xml."
  [parsed checksum]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (let [right-loc (zip/right loc)]
          (cond
            ;; at the end of the file, add to the right
            (nil? right-loc)
            (recur (add-data-granule-with-checksum loc zip/insert-right checksum) true)

            ;; at a DataGranule element, replace the node with updated value
            (= :DataGranule (-> right-loc zip/node :tag))
            (updated-checksum-zipper-node right-loc checksum)


            ;; at an element after DataGranule, add to the left
            (some tags-after-data-granule [(-> right-loc zip/node :tag)])
            (recur (add-data-granule-with-checksum right-loc zip/insert-left checksum) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false)))))))

(defn- update-checksum-metadata
  "Takes the granule ECHO10 xml and a checksum with optional algorithm.
  Update the ECHO10 granule metadata with the checksum (and value, if supplied).
  Returns the updated metadata."
  [gran-xml checksum]
  (let [parsed (xml/parse-str gran-xml)
        result (update-data-granule-zipper-node parsed checksum)
        _ (p/pprint result)]
    (xml/indent-str (update-data-granule-zipper-node parsed checksum))))

(defn update-checksum
  "Update the ECHO10 granule metadata with checksum and optional algorithm.
  Returns the granule concept with the updated metadata."
  [concept checksum]
  (let [updated-metadata (update-checksum-metadata (:metadata concept) checksum)]
    (assoc concept :metadata updated-metadata)))
