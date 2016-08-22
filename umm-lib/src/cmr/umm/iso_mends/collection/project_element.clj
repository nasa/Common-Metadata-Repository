(ns cmr.umm.iso-mends.collection.project-element
  "Contains functions for parsing and generating the ISO MENDS elements related to UMM project"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [clojure.string :as s]
            [cmr.umm.iso-mends.collection.keyword :as k]
            [cmr.umm.iso-mends.collection.helper :as h]))

(defn xml-elem->Project
  [project-elem]
  (let [short-name (cx/string-at-path project-elem
                                      [:identifier :MD_Identifier :code :CharacterString])
        description (cx/string-at-path project-elem [:description :CharacterString])
        ;; ISO description is built as "short-name > long-name", so here we extract the long-name out
        long-name (when-not (= short-name description)
                    (s/replace description (str short-name " > ") ""))]
    (c/map->Project
      {:short-name short-name
       :long-name long-name})))

(defn xml-elem->Projects
  [collection-element]
  (let [projects (map xml-elem->Project
                      (cx/elements-at-path
                        collection-element
                        [:acquisitionInformation :MI_AcquisitionInformation :operation :MI_Operation]))]
    (when (not (empty? projects))
      projects)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-projects
  [projects]
  (for [proj projects]
    (let [{:keys [short-name long-name]} proj
          description (if (empty? long-name) short-name (str short-name " > " long-name))]
      (x/element :gmi:operation {}
                 (x/element :gmi:MI_Operation {}
                            (h/iso-string-element :gmi:description description)
                            (x/element :gmi:identifier {}
                                       (x/element :gmd:MD_Identifier {}
                                                  (h/iso-string-element :gmd:code short-name)))
                            (x/element :gmi:status {})
                            (x/element :gmi:parentOperation {:gco:nilReason "inapplicable"}))))))

(defn- project->keyword
  "Returns the ISO keyword for the given project"
  [project]
  (let [{:keys [short-name long-name]} project]
    (if (empty? long-name)
      short-name
      (str short-name " > " long-name))))

(defn generate-project-keywords
  [projects]
  (let [keywords (map project->keyword projects)]
    (k/generate-keywords "project" keywords)))



