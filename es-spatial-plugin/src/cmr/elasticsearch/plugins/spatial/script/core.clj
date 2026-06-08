(ns cmr.elasticsearch.plugins.spatial.script.core
  (:require
   [cmr.common.util :as u]
   [cmr.spatial.serialize :as srl])
  (:import
    (java.util Map)
    (java.util.function Supplier)
    (org.apache.logging.log4j LogManager)
    (org.elasticsearch.script DocReader
                              LeafReaderContextSupplier)
    (org.elasticsearch.search.lookup LeafSearchLookup
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
   :init init
   :state data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         Begin script helper functions                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn extract-source-values
  "Extracts arrays from _source natively to bypass Java 17 reflection blocks."
  [source field-name expected-type]

  ;; NOTICE: (get ...) instead of (.get ...). No dots!
  (let [val (get source field-name)]
    (cond
      (nil? val) nil

      (instance? java.util.List val)
      (case expected-type
        :int    (mapv int val)
        :long   (mapv long val)
        :float  (mapv float val)
        :double (mapv double val)
        :string (mapv str val)
        :bool   (vec val)
        (vec val))

      :else
      (case expected-type
        :int    [(int val)]
        :long   [(long val)]
        :float  [(float val)]
        :double [(double val)]
        :string [(str val)]
        :bool   [val]
        [val]))))

(defn doc-intersects?
  [source intersects-fn]
  (let [ords-info (extract-source-values source "ords-info" :int)
        ords      (extract-source-values source "ords" :int)]
    ;; Only proceed if BOTH arrays exist and are not empty
    (if (and (seq ords-info) (seq ords))
      (let [shapes (srl/ords-info->shapes ords-info ords)]
        (try
          (if (u/any-true? intersects-fn shapes)
            true
            false)
          (catch Throwable t
            (.error (org.apache.logging.log4j.LogManager/getLogger "cmr_spatial_script") t)
            (throw (ex-info "An exception occurred checking for intersections" {:shapes shapes} t)))))
      ;; If either is missing or empty, safely return false
      false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script helper functions                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         Begin script functions                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import 'cmr.elasticsearch.plugins.SpatialScript)

;; Need to override setDocument for more control over lookup
(defn -setDocument
  [^SpatialScript this doc-id]
  (-> this .data :search-lookup (.setDocument doc-id)))

(defn -getSource
  [this]
  (let [lookup (-> this .data :search-lookup (.source))
        ;; 1. Unwrap the Lambda FIRST
        source-obj (if (instance? java.util.function.Supplier lookup)
                     (.get ^java.util.function.Supplier lookup)
                     lookup)]
    ;; 2. Now that it is unwrapped, get the raw Java Map
    (.source source-obj)))

(defn- -init [^Object intersects-fn ^Map params ^SearchLookup lookup ^DocReader doc-reader]
  (let [context (when (instance? LeafReaderContextSupplier doc-reader)
                  (.getLeafReaderContext ^LeafReaderContextSupplier doc-reader))]
    [[params lookup doc-reader] {:intersects-fn intersects-fn
                                 :search-lookup (.getLeafSearchLookup lookup context)}]))
(defn -execute [^SpatialScript this]
  (doc-intersects? (-getSource this)
                   (-> this .data :intersects-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script functions                              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
