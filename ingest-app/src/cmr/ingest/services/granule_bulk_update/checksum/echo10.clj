(ns cmr.ingest.services.granule-bulk-update.checksum.echo10
  "Contains functions to update ECHO10 granule xml for S3 url bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.xml :as cx]))

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
  (let [[value algorithm] checksum]
    (xml/element :Checksum {}
                 (xml/element :Value {} value)
                 (xml/element :Algorithm {} algorithm))))

(defn- updated-checksum-node
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [checksum right-loc]
  (println right-loc)
  (checksum-element checksum))

(defn- add-checksum
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [loc insert-fn checksum]
  (insert-fn loc (checksum-element checksum)))

(defn- updated-data-granule-zipper-node
  "Take a parsed granule xml, update the <Checksum> value and optionally the algorithm.
  Returns the zipper representation of the updated xml."
  [data-granule checksum]
  (let [zipper (zip/xml-zip data-granule)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (let [right-loc (zip/right loc)]
          (cond
            ;; at the end of the file, add to the right
            (nil? right-loc)
            (recur (add-checksum loc zip/insert-right checksum) true)

            ;; at a Checksum element, replace the node with updated value
            (= :Checksum (-> right-loc zip/node :tag))
            (recur (zip/replace right-loc (updated-checksum-node checksum right-loc))
                   true)

            ;; at an element after DataGranule, add to the left
            (some tags-after-checksum [(-> right-loc zip/node :tag)])
            (recur (add-checksum right-loc zip/insert-left checksum) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false)))))))

(defn- add-data-granule-with-checksum
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [loc insert-fn checksum]
  (insert-fn loc
             (xml/element :DataGranule {} (checksum-element checksum))))

(defn- update-checksum
  "Take a parsed granule xml, update the <Checksum> value and optionally the algorithm.
  Returns the zipper representation of the updated xml."
  [parsed checksum]
  (println "test3")
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
            (recur (zip/replace right-loc (updated-data-granule-zipper-node checksum))
                   true)

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
        result (update-checksum parsed checksum)]
    (println "result: " result)
    (xml/indent-str (update-checksum parsed checksum))))

(defn update-checksum
  "Update the ECHO10 granule metadata with checksum and optional algorithm.
  Returns the granule concept with the updated metadata."
  [concept checksum]
  (let [updated-metadata (update-checksum-metadata (:metadata concept) checksum)]
    (assoc concept :metadata updated-metadata)))
