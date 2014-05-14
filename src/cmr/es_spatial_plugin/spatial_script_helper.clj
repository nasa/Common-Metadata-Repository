(ns cmr.es-spatial-plugin.spatial-script-helper
  (:require [cmr.spatial.ring :as ring]
            [cmr.spatial.serialize :as srl]
            [clojure.string :as s])
  (:import org.elasticsearch.index.fielddata.ScriptDocValues$Doubles
           org.elasticsearch.search.lookup.DocLookup
           org.elasticsearch.search.lookup.FieldsLookup
           org.elasticsearch.search.lookup.FieldLookup
           org.elasticsearch.common.logging.ESLogger))

(defn- get-ords-in-fields
  "Gets the ordinates from a fields lookup."
  [^FieldsLookup lookup]
  (let [^FieldLookup field-lookup (.get lookup "ords")]
    (.getValues field-lookup)))

(defn doc-intersects?
  "Returns true if the doc contains a ring that intersects the ring passed in."
  [^ESLogger logger ^FieldsLookup lookup intersects-fn]
  ; Must explicitly return true or false or elastic search will complain

  ;; TODO idea for performance improvement. We could make the ring lazy.
  ;; Let's say that the first arc in the ring would result in an intersection with the original ring
  ;; We would only have to create one arc in that case. We wouldn't have to calculate the great
  ;; circle for any of the other arcs
  (if-let [ords (get-ords-in-fields lookup)]
    ;; Hardcoded the type to polygon for now
    (let [polygon (srl/stored-ords->shape :polygon ords)]
      (try
        (if (intersects-fn polygon)
          true
          false)
        (catch Throwable t
           (.error logger (s/join "\n" (map #(.toString ^String %) (.getStackTrace t))) nil)
           (.info logger (pr-str ords) nil)
           (.info logger (pr-str polygon) nil)
           (throw t))))
    false))
