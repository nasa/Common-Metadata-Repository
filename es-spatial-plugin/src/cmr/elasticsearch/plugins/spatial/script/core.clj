(ns cmr.elasticsearch.plugins.spatial.script.core
  (:require
   [cmr.common.util :as u]
   [cmr.spatial.serialize :as srl])
  (:import
   (java.util Map)
   (org.elasticsearch.script DocReader) 
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
   :methods [[getFields [] org.elasticsearch.search.lookup.LeafStoredFieldsLookup]]
   :init init
   :state data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         Begin script helper functions                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-from-fields
  [^LeafStoredFieldsLookup lookup key]
  (when (and lookup key (.containsKey lookup key))
    (when-let [^FieldLookup field-lookup (.get lookup key)]
      (seq (.getValues field-lookup)))))

(defn doc-intersects?
  "Returns true if the doc contains a ring that intersects the ring passed in."
  [^LeafStoredFieldsLookup lookup intersects-fn]
  ;; Must explicitly return true or false or elastic search will complain
  (if-let [ords-info (get-from-fields lookup "ords-info")]
    (let [ords (get-from-fields lookup "ords")
          shapes (srl/ords-info->shapes ords-info ords)]
      (try
        (if (u/any-true? intersects-fn shapes)
          true
          false)
        (catch Throwable t
          (.printStackTrace t)
          (throw t))))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script helper functions                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         Begin script functions                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import 'cmr.elasticsearch.plugins.SpatialScript)

(defn ^LeafStoredFieldsLookup -getFields
  [^SpatialScript this]
  (-> this .data :search-lookup .fields))

(defn ^Map -getDoc
  [^SpatialScript this]
  (-> this .data :search-lookup .doc))

;; Need to override setDocument for more control over lookup
(defn -setDocument
  [^SpatialScript this doc-id]
  (-> this .data :search-lookup (.setDocument doc-id)))

(defn- -init [^Object intersects-fn ^Map params ^SearchLookup lookup ^DocReader doc-reader]
  [[params lookup doc-reader] {:intersects-fn intersects-fn
                               :search-lookup (.getLeafSearchLookup lookup (.getLeafReaderContext doc-reader))}])

(defn -execute [^SpatialScript this]
  (doc-intersects? (.getFields this)
                   (-> this .data :intersects-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script functions                              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
