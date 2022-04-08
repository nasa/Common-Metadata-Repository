(ns cmr.system-int-test.data2.stac
  "Contains helper functions for converting granules into the expected map of parsed stac results."
  (:require
   [cmr.common.util :as util]
   [cmr.system-int-test.utils.url-helper :as url]
   [ring.util.codec :as codec]))

(defn- href
  "Returns the link href"
  ([]
   (format "%sgranules.stac" (url/search-root)))
  ([query-string]
   (format "%sgranules.stac?%s" (url/search-root) query-string))
  ([query-string page-num]
   (format "%sgranules.stac?%s&page_num=%s" (url/search-root) query-string page-num)))

(defn- result-map->expected-links
  "Returns the stac links for the given result map"
  ([result-map]
   (result-map->expected-links result-map "GET"))
  ([result-map method]
   (let [{:keys [query-string page-num prev-num next-num body]} result-map]
     (remove nil?
             [{:rel "self"
               :href (href query-string page-num)}
              {:rel "root"
               :href (url/search-root)}
              (if (= method "GET")
                (when prev-num
                  {:rel "prev"
                   :method "GET"
                   :href (href query-string prev-num)})
                (when prev-num
                  {:rel "prev"
                   :method "POST"
                   :body (into (sorted-map) (assoc body :page_num (str prev-num)))
                   :merge true
                   :href (href)}))
              (if (= method "GET")
                (when next-num
                  {:rel "next"
                   :method "GET"
                   :href (href query-string next-num)})
                (when next-num
                  {:rel "next"
                   :method "POST"
                   :body (into (sorted-map) (assoc body :page_num (str next-num)))
                   :merge true
                   :href (href)}))]))))

(defn- granule->expected-stac
  "Returns the stac map of the granule.
  This is used to verify the structure of the granule stac feature, not all the field values."
  [result-map granule]
  (let [{:keys [concept-id beginning-date-time ending-date-time cloud-cover]} granule
        {:keys [coll-concept-id geometry bbox]} result-map
        coll-href (format "%s%s.stac" (url/location-root) coll-concept-id)
        gran-href (format "%s%s" (url/location-root) concept-id)
        stac-extensions (if cloud-cover
                          ["https://stac-extensions.github.io/eo/v1.0.0/schema.json"]
                          [])]
    {:type "Feature"
     :stac_extensions stac-extensions
     :id concept-id
     :stac_version "1.0.0"
     :collection coll-concept-id
     :properties
     {:datetime beginning-date-time
      :start_datetime beginning-date-time
      :end_datetime ending-date-time
      :eo:cloud_cover cloud-cover}
     :geometry geometry
     :bbox bbox
     :assets
     {:metadata
      {:href (format "%s.xml" gran-href)
       :type "application/xml"}}
     :links
     [{:rel "self"
       :href (format "%s.stac" gran-href)}
      {:rel "parent"
       :href coll-href}
      {:rel "collection"
       :href coll-href}
      {:rel "root"
       :href (url/search-root)}
      {:rel "via"
       :href (format "%s.json" gran-href)}
      {:rel "via"
       :href (format "%s.umm_json" gran-href)}]}))

(defn result-map->expected-stac
  "Returns the stac representation of the given result map.
  result-map contains the info needed to construct the stac result, including:
  - coll-concept-id: concept id of parent collection
  - matched: number of granules found by the query
  - granules: the returned granules in the result set
  - geometry: stac geometry info. Since the different geometry test cases has been covered
              in unit tests. We use the same geometry for all granules in this test.
  - bbox: stac bbox info. Simplified similar to geometry."
  ([result-map]
   (result-map->expected-stac result-map "GET"))
  ([result-map method]
   (let [{:keys [matched granules]} result-map]
     (util/remove-nil-keys
      {:type "FeatureCollection"
       :stac_version "1.0.0"
       :numberMatched matched
       :numberReturned (count granules)
       :features (seq (map (partial granule->expected-stac result-map) granules))
       :links (result-map->expected-links result-map method)
       :context {:returned (count granules)
                 :limit 1000000
                 :matched matched}}))))
