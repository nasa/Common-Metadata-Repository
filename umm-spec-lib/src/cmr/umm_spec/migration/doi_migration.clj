(ns cmr.umm-spec.migration.doi-migration
  "Contains helper functions for migrating between different versions of UMM DOI"
  (:require
   [cmr.common.util :as util :refer [update-in-each]]))

(defn migrate-doi-up
  "Migrate :DOI from CollectionCitation level up to collection level."
  [c]
  (if-let [doi-obj (some :DOI (:CollectionCitations c))]
    (-> c
      (update-in-each [:CollectionCitations] dissoc :DOI)
      (assoc :DOI doi-obj))
    c))

(defn migrate-doi-down
  "Migrate :DOI from collection level down to CollectionCitation level."
  [c]
  (if-let [doi-obj (:DOI c)]
    (-> c
      (update-in-each [:CollectionCitations] assoc :DOI doi-obj)
      (dissoc :DOI))
    c))

(defn migrate-missing-reason
  "Migrate nil :DOI to have MissingReason."
  [c]
  (when-not (seq (:DOI c))
    (assoc c :DOI {:MissingReason "Not Applicable"})))
