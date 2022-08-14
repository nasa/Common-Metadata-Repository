(ns cmr.elasticsearch.plugins.spatial.script.core
  (:require
   [cmr.common.util :as u]
   [cmr.spatial.serialize :as srl])
  (:import
   (java.util Map)
   (org.apache.logging.log4j Logger LogManager)
   (org.elasticsearch.script DocReader)
   (org.elasticsearch.common.xcontent.support XContentMapValues)
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

(def parameters
  "The parameters to the Spatial script"
  [:ords :ords-info :operator])

(defn- extract-params
  "Extracts the parameters from the params map given in the script."
  [script-params]
  (when script-params
    (into {} (for [param parameters]
               [param (XContentMapValues/nodeStringValue
                        (get script-params (name param)) nil)]))))

(defn- get-from-fields
  [^LeafStoredFieldsLookup lookup key]
  (when (and lookup key (.containsKey lookup key))
    (when-let [^FieldLookup field-lookup (.get lookup key)]
      (seq (.getValues field-lookup)))))

(defn remove-br
  "Removes bounding rectangles from granule spatial data."
  [shapes]
  (let [shapes-no-br (into [] (remove #(instance? cmr.spatial.mbr.Mbr %) shapes))]
    shapes-no-br))

(defn doc-intersects?
  "Returns true if the doc contains a ring that intersects the ring passed in."
  [^LeafStoredFieldsLookup lookup params intersects-fn]
  ;; Must explicitly return true or false or elastic search will complain
  (if-let [ords-info (get-from-fields lookup "ords-info")]
    (let [ords (get-from-fields lookup "ords")
          shapes (srl/ords-info->shapes ords-info ords)
          shapes-no-br (remove-br shapes)
          op (:operator (extract-params params))]
      ;; Example for logging to Docker logs in es-spatial-plugin
      ;;(.info (LogManager/getLogger) (str "OPERATOR:" op))
      (try
        (case op
          "every" (every? intersects-fn shapes)
          "ignore_br" (some intersects-fn shapes-no-br)
          (some intersects-fn shapes))
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
                               :params params
                               :search-lookup (.getLeafSearchLookup lookup (.getLeafReaderContext doc-reader))}])

(defn -execute [^SpatialScript this]
  (doc-intersects? (.getFields this)
                   (-> this .data :params)
                   (-> this .data :intersects-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script functions                              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
