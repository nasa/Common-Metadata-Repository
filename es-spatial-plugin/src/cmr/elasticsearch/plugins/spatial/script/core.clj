(ns cmr.elasticsearch.plugins.spatial.script.core
  (:require
   [cmr.common.util :as u]
   [cmr.spatial.serialize :as srl])
  (:import
   (java.util Map)
   (org.apache.logging.log4j LogManager)
   (org.elasticsearch.script DocReader
                             LeafReaderContextSupplier)
   (org.elasticsearch.search.lookup FieldLookup
                                    LeafDocLookup
                                    LeafStoredFieldsLookup
                                    LeafSearchLookup
                                    SearchLookup))
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScript
   :extends org.elasticsearch.script.FilterScript
   :constructors {[java.lang.Object
                   java.util.Map
                   org.elasticsearch.search.lookup.SearchLookup
                   org.elasticsearch.script.DocReader]
                  [java.util.Map
                   org.elasticsearch.search.lookup.SearchLookup
                   org.elasticsearch.script.DocReader]}
   :methods [[getFields [] org.elasticsearch.search.lookup.LeafStoredFieldsLookup]
             [getDoc [] org.elasticsearch.search.lookup.LeafDocLookup]]
   :init init
   :state data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         Begin script helper functions                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-from-fields
  [^LeafStoredFieldsLookup lookup key]
  (when (and lookup key)
    (when-let [^FieldLookup field-lookup (.get lookup key)]
      (seq (.getValues field-lookup)))))

(defn extract-doc-values
  "Safely extracts and coerces values from a LeafDocLookup field.
  NOTE: doc-values for every field must be set to 'true' which is default."
  [^LeafDocLookup doc field-name expected-type]
  (let [doc-values (.get doc field-name)]
    (when (and doc-values (pos? (.size doc-values)))
      (let [vals (seq doc-values)]
        (case expected-type
          :int    (mapv int vals)
          :long   (vec vals)
          :float  (mapv float vals)
          :double (vec vals)
          :bool   (vec vals)
          :string (mapv str vals)
          ;; Example for Dates (converting to epoch milliseconds)
          :date   (mapv #(.toInstant % ) vals) ;; or .toEpochMilli depending on your needs
          ;; Example for GeoPoints (extracting lat/lon as maps or vectors)
          :geo    (mapv (fn [gp] [(.getLon gp) (.getLat gp)]) vals)
          (vec vals))))))

(defn doc-intersects?
  "Returns true if the doc contains a ring that intersects the ring passed in."
  [^LeafDocLookup doc intersects-fn] ;; <-- 1. Change type hint to LeafDocLookup

  ;; 2. Use extract-doc-values to safely get the ints from ES 8 ScriptDocValues
  (if-let [ords-info (extract-doc-values doc "ords-info" :int)]
    (let [ords (extract-doc-values doc "ords" :int)
          shapes (srl/ords-info->shapes ords-info ords)]
      (try
        ;; Must explicitly return true or false or elastic search will complain
        (if (u/any-true? intersects-fn shapes)
          true
          false)
        (catch Throwable t
          (.error (LogManager/getLogger "cmr_spatial_script") t)
          (throw (ex-info "An exception occurred checking for intersections" {:shapes shapes} t)))))
    false))

;(defn doc-intersects?
;  "Returns true if the doc contains a ring that intersects the ring passed in."
;  [^LeafStoredFieldsLookup lookup intersects-fn]
;
;  (if-let [ords-info (get-from-fields lookup "ords-info")]
;    (let [ords (get-from-fields lookup "ords")
;          shapes (srl/ords-info->shapes ords-info ords)]
;      (try
;        ;; Must explicitly return true or false or elastic search will complain
;        (if (u/any-true? intersects-fn shapes)
;          true
;          false)
;        (catch Throwable t
;          (.error (LogManager/getLogger "cmr_spatial_script") t)
;          (throw (ex-info "An exception occurred checking for intersections" {:shapes shapes} t)))))
;    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script helper functions                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         Begin script functions                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import 'cmr.elasticsearch.plugins.SpatialScript)

(defn ^LeafStoredFieldsLookup -getFields
  [^SpatialScript this]
  (-> this .data :search-lookup (.fields)))

(defn ^LeafDocLookup -getDoc
  [^SpatialScript this]
  (-> this .data :search-lookup (.doc)))

;; Need to override setDocument for more control over lookup
(defn -setDocument
  [^SpatialScript this doc-id]
  (-> this .data :search-lookup (.setDocument doc-id)))

(defn- -init [^Object intersects-fn ^Map params ^SearchLookup lookup ^DocReader doc-reader]
  (let [context (when (instance? LeafReaderContextSupplier doc-reader)
                  (.getLeafReaderContext ^LeafReaderContextSupplier doc-reader))]
    [[params lookup doc-reader] {:intersects-fn intersects-fn
                                 :search-lookup (.getLeafSearchLookup lookup context)}]))

;(defn -execute [^SpatialScript this]
;  (doc-intersects? (.getFields this)
;                   (-> this .data :intersects-fn)))

(defn -execute [^SpatialScript this]
  ;; Pass the LeafDocLookup (doc values) instead of StoredFields
  ;; NOTE: if the doc-values field = false, this will break, but it should be set to true by default.
  ;; NOTE: if an elastic field for spatial is ever "type":"text" this will break because text does not have doc-value,
  ;; but this should never happen because text is not a type often or should be used for spatial data
  (doc-intersects? (-getDoc this)
                   (-> this .data :intersects-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script functions                              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
