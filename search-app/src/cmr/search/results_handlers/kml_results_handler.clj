(ns cmr.search.results-handlers.kml-results-handler
  "Handles the returning search results in KML format (keyhole markup language for Google Earth etc)"
  (:require
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as er-to-qr]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common-app.services.search.results-model :as r]
   [cmr.common.services.errors :as svc-errors]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.orbit-swath-results-helper :as orbit-swath-helper]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
   [cmr.spatial.cartesian-ring :as cr]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.kml :as kml]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.relations :as relations]
   [cmr.spatial.serialize :as srl]))

(defmethod gcrf/query-results->concept-ids :kml
  [results]
  (svc-errors/throw-service-error
    :bad-request
    "Collections search in kml format is not supported with include_granule_counts option"))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :kml]
  [concept-type query]
  ["entry-title"
   "ords-info"
   "ords"])

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :kml]
  [concept-type query]
  (vec (into #{"granule-ur" "ords-info" "ords"}
             orbit-swath-helper/orbit-elastic-fields)))

(defn collection-elastic-result->query-result-item
  [elastic-result]
  (let [{granule-ur :granule-ur
         entry-title :entry-title
         ords-info :ords-info
         ords :ords} (:_source elastic-result)]
    {:name (or granule-ur entry-title)
     :shapes (srl/ords-info->shapes ords-info ords)}))

(defn granule-elastic-result->query-result-item
  [orbits-by-collection elastic-result]
  (let [{granule-ur :granule-ur
         entry-title :entry-title
         ords-info :ords-info
         ords :ords} (:_source elastic-result)
        shapes (concat (srl/ords-info->shapes ords-info ords)
                       (orbit-swath-helper/elastic-result->swath-shapes
                         orbits-by-collection elastic-result))]
    {:name (or granule-ur entry-title)
     :shapes shapes}))

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
    (r/map->Results {:hits hits :timed-out timed-out :items items :result-format (:result-format query)})))

(doseq [concept-type [:granule :collection]]
  (defmethod er-to-qr/elastic-results->query-results [concept-type :kml]
    [context query elastic-results]
    (elastic-results->query-results context query elastic-results)))

(defn- item->kml-placemarks
  "Converts a single item into KML placemarks xml element. Most of the time an item will become a single
  placemark. In the event an item has both geodetic areas and cartesian as with geodetic polygons and
  bounding boxes it will be written as two placemarks"
  [item]
  (if-let [shapes (seq (:shapes item))]
    (let [shapes-by-coord-sys (group-by #(or (relations/coordinate-system %) :geodetic) (:shapes item))
          multiple-placemarks? (> (count shapes-by-coord-sys) 1)]
      (for [[coordinate-system shapes] shapes-by-coord-sys]
        (x/element :Placemark {}
                   (x/element :name {} (if multiple-placemarks?
                                         (str (:name item) "_" (name coordinate-system))
                                         (:name item)))
                   (x/element :styleUrl {} (kml/coordinate-system->style-url coordinate-system))
                   (if (> (count shapes) 1)
                     (x/element :MultiGeometry {}
                                (map kml/shape->xml-element shapes))
                     (kml/shape->xml-element (first shapes))))))
    [(x/element :Placemark {}
                (x/element :name {} (:name item))
                (x/xml-comment "No spatial area"))]))

(defn- search-results->response
  [context query results]
  (x/emit-str
    (x/element :kml kml/KML_XML_NAMESPACE_ATTRIBUTES
               (x/element :Document {}
                          kml/kml-geodetic-style-xml-elem
                          kml/kml-cartesian-style-xml-elem
                          (mapcat item->kml-placemarks (:items results))))))

(defmethod qs/search-results->response [:collection :kml]
  [context query results]
  (search-results->response context query results))

(defmethod qs/search-results->response [:granule :kml]
  [context query results]
  (search-results->response context query results))
