(ns cmr.search.results-handlers.stac-results-handler
  "Handles the STAC results format and related functions"
  (:require
   [cheshire.core :as json]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as er-to-qr]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common-app.services.search.results-model :as r]
   [cmr.common.util :as util]
   [cmr.search.models.query :as q]
   [cmr.search.results-handlers.orbit-swath-results-helper :as orbit-swath-helper]
   [cmr.search.results-handlers.stac-spatial-results-handler :as ssrh]
   [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
   [cmr.search.services.url-helper :as url]
   [cmr.spatial.serialize :as srl]))

(def ^:private STAC_VERSION
  "Version of STAC specification supported by CMR"
  "1.0.0")

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :stac]
 [concept-type query]
  (let [stac-fields ["granule-ur"
                     "concept-id"
                     "collection-concept-id"
                     "provider-id"
                     "start-date"
                     "end-date"
                     "atom-links"
                     "orbit-asc-crossing-lon"
                     "start-lat"
                     "start-direction"
                     "end-lat"
                     "end-direction"
                     "orbit-calculated-spatial-domains-json"
                     "cloud-cover"
                     "ords-info"
                     "ords"]]
   (distinct (concat stac-fields acl-rhh/granule-elastic-fields))))

(defn- granule-elastic-result->query-result-item
  [orbits-by-collection elastic-result]
  (let [{concept-id :_id
         {granule-ur :granule-ur
          collection-concept-id :collection-concept-id
          provider-id :provider-id
          start-date :start-date
          end-date :end-date
          atom-links :atom-links
          ascending-crossing :orbit-asc-crossing-lon
          start-lat :start-lat
          start-direction :start-direction
          end-lat :end-lat
          end-direction :end-direction
          orbit-calculated-spatial-domains-json :orbit-calculated-spatial-domains-json
          cloud-cover :cloud-cover
          ords-info :ords-info
          ords :ords} :_source} elastic-result
        atom-links (map (fn [link-str]
                          (update-in (json/decode link-str true) [:size] #(when % (str %))))
                        atom-links)
        shapes (concat (srl/ords-info->shapes ords-info ords)
                       (when (and start-date end-date)
                         (orbit-swath-helper/elastic-result->swath-shapes
                           orbits-by-collection elastic-result)))]
    (merge {:id concept-id
            :granule-ur granule-ur
            :collection-concept-id collection-concept-id
            :start-date (acl-rhh/parse-elastic-datetime start-date)
            :end-date (acl-rhh/parse-elastic-datetime end-date)
            :atom-links atom-links
            :cloud-cover cloud-cover
            :shapes shapes}
           (acl-rhh/parse-elastic-item :granule elastic-result))))

(defn- granule-elastic-results->query-result-items
  [context query elastic-matches]
  (let [orbits-by-collection (orbit-swath-helper/get-orbits-by-collection context elastic-matches)]
    (pmap (partial granule-elastic-result->query-result-item orbits-by-collection) elastic-matches)))

(defn- elastic-results->query-results
  [context query elastic-results]
  (let [hits (er-to-qr/get-hits elastic-results)
        timed-out (er-to-qr/get-timedout elastic-results)
        scroll-id (er-to-qr/get-scroll-id elastic-results)
        search-after (er-to-qr/get-search-after elastic-results)
        elastic-matches (er-to-qr/get-elastic-matches elastic-results)
        items (granule-elastic-results->query-result-items context query elastic-matches)]
    (r/map->Results {:hits hits
                     :items items
                     :timed-out timed-out
                     :result-format (:result-format query)
                     :scroll-id scroll-id
                     :search-after search-after})))

(defmethod er-to-qr/elastic-results->query-results [:granule :stac]
  [context query elastic-results]
  (elastic-results->query-results context query elastic-results))

(defn- href->browse-type
  "Returns the STAC browse type for the given href"
  [href]
  (let [suffix (last (clojure.string/split href #"\."))]
    (case suffix
      "png" "image/png"
      "tiff" "image/tiff"
      "tif" "image/tiff"
      "raw" "image/raw"
      "image/jpeg")))

(defn- get-browse-type
  "Returns the STAC browse type for the given link. The browse type is determined by:
  if MimeType exists in the browse link and is valid, use it; otherwise deduce from the URL suffix."
  [link]
  (let [{:keys [mime-type href]} link]
    (if (some #{mime-type} ["image/png" "image/tiff" "image/raw" "image/jpeg"])
      mime-type
      (href->browse-type href))))

(defn- atom-link->asset
  "Returns the STAC asset value of the given atom link"
  [link]
  (when link
    (util/remove-nil-keys
     {:title (:title link)
      :href (:href link)
      :type (:mime-type link)})))

(defn- indexed-link->asset
  "Returns the STAC asset value of the given index and link"
  [index link]
  (let [suffix (when (> index 0) index)]
    {(keyword (str "data" suffix)) (util/remove-nil-keys
                                    {:title (:title link)
                                     :href (:href link)
                                     :type (:mime-type link)})}))

(defn- data-links->assets
  "Returns the STAC assets for the given data links"
  [data-links]
  (when (seq data-links)
    (->> data-links
         (map-indexed indexed-link->asset)
         (apply merge))))

(defn- atom-links->assets
  "Returns the STAC assets from the given atom links"
  [metadata-link atom-links]
  ;; the full rule needs to be worked in CMR-7795
  ;; here we just put in a placeholder of doing just one data link and one browse link
  (let [data-links (filter #(= (:link-type %) "data") atom-links)
        browse-links (filter #(= (:link-type %) "browse") atom-links)
        opendap-links (filter #(= (:link-type %) "service") atom-links)
        first-browse-link (first browse-links)
        first-opendap-link (first opendap-links)]
    (util/remove-nil-keys
     (merge {:metadata {:href metadata-link
                        :type "application/xml"}
             :browse (when first-browse-link
                       (util/remove-nil-keys
                        {:title (:title first-browse-link)
                         :href (:href first-browse-link)
                         :type (get-browse-type first-browse-link)}))
             :opendap (atom-link->asset first-opendap-link)}
            (data-links->assets data-links)))))

(defmulti stac-reference->json
  "Converts a search result STAC reference into json"
  (fn [context concept-type reference]
    concept-type))

(defmethod stac-reference->json :collection
  [context concept-type reference]
  ;; implement this when working on CMR-7719
  )

(defmethod stac-reference->json :granule
  [context concept-type reference]
  (let [{:keys [id collection-concept-id start-date end-date atom-links cloud-cover shapes]} reference
        metadata-link (url/concept-xml-url context id)
        stac-extension (if cloud-cover
                         ;; cloud-cover requires the eo extension
                         ["https://stac-extensions.github.io/eo/v1.0.0/schema.json"]
                         [])
        result {:type "Feature"
                :id id
                :stac_version STAC_VERSION
                :stac_extensions stac-extension
                :collection collection-concept-id
                :geometry (ssrh/shapes->stac-geometry shapes)
                :bbox (ssrh/shapes->stac-bbox shapes)
                :links [{:rel "self"
                         :href (url/concept-stac-url context id)}
                        {:rel "parent"
                         :href (url/concept-stac-url context collection-concept-id)}
                        {:rel "collection"
                         :href (url/concept-stac-url context collection-concept-id)}
                        {:rel "root"
                         :href (url/search-root context)}
                        {:rel "via"
                         :href (url/concept-json-url context id)}
                        {:rel "via"
                         :href (url/concept-umm-json-url context id)}]
                :properties (util/remove-nil-keys
                             {:datetime start-date
                              :start_datetime start-date
                              :end_datetime (or end-date start-date)
                              :eo:cloud_cover cloud-cover})
                :assets (atom-links->assets metadata-link atom-links)}]
    ;; remove entries with nil value
    (util/remove-nil-keys result)))

(defn- single-result->response
  [context query results]
  (json/generate-string
   (stac-reference->json context
                         (:concept-type query)
                         (first (:items results)))))

(defmethod qs/single-result->response [:granule :stac]
  [context query results]
  (single-result->response context query results))
