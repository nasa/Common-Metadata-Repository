(ns cmr.search.results-handlers.stac-results-handler
  "Handles the STAC results format and related functions"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as er-to-qr]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common-app.services.search.results-model :as r]
   [cmr.common.services.errors :as svc-errors]
   [cmr.common.util :as util]
   [cmr.search.models.query :as q]
   [cmr.search.results-handlers.orbit-swath-results-helper :as orbit-swath-helper]
   [cmr.search.results-handlers.stac-spatial-results-handler :as ssrh]
   [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
   [cmr.search.services.url-helper :as url]
   [cmr.spatial.serialize :as srl]
   [ring.util.codec :as codec]))

(def ^:private STAC_VERSION
  "Version of STAC specification supported by CMR"
  "1.0.0")

(def ^:private MAX_RESULT_WINDOW
  "Number of max results can be returned in an Elasticsearch query."
  1000000)

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :stac]
 [concept-type query]
 (let [stac-fields ["summary"
                    "entry-title"
                    "start-date"
                    "end-date"
                    "ords-info"
                    "ords"]]
  (distinct (concat stac-fields acl-rhh/collection-elastic-fields))))

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

(defn- collection-elastic-result->query-result-item
  [elastic-result]
  (let [{concept-id :_id
         {summary :summary
          entry-title :entry-title
          start-date :start-date
          end-date :end-date
          ords-info :ords-info
          ords :ords} :_source} elastic-result
        start-date (acl-rhh/parse-elastic-datetime start-date)
        end-date (acl-rhh/parse-elastic-datetime end-date)]
    (merge {:id concept-id
            :dataset-id entry-title
            :summary summary
            :start-date start-date
            :end-date end-date
            :shapes (srl/ords-info->shapes ords-info ords)}
           (acl-rhh/parse-elastic-item :collection elastic-result))))

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
        elastic-matches (er-to-qr/get-elastic-matches elastic-results)
        items (if (= :granule (:concept-type query))
                (granule-elastic-results->query-result-items context query elastic-matches)
                (map collection-elastic-result->query-result-item elastic-matches))]
    (r/map->Results {:hits hits
                     :items items
                     :timed-out timed-out
                     :result-format (:result-format query)})))

(defmethod er-to-qr/elastic-results->query-results [:collection :stac]
  [context query elastic-results]
  (elastic-results->query-results context query elastic-results))

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
  "Returns the STAC browse type for the given link mime-type and href.
  The browse type is determined by:
  if MimeType exists in the browse link and is valid, use it; otherwise deduce from the URL suffix."
  [mime-type href]
  (if (some #{mime-type} ["image/png" "image/tiff" "image/raw" "image/jpeg"])
      mime-type
      (href->browse-type href)))

(defn- atom-link->asset
  "Returns the STAC asset value of the given atom link"
  [link]
  (when link
    (let [{:keys [title href link-type mime-type]} link]
      (util/remove-nil-keys
       {:title title
        :href href
        :type (if (= "browse" link-type)
                (get-browse-type mime-type href)
                mime-type)}))))

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
        assets {:metadata {:href metadata-link
                           :type "application/xml"}
                :browse (atom-link->asset (first browse-links))
                :opendap (atom-link->asset (first opendap-links))}]
    (util/remove-nil-keys
     (merge assets
            (data-links->assets data-links)))))

(defmulti stac-reference->json
  "Converts a search result STAC reference into json"
  (fn [context concept-type reference]
    concept-type))

(defmethod stac-reference->json :collection
  [context concept-type reference]
  (let [{:keys [id dataset-id summary start-date end-date shapes]} reference
        bbox (ssrh/shapes->stac-bbox shapes)
        _ (when-not bbox
            (svc-errors/throw-service-error
             :bad-request
             (format "Collection [%s] without spatial info is not supported in STAC" id)))
        metadata-link (url/concept-xml-url context id)
        result {:id id
                :stac_version STAC_VERSION
                :license "not-provided"
                :title dataset-id
                :type "Collection"
                :description summary
                :links [{:rel "self"
                         :href (url/concept-stac-url context id)}
                        {:rel "root"
                         :href (url/search-root context)}
                        {:rel "items"
                         :title "Granules in this collection"
                         :type "application/json"
                         :href (url/stac-request-url context (str "collection_concept_id=" id))}
                        {:rel "about"
                         :title "HTML metadata for collection"
                         :type "text/html"
                         :href (url/concept-html-url context id)}
                        {:rel "via"
                         :title "CMR JSON metadata for collection"
                         :type "application/json"
                         :href (url/concept-json-url context id)}]
                ;; Note: the double array on bbox and interval values.
                ;; Even though there will only be one value for bbox and interval,
                ;; we still put them into array of arrays based on STAC specification:
                ;; https://github.com/radiantearth/stac-spec/blob/master/collection-spec/collection-spec.md#spatial-extent-object
                :extent {:spatial {:bbox [bbox]}
                         :temporal {:interval [[start-date end-date]]}}}]
    ;; remove entries with nil value
    (util/remove-nil-keys result)))

(defmethod stac-reference->json :granule
  [context concept-type reference]
  (let [{:keys [id collection-concept-id start-date end-date atom-links cloud-cover shapes]} reference
        metadata-link (url/concept-xml-url context id)
        stac-extension (if cloud-cover
                         ;; cloud-cover requires the eo extension
                         ["https://stac-extensions.github.io/eo/v1.0.0/schema.json"]
                         [])
        geometry (ssrh/shapes->stac-geometry shapes)
        _ (when-not geometry
            (svc-errors/throw-service-error
             :bad-request
             (format "Granule [%s] without spatial info is not supported in STAC" id)))
        result {:type "Feature"
                :id id
                :stac_version STAC_VERSION
                :stac_extensions stac-extension
                :collection collection-concept-id
                :geometry geometry
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

(doseq [concept-type [:collection :granule]]
  (defmethod qs/single-result->response [concept-type :stac]
    [context query results]
    (single-result->response context query results)))

(defn stac-post-body
  "Builds :body map to be merged into the link for POST requests."
  [base-query-string]
  (let [body (codec/form-decode base-query-string)]
    (if (map? body) body {})))

(defn stac-prev-next
  "Returns link values for prev and next rels depending on the method in the context.
   For POSTs, a :body is built from the query parameters.
   :body {
      :cmr_search_param \"value\"
   }"
  [context base-query-string rel value]
  (if (= (:method context) :get)
    {:rel rel
     :method "GET"
     :href (url/stac-request-url context base-query-string value)}
    (let [body
          (into (sorted-map)
                (util/map-keys->snake_case
                 (merge (dissoc (:query-params context) :path-w-extension :page_num)
                        (assoc (stac-post-body base-query-string) :page_num (str value)))))]

      {:rel rel
       :body body
       :method "POST"
       :merge true
       :href (url/stac-request-url context)})))

(defn- get-fc-links
  "Returns the links for feature collection"
  [context query hits]
  (let [{:keys [page-size offset]} query
        page-num (if offset
                   (inc (quot offset page-size))
                   1)
        item-count (if offset
                     (+ offset page-size)
                     page-size)
        allowed-hits (min hits MAX_RESULT_WINDOW)
        match-1 "page_num=\\d+"
        match-2 "page-num=\\d+"
        page-num-pattern (re-pattern (format "%s&|&%s|%s&|&%s" match-1 match-1 match-2 match-2))
        base-query-string (-> context
                              :query-string
                              (codec/form-decode)
                              util/map-keys->snake_case
                              (codec/form-encode)
                              (string/replace page-num-pattern ""))
        prev-num (when (> page-num 1)
                   (dec page-num))
        next-num (when (> allowed-hits item-count)
                   (inc page-num))]
    (remove nil?
            [{:rel "self"
              :href (url/stac-request-url context base-query-string page-num)}
             {:rel "root"
              :href (url/search-root context)}
             (when prev-num
               (stac-prev-next context base-query-string "prev" prev-num))
             (when next-num
               (stac-prev-next context base-query-string "next" next-num))])))

(defn- results->json
  [context query results]
  (let [{:keys [hits items facets]} results
        returned (count items)]
    {:type "FeatureCollection"
     :stac_version STAC_VERSION
     :numberMatched hits
     :numberReturned returned
     :features (map (partial stac-reference->json context :granule) items)
     :links (get-fc-links context query hits)
     :context {:returned returned
               :limit MAX_RESULT_WINDOW
               :matched hits}}))

(defn- search-results->response
  [context query results]
  (let [{:keys [concept-type echo-compatible? result-features]} query
        include-facets? (boolean (some #{:facets} result-features))
        response-results (results->json context query results)]
    (json/generate-string response-results)))

(defmethod qs/search-results->response [:granule :stac]
  [context query results]
  (search-results->response context query results))
