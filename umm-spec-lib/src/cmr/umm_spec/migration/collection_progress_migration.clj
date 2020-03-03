(ns cmr.umm-spec.migration.collection-progress-migration
  "Contains helper functions for migrating between different versions of UMM collection progress"
  (:require
   [clojure.set :as set]
   [cmr.common.util :as util]
   [cmr.umm-spec.util :as umm-spec-util]))

(def mapping-up
  "Defines mappings of CollectionProgress values from v1.9 to v1.10."
  {"COMPLETE" "COMPLETE"
   "IN WORK" "ACTIVE"
   "PLANNED" "PLANNED"
   "NOT APPLICABLE" "NOT APPLICABLE"
   "NOT PROVIDED" "NOT PROVIDED"})

(defn migrate-up
  "Migrate CollectionProgress from string to enum."
  [c]
  (assoc c :CollectionProgress
           (get mapping-up
                (util/safe-uppercase (:CollectionProgress c))
                umm-spec-util/NOT-PROVIDED)))

(defn migrate-down
  "Migrate from enum to string."
  [c]
  (assoc c :CollectionProgress
           (get (set/map-invert mapping-up)
                (util/safe-uppercase (:CollectionProgress c)))))

(def mapping-1_15_1-down
  "Defines mappings of CollectionProgress values from v1.15.1 to v1.15."
  {"COMPLETE" "COMPLETE"
   "ACTIVE" "ACTIVE"
   "PLANNED" "PLANNED"
   "DEPRECATED" "COMPLETE"
   "NOT APPLICABLE" "NOT APPLICABLE"
   "NOT PROVIDED" "NOT PROVIDED"})

(defn migrate-down-to-1_15
  "Migrate from 1.15.1 enumerations to 1.15 enumerations."
  [c]
  (assoc c :CollectionProgress
           (get mapping-1_15_1-down (:CollectionProgress c))))
