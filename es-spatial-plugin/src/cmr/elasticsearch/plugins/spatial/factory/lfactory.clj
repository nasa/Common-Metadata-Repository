(ns cmr.elasticsearch.plugins.spatial.factory.lfactory
  (:import
   (cmr.spatial.mbr Mbr)
   (cmr.spatial.polygon Polygon)
   (cmr.elasticsearch.plugins SpatialScript)
   (java.util Map)
   (org.apache.logging.log4j LogManager)
   (org.elasticsearch.script DocReader)
   (org.elasticsearch.common.xcontent.support XContentMapValues)
   (org.elasticsearch.search.lookup SearchLookup))
  (:require
   [clojure.string :as string]
   [cmr.spatial.relations :as relations]
   [cmr.spatial.s2geometry.cells :as s2-cells]
   [cmr.spatial.serialize :as srl])
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScriptLeafFactory
   :implements [org.elasticsearch.script.FilterScript$LeafFactory]
   :constructors {[java.util.Map org.elasticsearch.search.lookup.SearchLookup] []}
   :init init
   :state data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                    Begin factory helper functions                         ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def parameters
  "The parameters to the Spatial script"
  [:ords :ords-info :use-s2])

(defn- extract-params
  "Extracts the parameters from the params map given in the script."
  [script-params]
  (when script-params
    (into {} (for [param parameters]
               [param (XContentMapValues/nodeStringValue
                        (get script-params (name param)) nil)]))))

(defn- assert-required-parameters
  "Asserts that all the parameters are supplied or it throws an exception."
  [params]
  (when-not (every? params parameters)
    (throw (IllegalArgumentException.
             (str "Missing one or more of required parameters: "
                  (clojure.string/join parameters ", "))))))

(defn- convert-params
  "Convert the comma separated string into a vector of integers."
  [[k v]]
  [k (map #(Integer. ^String %) (string/split v #","))])

(defn- params->spatial-shape
  [params]
  (let [{:keys [ords-info ords]} (->> params
                                      (map convert-params)
                                      (into {}))]
    (first (srl/ords-info->shapes ords-info ords))))

(defn get-intersects-fn [script-params]
  (let [params (extract-params script-params)
        shape (params->spatial-shape params)]
    (assert-required-parameters params)
    (try
      (relations/shape->intersects-fn shape)
      (catch Exception e
        (.error (LogManager/getLogger "cmr_spatial_lfactory") (format "Unable to create intersects function for shapes [%s]" (pr-str shape)) e)
        (throw (ex-info "An exception occurred creating intersects-fn" {:shape shape :params params} e))))))

(defmulti get-s2-shape class)

(defmethod get-s2-shape :default
  [shape]
  (throw (ex-info (format "get-s2-shape Unsupported shape type [%s]" (class shape)) {:shape shape})))

(defmethod get-s2-shape Polygon
  [shape]
  (s2-cells/shape->s2polygon shape))

(defmethod get-s2-shape Mbr
  [shape]
  (s2-cells/shape->s2latlngrect shape))

(defn get-query-shape
  "This function will take the ords and ords-info parameters and convert them into a s2polygon."
  [script-params use-s2]
  ;; if use-s2 is false return nothing
  (when (= use-s2 "true")
    (let [params (extract-params script-params)
          shape (params->spatial-shape params)]
      (assert-required-parameters params)
      (try
        (get-s2-shape shape)
        (catch Exception e
          (.error (LogManager/getLogger "cmr_spatial_lfactory") (format "Unable to create query polygon for shapes [%s]" (pr-str shape)) e)
          (throw (ex-info "An exception occurred creating query polygon" {:shape shape :params params} e)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                    End factory helper functions                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                    Begin leaf factory functions                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import 'cmr.elasticsearch.plugins.SpatialScriptLeafFactory)

(defn- -init [^Map params ^SearchLookup lookup]
  [[] {:params params
       :lookup lookup
       :use-s2 (get params "use-s2" "false")
       :query-shape (get-query-shape params (get params "use-s2" "false"))}])

(defn -newInstance [^SpatialScriptLeafFactory this ^DocReader doc-reader]
  (let [^Map params (-> this .data :params)]
    (SpatialScript.
     ^Object (get-intersects-fn params)
     ^Map params
     ^SearchLookup (-> this .data :lookup)
     ^DocReader doc-reader
     ^Object (-> this .data :query-shape)
     ^String (-> this .data :use-s2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                    End leaf factory functions                             ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
