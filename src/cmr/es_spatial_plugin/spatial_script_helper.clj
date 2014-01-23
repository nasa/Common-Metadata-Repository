(ns cmr.es-spatial-plugin.spatial-script-helper
  (:require [cmr-spatial.ring :as ring]
            [clojure.string :as s])
  (:import org.elasticsearch.index.fielddata.ScriptDocValues$Doubles
           org.elasticsearch.search.lookup.DocLookup
           org.elasticsearch.search.lookup.FieldsLookup
           org.elasticsearch.search.lookup.FieldLookup
           org.elasticsearch.common.logging.ESLogger))

(defn- get-ords-in-doc
  "Gets the ordinates from a DocLookup"
  [^DocLookup doc]
  (let [^ScriptDocValues$Doubles doc-value (.get doc "ords")]
    (when (and (not (nil? doc-value))
             (not (.isEmpty doc-value)))
      (.getValues doc-value))))

(defn- get-ords-in-fields
  "Gets the ordinates from a fields lookup."
  [^FieldsLookup lookup]
  (let [^FieldLookup field-lookup (.get lookup "ords")]
    (.getValues field-lookup)))

;; TODO using FieldsLookup is supposed to be much slower than indexed values.
;; The problem with DocLookup is that it doesn't return the values sorted. Another alternative
;; to save the values as a string or encode the ordinate index number within each lon lat value
;; Example lon 179.74 at index 12 = 12 * 1000 + 179.74 = 12179.74
;; Putting them back in order is a matter of calling sort then mod 1000 on each to get original values.
;; The values could also be merged into a long or byte.
;; TODO consider changing the storage type to long, integer, or short. It may decrease the size of the elastic inde


;; I found that fields lookup locally was faster initially (before everything cached) and then about the same as doc.
;; after testing again I couldn't reproduce the results.
;; TODO Will want to test that in amazon.

(defn ordinates->stored-ordinates [ords]
  (map-indexed (fn [^double ind ^double ord]
                 (let [negative (< ord 0.0)
                       value (+ (* ind 1000.0) (Math/abs ord))]
                   (if negative
                     (* value -1.0)
                     value)))
               ords))

(defn stored-ordinates->ordinates [ords]
  (map (fn [^double ord]
         (rem ord 1000.0))
       (sort-by #(Math/abs ^double %) ords)))


(defn doc-intersects?
  "Returns true if the doc contains a ring that intersects the ring passed in."
  [^ESLogger logger ^FieldsLookup lookup intersects-fn]
  ; Must explicitly return true or false or elastic search will complain

  ;; TODO idea for performance improvement. We could make the ring lazy.
  ;; Let's say that the first arc in the ring would result in an intersection with the original ring
  ;; We would only have to create one arc in that case. We wouldn't have to calculate the great
  ;; circle for any of the other arcs
  (if-let [ords (get-ords-in-fields lookup)]
    (let [ords (stored-ordinates->ordinates ords)
          ring2 (apply ring/ords->ring ords)]
      (try
        (if (intersects-fn ring2)
          true
          false)
        (catch Throwable t
           (.error logger (s/join "\n" (map #(.toString ^String %) (.getStackTrace t))) nil)
           (.info logger (pr-str ords) nil)
           (.info logger (pr-str ring2) nil)
           (throw t))))
    false))
