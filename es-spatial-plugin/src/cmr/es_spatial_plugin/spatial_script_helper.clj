(ns cmr.es-spatial-plugin.spatial-script-helper
  (:require [cmr.spatial.serialize :as srl]
            [clojure.string :as s])
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

  ;; Performance enhancement: We could make the ring lazy.
  ;; Let's say that the first arc in the ring would result in an intersection with the original ring
  ;; We would only have to create one arc in that case. We wouldn't have to calculate the great
  ;; circle for any of the other arcs

  (if-let [ords-info (get-from-fields lookup "ords-info")]
    (let [ords (get-from-fields lookup "ords")
          shapes (srl/ords-info->shapes ords-info ords)]
      (try
        (if (some intersects-fn shapes)
          true
          false)
        (catch Throwable t
          (.printStackTrace t)
          (.error logger (s/join "\n" (map #(.toString ^Object %) (.getStackTrace t))) nil)
          (.info logger (pr-str ords-info) nil)
          (.info logger (pr-str ords) nil)
          (throw t))))
    false))
