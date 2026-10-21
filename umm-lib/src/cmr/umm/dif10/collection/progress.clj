(ns cmr.umm.dif10.collection.progress
  "Functions for parsing and generating the UMM Collection Progress
  information from and to DIF 10 XML."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [cmr.common.xml :as cx]))

(def generate-state-map
  {:planned  "PLANNED"
   :in-work  "IN WORK"
   :complete "COMPLETE"})

(def parse-state-map
  {"PLANNED"  :planned
   "IN WORK"  :in-work
   "ACTIVE"   :in-work
   "COMPLETE" :complete})

(defn parse
  "Returns a collection progress value from a parsed DIF XML document
  structure."
  [xml-struct]
  (when-let [prog-str (cx/string-at-path xml-struct [:Dataset_Progress])]
    (get parse-state-map (string/upper-case prog-str))))

(defn generate
  "Returns DIF 10 XML element structures for the collection's progress
  value."
  [collection]
  (when-let [state (:collection-progress collection)]
    (xml/element :Dataset_Progress {} (generate-state-map state))))
