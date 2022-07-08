(ns cmr.system-int-test.data2.atom-json
  "Contains helper functions for converting concepts into the expected map of
  parsed json results."
  (:require [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [clojure.string :as str]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.umm-spatial :as umm-s]
            [cmr.umm.echo10.spatial :as echo-s]
            [cmr.system-int-test.data2.atom :as atom]
            [cmr.system-int-test.data2.facets :as f]
            [cmr.common.date-time-parser :as dtp]))

(defn json-polygons->polygons
  [polygons]
  (map #(poly/polygon (map umm-s/lat-lon-point-str->ring %)) polygons))

(defn json-points->points
  [points]
  (map (comp first umm-s/lat-lon-point-str->points) points))

(defn json-lines->lines
  [lines]
  (map (comp l/line-string umm-s/lat-lon-point-str->points) lines))

(defn json-boxes->bounding-rectangles
  [boxes]
  (map (fn [s]
         (let [[s w n e] (map #(Double. ^String %) (str/split s #" "))]
           (m/mbr w n e s)))
       boxes))

(defn json-geometry->shapes
  "Extracts the spatial shapes from the json geometry."
  [coordinate-system points boxes polygons lines]
  (when-let [coordinate-system (some-> coordinate-system str/lower-case keyword)]
    (let [coordinate-system (if (= :orbit coordinate-system)
                              :geodetic
                              coordinate-system)]
      (seq (map (partial umm-s/set-coordinate-system coordinate-system)
                (concat (json-polygons->polygons polygons)
                        (json-points->points points)
                        (json-lines->lines lines)
                        (json-boxes->bounding-rectangles boxes)))))))

(defmulti json-entry->entry
  "Retrns an entry from a parsed json entry"
  (fn [concept-type json-entry]
    concept-type))

(defn parse-long
  [^String v]
  (when-not (str/blank? v)
    (Long. v)))

(defn parse-double
  [^String v]
  (when-not (str/blank? v)
    (Double. v)))

(defn parse-integer
  [^String v]
  (when-not (str/blank? v)
    (Integer. v)))

(defn- parse-orbit-parameters
  "Parse orbit-parameters map"
  [orbit-params]
  (when orbit-params
    (let [result (util/remove-nil-keys
                   (into orbit-params
                         (for [[k v] orbit-params] [k (parse-double v)])))]
      ;; Don't return an empty map
      (when (seq result)
        result))))

(defn- parse-orbit
  "Parse orbit map"
  [orbit]
  (when orbit
    (-> orbit
        (update-in [:ascending-crossing] parse-double)
        (update-in [:start-lat] parse-double)
        (update-in [:start-direction] echo-s/orbit-direction->key)
        (update-in [:end-lat] parse-double)
        (update-in [:end-direction] echo-s/orbit-direction->key))))

(defn- parse-ocsd
  "Parse orbit-calculated-spatial-domain map"
  [ocsd]
  (into ocsd (util/remove-nil-keys
               {:orbit-number (parse-long (:orbit-number ocsd))
                :start-orbit-number (parse-integer (:start-orbit-number ocsd))
                :stop-orbit-number (parse-integer (:stop-orbit-number ocsd))
                :equator-crossing-longitude (parse-double (:equator-crossing-longitude ocsd))})))

(defmethod json-entry->entry :collection
  [concept-type json-entry]
  (let [tags (util/map-keys name (:tags json-entry))
        json-entry (util/map-keys->kebab-case json-entry)
        {:keys [id title short-name version-id summary updated dataset-id collection-data-type
                processing-level-id original-format data-center archive-center time-start time-end
                links dif-ids online-access-flag browse-flag coordinate-system score platforms
                shapes points boxes polygons lines granule-count has-granules has-granules-or-cwic
                has-variables has-formats has-transforms has-spatial-subsetting
                has-temporal-subsetting cloud-hosted orbit-parameters highlighted-summary-snippets
                organizations service-features associations consortiums]} json-entry]
    (util/remove-nil-keys
      {:id id
       :consortiums consortiums
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
       :organizations organizations
       :platforms platforms
       :processing-level-id processing-level-id
       :links (seq links)
       :start (some-> time-start dtp/parse-datetime)
       :end (some-> time-end dtp/parse-datetime)
       :associated-difs dif-ids
       :online-access-flag online-access-flag
       :browse-flag browse-flag
       :coordinate-system coordinate-system
       :score score
       :granule-count granule-count
       :has-granules has-granules
       :has-variables has-variables
       :has-formats has-formats
       :has-transforms has-transforms
       :has-spatial-subsetting has-spatial-subsetting
       :has-temporal-subsetting has-temporal-subsetting
       :cloud-hosted cloud-hosted
       :shapes (json-geometry->shapes coordinate-system points boxes polygons lines)
       :orbit-parameters (parse-orbit-parameters orbit-parameters)
       :highlighted-summary-snippets highlighted-summary-snippets
       :tags (when (seq tags) tags)
       :service-features service-features
       :associations (when (seq associations)
                       (util/map-values set associations))})))

(defmethod json-entry->entry :granule
  [concept-type json-entry]
  (let [json-entry (util/map-keys->kebab-case json-entry)
        {:keys [id title updated dataset-id producer-granule-id granule-size original-format
                data-center links time-start time-end online-access-flag browse-flag day-night-flag
                cloud-cover coordinate-system points boxes polygons lines
                orbit orbit-calculated-spatial-domains collection-concept-id]} json-entry
        shapes (json-geometry->shapes coordinate-system points boxes polygons lines)]
    (util/remove-nil-keys
      {:id id
       :title title
       :updated updated
       :dataset-id dataset-id
       :collection-concept-id collection-concept-id
       :producer-granule-id producer-granule-id
       :size (parse-double granule-size)
       :original-format original-format
       :data-center data-center
       :links (seq links)
       :orbit (parse-orbit orbit)
       :orbit-calculated-spatial-domains (seq (map parse-ocsd orbit-calculated-spatial-domains))
       :start (some-> time-start dtp/parse-datetime)
       :end (some-> time-end dtp/parse-datetime)
       :online-access-flag online-access-flag
       :browse-flag browse-flag
       :day-night-flag day-night-flag
       :cloud-cover (parse-double cloud-cover)
       :coordinate-system coordinate-system
       :shapes shapes})))

(defn- echo-term-count->cmr-value-count
  "Converts echo term count map into cmr value count vector"
  [term-count]
  (let [{:keys [term count]} term-count]
    [term count]))

(defn- echo-facet->cmr-facet
  "Converts the echo-facet into cmr-facet format"
  [[field values]]
  {:value-counts (map echo-term-count->cmr-value-count values)
   :field (f/echo-facet-key->cmr-facet-name (csk/->kebab-case-keyword field))})

(defn parse-echo-json-result
  "Returns the json result from a echo json string"
  [json-str]
  (let [json-struct (json/decode json-str true)]
    (map echo-facet->cmr-facet json-struct)))

(defn parse-json-result
  "Returns the json result from a json string"
  [concept-type json-str]
  (let [json-struct (json/decode json-str true)
        {{:keys [id title entry facets]} :feed} json-struct]
    (util/remove-nil-keys
      {:id id
       :title title
       :entries (seq (map (partial json-entry->entry concept-type) entry))
       :facets facets})))

(defn parse-json-collection
  "Parses a standalone collection from a JSON string."
  [json-str]
  (json-entry->entry :collection (json/decode json-str true)))

(defn parse-json-granule
  "Parses a standalone granule from a JSON string."
  [json-str]
  (json-entry->entry :granule (json/decode json-str true)))

(defn- update-inherited-link
  "Update the inherited field from string to boolean value"
  [link]
  (if-let [inherited (:inherited link)]
    (update-in link [:inherited] = "true")
    link))

(defn- update-inherited-links
  "Update the inherited links for the given entry"
  [entry]
  (if (seq (:links entry))
    (update-in entry [:links] (partial map update-inherited-link))
    entry))

(defn- update-entries
  "Update the entries by fixing the inherited field for each link.
  The atom value is string true and the json value is boolean true."
  [entries]
  (map update-inherited-links entries))

(defn granules->expected-json
  "Returns the json map of the granules"
  [granules collections atom-path]
  (let [expected-atom (atom/granules->expected-atom granules collections atom-path)]
    (update-in expected-atom [:entries] update-entries)))
