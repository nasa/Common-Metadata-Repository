(ns cmr.indexer.data.concepts.generic-util
  "Contains functions to parse and convert Generic Documents (that is a document
   complying to a schema supported by the Generic Document system) to and object
   that can be indexed in lucine.")

(defn only-elastic-preferences
  "Go through all the index configurations and return only the ones related to
   generating elastic values. If an index does not specify what type it is for,
   then assume elastic"
  [list-of-indexes]
  (keep #(when (not (nil? %)) %)
        (map
         (fn [x] (when (or (nil? (:Type x)) (= "elastic" (:Type x))) x))
         list-of-indexes)))
