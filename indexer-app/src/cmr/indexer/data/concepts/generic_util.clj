(ns cmr.indexer.data.concepts.generic-util
  "Contains functions to parse and convert Generic Documents (that is a document
   complying to a schema supported by the Generic Document system) to and object
   that can be indexed in lucine."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.log :refer (debug info warn error)]))

(defn only-elastic-preferences
  "Go through all the index configurations and return only the ones related to 
   generating elastic values. If an index does not specify what type it is for,
   then assume elastic"
  [list-of-indexs]
  (keep #(if (not (nil? %)) %)
        (map
         (fn [x] (when (or (nil? (:Type x)) (= "elastic" (:Type x))) x))
         list-of-indexs)))
(defn jq->list
  "Convert a jq (unix command) style path to a list which can be used by get in
   to drill down into a nested set of maps.
   .Level1.Level2[1].Level3 -> [:Level1 :Level2 1 :Level3]"
  ([jq-path] (jq->list jq-path str))
  ([jq-path namer]
   (into [] (map (fn
                   [value]
                   (if (every? #(Character/isDigit %) value)
                     (Integer/parseInt value)
                     (namer value)))
                 (-> jq-path
                     (string/replace #"^\." "")
                     (string/replace #"\[(\d+)\]" ".$1")
                     (string/split #"\."))))))