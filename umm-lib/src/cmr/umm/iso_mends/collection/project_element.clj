(ns cmr.umm.iso-mends.collection.project-element
  "Contains functions for parsing and generating the ISO MENDS elements related to UMM project"
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]
   [clojure.string :as string]
   [cmr.umm.iso-mends.collection.keyword :as k-word]
   [cmr.umm.iso-mends.collection.helper :as helper]))

(defn xml-elem->Project
  [project-elem]
  (let [short-name (cx/string-at-path project-elem
                                      [:identifier :MD_Identifier :code :CharacterString])
        description (cx/string-at-path project-elem [:description :CharacterString])
        ;; ISO description is built as "short-name > long-name", so here we extract the long-name out
        long-name (when-not (= short-name description)
                    (string/replace description (str short-name " > ") ""))]
    (coll/map->Project
      {:short-name short-name
       :long-name long-name})))

(defn xml-elem->Projects
  [collection-element]
  (let [projects (map xml-elem->Project
                      (cx/elements-at-path
                        collection-element
                        [:acquisitionInformation :MI_AcquisitionInformation :operation :MI_Operation]))]
    (when (seq projects)
      projects)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-projects
  [projects]
  (for [proj projects]
    (let [{:keys [short-name long-name]} proj
          description (if (empty? long-name) short-name (str short-name " > " long-name))]
      (xml/element :gmi:operation {}
                   (xml/element :gmi:MI_Operation {}
                                (helper/iso-string-element :gmi:description description)
                                (xml/element :gmi:identifier {}
                                             (xml/element :gmd:MD_Identifier {}
                                                          (helper/iso-string-element :gmd:code short-name)))
                                (xml/element :gmi:status {})
                                (xml/element :gmi:parentOperation {:gco:nilReason "inapplicable"}))))))

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
    (k-word/generate-keywords "project" keywords)))



