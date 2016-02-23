(ns cmr.umm.dif.collection.progress
  "Functions for parsing and generating the UMM Collection Progress
  information from and to DIF XML."
  (:require [clojure.data.xml :as x]
            [clojure.string :as string]
            [cmr.common.xml :as cx]))

(defn parse
  "Returns a collection progress value from a parsed DIF XML document
  structure."
  [xml-struct]
  (when-let [prog-str (cx/string-at-path xml-struct [:Data_Set_Progress])]
    (condp = (string/lower-case prog-str)
      "planned"  :planned
      "in work"  :in-work
      "complete" :complete
      "completed" :complete
      :in-work)))

(defn generate
  "Returns DIF XML element structures for the collection's progress
  value."
  [{:keys [collection-progress]}]
  (when collection-progress
    (x/element :Data_Set_Progress {}
               (-> collection-progress
                   name
                   (string/replace "-" " ")
                   string/upper-case))))
