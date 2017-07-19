(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.processing-level
  "Functions for generating ISO-19115 and ISO-SMAP XML elements from UMM processing level element."
  (:require
    [cmr.umm-spec.util :as su :refer [char-string]]))

(def processing-level-code-space-string "gov.nasa.esdis.umm.processinglevelid")

(defn generate-iso-processing-level
  "Generate the processing level ISO sub-elements."
  [processing-level]
  [:gmd:MD_Identifier
    [:gmd:code (char-string (:Id processing-level))]
    [:gmd:codeSpace (char-string processing-level-code-space-string)]
    [:gmd:description (char-string (:ProcessingLevelDescription processing-level))]])
