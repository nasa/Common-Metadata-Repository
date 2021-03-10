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

(defn migrate-doi-up-to-1-16-1
  "If earlier records do not have a DOI add one with a missing reason and an explanation."
  [doi]
  (if doi
    doi
    {:MissingReason "Unknown"
     :Explanation "Native record does not contain a DOI."}))

(defn migrate-doi-down-to-1-16
  "Removes DOI if a MissingReason of Unknown is found"
  [doi]
  (when-not (= "Unknown" (get doi :MissingReason))
     doi))

(defn migrate-pub-ref-up-to-1-16-1
  "Migrate publication references from 1.16 to 1.16.1 because the missing reason was
   removed from the Publication References DOI definition."
  [pub-refs]
  (util/remove-nils-empty-maps-seqs
    (for [pub-ref pub-refs]
      (if (get-in pub-ref [:DOI :MissingReason])
        (-> pub-ref
            (dissoc :DOI)
            util/remove-nils-empty-maps-seqs)
        pub-ref))))
