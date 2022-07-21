(ns cmr.common.generics
  "Defines utilities for new generic document pipeline"
  (:require
   [cmr.common-app.config :as common-config]))

(defn approved-generic?
  "Check to see if a requested generic is on the approved list"
  [schema version]
  (some #(= version %) (schema (common-config/approved-pipeline-documents))))