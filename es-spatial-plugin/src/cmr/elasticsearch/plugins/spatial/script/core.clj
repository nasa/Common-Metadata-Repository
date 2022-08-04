(ns cmr.elasticsearch.plugins.spatial.script.core
  (:require
   [cmr.common.util :as u]
   [cmr.spatial.serialize :as srl])
  (:import
   (java.util Map)
   (org.elasticsearch.script DocReader)
   (org.apache.logging.log4j Logger LogManager)
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

(defn any-true?
  "Returns true if predicate f returns a truthy value against any of the items.
  This is very similar to some but it's faster through it's use of reduce."
  [f items]
  (reduce (fn [_ i]
            (if (f i)
              (reduced true) ;; short circuit
              false))
          false
          items))

(defn every-true?
  "Returns true if predicate f returns a truthy value against all of the items."
  [f items]
  (every? true?
    (map f items)))

(defn remove-br
  "Removes bounding rectangles from granule spatial data."
  [shapes]
  (let [shapes-no-br (into [] (remove nil? (for [shape shapes]
                                             (if (not (= 5 (count shape)))
                                               shape
                                               nil))))]
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
      (try
        (case op
          "every" (every-true? intersects-fn shapes-no-br)
          "ignore-br" (any-true? intersects-fn shapes-no-br)
          (any-true? intersects-fn shapes))
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
  ;(.info (LogManager/getLogger) (str "====PARAMS" params))
  [[params lookup doc-reader] {:intersects-fn intersects-fn
                               ;:operator (:operator (extract-params params))
                               :params params
                               :search-lookup (.getLeafSearchLookup lookup (.getLeafReaderContext doc-reader))}])

(defn -execute [^SpatialScript this]
  ;(.info (LogManager/getLogger) (str "====THIS" (-> this .data)))
  (doc-intersects? (.getFields this)
                   (-> this .data :params)
                   (-> this .data :intersects-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script functions                              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
