(ns cmr.umm.dif10.collection.project-element
  "Provide functions to parse and generate DIF10 Project elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.dif.dif-core :as dif]
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
  (if (seq projects)
    (for [{:keys [short-name long-name]} projects]
      (x/element :Project {}
                 (x/element :Short_Name {} short-name)
                 (when long-name
                   (x/element :Long_Name {} long-name))))
    ;; Added since Project is a required field in DIF10. CMRIN-78
    (x/element :Project {}
               (x/element :Short_Name {} c/not-provided))))
