(ns cmr.elastic-utils.generics
  "A set of functions for dealing with Generics and Elastic")

(defn only-elastic-preferences
  "Go through all the index configurations and return only the ones related to
   generating elastic values. If an index does not specify what type it is for,
   then assume elastic"
  [list-of-indexes]
  (keep #(if (not (nil? %)) %)
        (map
         (fn [x] (when (or (nil? (:Type x)) (= "elastic" (:Type x))) x))
         list-of-indexes)))
