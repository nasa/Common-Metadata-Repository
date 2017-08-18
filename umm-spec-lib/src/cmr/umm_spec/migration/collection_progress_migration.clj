(ns cmr.umm-spec.migration.collection-progress-migration
  "Contains helper functions for migrating between different versions of UMM collection progress"
  (:require 
   [clojure.string :as string]
   [cmr.umm-spec.util :as umm-spec-util]))

(def mapping-up
  "Defines mappings of CollectionProgress values from v1.9 to v1.10."
  {"COMPLETE" "COMPLETE"
   "IN WORK" "ACTIVE"
   "PLANNED" "PLANNED"
   "NOT APPLICABLE" "NOT APPLICABLE"
   "NOT PROVIDED" "NOT PROVIDED"})

(def mapping-down
  "Defines mappings of CollectionProgress values from v1.10 to v1.9."
  {"COMPLETE" "COMPLETE"
   "ACTIVE" "IN WORK"
   "PLANNED" "PLANNED"
   "NOT APPLICABLE" "NOT APPLICABLE"
   "NOT PROVIDED" "NOT PROVIDED"})

(defn migrate-up
  "Migrate CollectionProgress from string to enum."
  [c]
  (assoc c :CollectionProgress
           (get mapping-up
                (when-let [c-progress (:CollectionProgress c)]
                  (string/upper-case c-progress)) 
                umm-spec-util/NOT-PROVIDED))) 

(defn migrate-down
  "Migrate from enum to string."
  [c]
  (assoc c :CollectionProgress
           (get mapping-down 
                (when-let [c-progress (:CollectionProgress c)]
                  (string/upper-case c-progress)))))

