(ns cmr.system-int-test.data2.atom-json
  "Contains helper functions for converting granules into the expected map of parsed json results."
  (:require [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line :as l]
            [cmr.spatial.mbr :as m]
            [cheshire.core :as json]
            [cmr.common.util :as u]
            [clojure.string :as str]
            [cmr.system-int-test.data2.atom :as atom]))

(defn json-polygons->polygons
  [polygons]
  (map #(poly/polygon (map atom/ring-str->ring %)) polygons))

(defn json-points->points
  [points]
  (map (comp first atom/point-str->points) points))

(defn json-lines->lines
  [lines]
  (map (comp l/line atom/point-str->points) lines))

(defn json-boxes->bounding-rectangles
  [boxes]
  (map (fn [s]
         (let [[s w n e] (map #(Double. ^String %) (str/split s #" "))]
           (m/mbr w n e s)))
       boxes))

(defn json-geometry->shapes
  "Extracts the spatial shapes from the json geometry."
  [points boxes polygons lines]
  (seq (concat (json-polygons->polygons polygons)
               (json-points->points points)
               (json-lines->lines lines)
               (json-boxes->bounding-rectangles boxes))))

(defn- collection-json-entry->entry
  "Retrns an entry from a parsed collection json entry"
  [json-entry]
  (let [json-entry (u/map-keys->kebab-case json-entry)
        {:keys [id title short-name version-id summary updated dataset-id collection-data-type
                processing-level-id original-format data-center archive-center time-start time-end
                links dif-ids online-access-flag browse-flag coordinate-system
                shapes points boxes polygons lines]} json-entry]
    {:id id
     :title title
     :summary summary
     :updated updated
     :dataset-id dataset-id
     :short-name short-name
     :version-id version-id
     :original-format original-format
     :collection-data-type collection-data-type
     :data-center data-center
     :archive-center archive-center
     :processing-level-id processing-level-id
     :links links
     :start time-start
     :end time-end
     :associated-difs dif-ids
     :online-access-flag (str online-access-flag)
     :browse-flag (str browse-flag)
     :coordinate-system coordinate-system
     :shapes (json-geometry->shapes points boxes polygons lines)}))

(defn- granule-json-entry->entry
  "Retrns an entry from a parsed granule json entry"
  [json-entry]
  (let [json-entry (u/map-keys->kebab-case json-entry)
        {:keys [id title updated dataset-id producer-granule-id granule-size original-format
                data-center links time-start time-end online-access-flag browse-flag day-night-flag
                cloud-cover coordinate-system points boxes polygons lines]} json-entry]
    {:id id
     :title title
     :updated updated
     :dataset-id dataset-id
     :producer-granule-id producer-granule-id
     :size granule-size
     :original-format original-format
     :data-center data-center
     :links links
     :start time-start
     :end time-end
     :online-access-flag (str online-access-flag)
     :browse-flag (str browse-flag)
     :day-night-flag day-night-flag
     :cloud-cover cloud-cover
     :coordinate-system coordinate-system
     :shapes (json-geometry->shapes points boxes polygons lines)}))

(defn parse-json-result
  "Returns the json result from a json string"
  [concept-type json-str]
  (let [json-struct (json/decode json-str true)
        {{:keys [id title entry]} :feed} json-struct
        json-to-entry-fn (if (= :granule concept-type)
                           granule-json-entry->entry
                           collection-json-entry->entry)]
    {:id id
     :title title
     :entries (seq (map json-to-entry-fn entry))}))
