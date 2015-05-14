(ns cmr.umm.echo10.collection.progress
  "Functions for parsing/generating UMM collection progress from/to
  ECHO10 XML."
  (:require [clojure.data.xml :as x]
            [clojure.string :as string]
            [cmr.common.xml :as cx]))

(def umm->echo
  {:planned "Planned"
   :in-work "In Work"
   :complete "Completed"})

(def echo->umm
  (zipmap
   (map string/lower-case (vals umm->echo))
   (keys umm->echo)))

(defn parse
  [echo-xml-doc]
  (when-let [state-str (cx/string-at-path echo-xml-doc [:CollectionState])]
    (echo->umm (string/lower-case state-str))))

(defn generate
  [collection]
  (when-let [prog (:collection-progress collection)]
    (x/element :CollectionState {} (umm->echo prog))))
