(ns cmr.ingest.services.granule-bulk-update.format.echo10
  "Contains functions to update ECHO10 granule xml for format bulk granule update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.zip :as zip]))

(def ^:private tags-after-data-format
  "Defines the element tags that come after DataFormat in ECHO10 Granule xml schema"
  #{:Visible :CloudCover :MetadataStandardName :MetadataStandardVersion :AssociatedBrowseImages :AssociatedBrowseImageUrls})

(defn- data-format-element
  "Returns the DataFormat element with the provided value"
  [value]
  (xml/element :DataFormat {} value))

(defn- update-data-format-element
  "Take a parsed granule xml, update the <DataFormat>.
  Returns the zipper representation of the updated xml."
  [parsed format]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (let [right-loc (zip/right loc)]
          (cond
            ;;reached the end of the granule, add DataFormat as final element
            (nil? right-loc)
            (recur (zip/insert-right loc (data-format-element format)) true)

            ;; at a DataFormat element, update this node
            (= :DataFormat (-> right-loc zip/node :tag))
            (recur (zip/replace right-loc (data-format-element format)) true)

            ;; at an element after DataFormat, update to left
            (some tags-after-data-format [(-> right-loc zip/node :tag)])
            (recur (zip/insert-left right-loc (data-format-element format)) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false)))))))

(defn- update-format-metadata
  "Takes the granule ECHO10 xml and format value.
  Update the ECHO10 granule metadata with the format. Returns the updated metadata."
  [gran-xml format]
  (let [parsed (xml/parse-str gran-xml)]
    (xml/indent-str (update-data-format-element parsed format))))

(defn update-format
  "Update the ECHO10 granule metadata with format.
  Returns the granule concept with the updated metadata."
  [concept format]
  (let [updated-metadata (update-format-metadata (:metadata concept) format)]
    (assoc concept :metadata updated-metadata)))
