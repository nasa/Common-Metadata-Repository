(ns cmr.system-int-test.data2.atom
  "Contains helper functions for converting granules into the expected map of parsed atom results."
  (:require [cmr.common.concepts :as cu]
            [cmr.umm.related-url-helper :as ru]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring :as r]
            [cmr.spatial.point :as p]
            [cmr.spatial.line :as l]
            [cmr.spatial.mbr :as m]
            [cmr.system-int-test.utils.url-helper :as url]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing the ATOM results

(comment
  (deref parsed)

  (ring-str->ring "6.92 5.18 7.01 -1.79 5.0 -2.65 5.05 4.29 6.92 5.18")

  (map xml-elem->entry (cx/elements-at-path @parsed [:entry]))

  )

(defn- point-str->points
  "Converts a string of lat lon pairs separated by spaces into a list of points"
  [s]
  (->> (str/split s #" ")
       (map #(Double. ^String %))
       (partition 2)
       (map (fn [[lat lon]]
              (p/point lon lat)))))

(defn- ring-str->ring
  "Parses a ring as represented in ATOM into a cmr.spatial.ring.Ring"
  [s]
  (r/ring (point-str->points s)))

(defn xml-elem->polygons-without-holes
  [entry-elem]
  (map #(poly/polygon [(ring-str->ring %)])
       (cx/strings-at-path entry-elem [:polygon])))

(defn xml-elem->polygons-with-holes
  [entry-elem]
  (map (fn [elem]
         (let [boundary (ring-str->ring (cx/string-at-path elem [:exterior :LinearRing :posList]))
               holes (map ring-str->ring
                          (cx/strings-at-path elem [:interior :LinearRing :posList]))]
           (poly/polygon (cons boundary holes))))
       (cx/elements-at-path entry-elem [:where :Polygon])))

(defn xml-elem->points
  [entry-elem]
  (map (comp first point-str->points) (cx/strings-at-path entry-elem [:point])))

(defn xml-elem->lines
  [entry-elem]
  (map (comp l/line point-str->points) (cx/strings-at-path entry-elem [:line])))

(defn xml-elem->bounding-rectangles
  [entry-elem]
  (map (fn [s]
         (let [[s w n e] (map #(Double. ^String %) (str/split s #" "))]
           (m/mbr w n e s)))
       (cx/strings-at-path entry-elem [:box])))


(defn xml-elem->shapes
  "Extracts the spatial shapes from the XML entry."
  [entry-elem]
  (mapcat #(% entry-elem)
          [xml-elem->polygons-without-holes
           xml-elem->polygons-with-holes
           xml-elem->points
           xml-elem->lines
           xml-elem->bounding-rectangles]))

(defn- xml-elem->entry
  "Retrns an atom entry from a parsed xml structure"
  [entry-elem]
  {:id (cx/string-at-path entry-elem [:id])
   :title (cx/string-at-path entry-elem [:title])
   :updated (cx/string-at-path entry-elem [:updated])
   :dataset-id (cx/string-at-path entry-elem [:datasetId])
   :producer-granule-id (cx/string-at-path entry-elem [:producerGranuleId])
   :size (cx/string-at-path entry-elem [:granuleSizeMB])
   :original-format (cx/string-at-path entry-elem [:originalFormat])
   :data-center (cx/string-at-path entry-elem [:dataCenter])
   :links (seq (map :attrs (cx/elements-at-path entry-elem [:link])))
   :start (cx/string-at-path entry-elem [:start])
   :end (cx/string-at-path entry-elem [:end])
   :online-access-flag (cx/string-at-path entry-elem [:onlineAccessFlag])
   :browse-flag (cx/string-at-path entry-elem [:browseFlag])
   :day-night-flag (cx/string-at-path entry-elem [:dayNightFlag])
   :cloud-cover (cx/string-at-path entry-elem [:cloudCover])
   :shapes (seq (xml-elem->shapes entry-elem))})

(def parsed (atom nil))

(defn parse-atom-result
  "Returns an atom result in map from an atom xml"
  [xml]
  (let [xml-struct (x/parse-str xml)]
    (reset! parsed xml-struct)
    {:id (cx/string-at-path xml-struct [:id])
     :title (cx/string-at-path xml-struct [:title])
     :entries (seq (map xml-elem->entry
                        (cx/elements-at-path xml-struct [:entry])))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Creating expected atom results

(def resource-type->link-type-uri
  {"GET DATA" "http://esipfed.org/ns/fedsearch/1.1/data#"
   "GET RELATED VISUALIZATION" "http://esipfed.org/ns/fedsearch/1.1/browse#"
   "ALGORITHM INFO" "http://esipfed.org/ns/fedsearch/1.1/metadata#"})

(defn- add-attribs
  "Returns the attribs with the field-value pair added if there is a value"
  [attribs field value]
  (if (empty? value) attribs (assoc attribs field value)))

(defn- related-url->link
  "Returns the atom link of the given related url"
  [related-url]
  (let [{:keys [type url title mime-type size inherited]} related-url
        title (if (= "ALGORITHM INFO" type) (str title " ()") title)
        attribs (-> {}
                    (add-attribs :inherited inherited)
                    (add-attribs :size size)
                    (add-attribs :rel (resource-type->link-type-uri type))
                    (add-attribs :type mime-type)
                    (add-attribs :title title)
                    (add-attribs :hreflang "en-US")
                    (add-attribs :href url))]
    attribs))

(defn- related-urls->links
  "Returns the atom links of the given related urls"
  [related-urls]
  (map related-url->link related-urls))

(defn- add-collection-links
  "Returns the related-urls after adding the atom-links in the collection"
  [coll related-urls]
  (let [non-browse-coll-links (filter #(not= "GET RELATED VISUALIZATION" (:type %)) (:related-urls coll))]
    (concat related-urls (map #(assoc % :inherited "true") non-browse-coll-links))))

(defn- granule->expected-atom
  "Returns the atom map of the granule"
  [granule coll]
  (let [{:keys [concept-id granule-ur producer-gran-id size related-urls
                beginning-date-time ending-date-time day-night cloud-cover]} granule
        related-urls (add-collection-links coll related-urls)
        dataset-id (get-in granule [:collection-ref :entry-title])
        update-time (get-in granule [:data-provider-timestamps :update-time])]
    {:id concept-id
     :title granule-ur
     :dataset-id dataset-id
     :producer-granule-id producer-gran-id
     :updated (str update-time)
     :size (str size)
     ;; TODO original-format will be changed to ECHO10 later once the metadata-db format is updated to ECHO10
     :original-format "application/echo10+xml"
     :data-center (:provider-id (cu/parse-concept-id concept-id))
     :links (related-urls->links related-urls)
     :start beginning-date-time
     :end ending-date-time
     :online-access-flag (str (> (count (ru/downloadable-urls related-urls)) 0))
     :browse-flag (str (> (count (ru/browse-urls related-urls)) 0))
     :day-night-flag day-night
     :cloud-cover (str cloud-cover)
     :shapes (seq (get-in granule [:spatial-coverage :geometries]))}))

(defn granules->expected-atom
  "Returns the atom map of the granules"
  [granules collections atom-path]
  {:id (str (url/search-root) atom-path)
   :title "ECHO granule metadata"
   :entries (map granule->expected-atom granules collections)})