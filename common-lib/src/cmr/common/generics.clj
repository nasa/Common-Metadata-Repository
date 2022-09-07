(ns cmr.common.generics
  "Defines utilities for new generic document pipeline"
  (:require 
   [cmr.common.config :as cfg]))

(defn approved-generic?
  "Check to see if a requested generic is on the approved list"
  [schema version]
  (when (and schema version)
    (some #(= version %) (schema (cfg/approved-pipeline-documents)))))
