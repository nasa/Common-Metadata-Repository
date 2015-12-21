(ns cmr.es-spatial-plugin.spatial-script-helper
  (:require [cmr.spatial.serialize :as srl]
            [clojure.string :as s]
            [cmr.common.util :as u])
  (:import org.elasticsearch.index.fielddata.ScriptDocValues$Doubles
           org.elasticsearch.search.lookup.DocLookup
           org.elasticsearch.search.lookup.FieldsLookup
           org.elasticsearch.search.lookup.FieldLookup
           org.elasticsearch.common.logging.ESLogger))


(defn- get-from-fields
  [^FieldsLookup lookup key]
  (let [^FieldLookup field-lookup (.get lookup key)]
    (.getValues field-lookup)))

(defn doc-intersects?
  "Returns true if the doc contains a ring that intersects the ring passed in."
  [^ESLogger logger ^FieldsLookup lookup intersects-fn]
  ; Must explicitly return true or false or elastic search will complain

  (if-let [ords-info (get-from-fields lookup "ords-info")]
    (let [ords (get-from-fields lookup "ords")
          shapes (srl/ords-info->shapes ords-info ords)]
      (try
        (if (u/any? intersects-fn shapes)
          true
          false)
        (catch Throwable t
          (.printStackTrace t)
          (.error logger (s/join "\n" (map #(.toString ^Object %) (.getStackTrace t))) nil)
          (.info logger (pr-str ords-info) nil)
          (.info logger (pr-str ords) nil)
          (throw t))))
    false))


