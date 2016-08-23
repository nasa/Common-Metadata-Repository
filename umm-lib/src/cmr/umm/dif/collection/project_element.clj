(ns cmr.umm.dif.collection.project-element
  "Provide functions to parse and generate DIF Project elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]))

(defn xml-elem->Project
  [project-elem]
  (let [short-name (cx/string-at-path project-elem [:Short_Name])
        long-name (cx/string-at-path project-elem [:Long_Name])]
    (c/map->Project
      {:short-name short-name
       :long-name long-name})))

(defn xml-elem->Projects
  [collection-element]
  (seq (map xml-elem->Project
            (cx/elements-at-path collection-element [:Project]))))

(defn generate-projects
  [projects]
  (when (seq projects)
    (for [proj projects]
      (let [{:keys [short-name long-name]} proj]
        (x/element :Project {}
                   (x/element :Short_Name {} short-name)
                   (when long-name
                     (x/element :Long_Name {} long-name)))))))
