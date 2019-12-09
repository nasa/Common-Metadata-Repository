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

(defn migrate-missing-reason-up
  "Migrate nil :DOI to have MissingReason up."
  [c]
  (if (seq (:DOI c))
    c
    (assoc c :DOI {:MissingReason "Not Applicable"})))

(defn migrate-missing-reason-down
  "Migrate nil :DOI to have MissingReason down."
  [c]
  (if (get-in c [:DOI :DOI])
    (update c :DOI dissoc :MissingReason :Explanation)
    (assoc c :DOI nil)))

(defn migrate-doi-down-to-1_10
  "Removes DOI if a MissingReason or Explanation is found"
  [c]
  (if (or (get-in c [:DOI :Explanation])
          (get-in c [:DOI :MissingReason]))
    (dissoc c :DOI)
    c))
