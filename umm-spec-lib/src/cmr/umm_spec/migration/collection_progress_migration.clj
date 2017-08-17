(ns cmr.umm-spec.migration.collection-progress-migration
  "Contains helper functions for migrating between version v1.9 and v1.10 of UMM CollectionProgress")

(def mapping-up
  {"COMPLETE" "COMPLETE"
   "IN WORK"  "ACTIVE"
   "PLANNED" "PLANNED"
   "NOT APPLICABLE" "NOT APPLICABLE"
   "NOT PROVIDED" "NOT PROVIDED"})

(def mapping-down 
  {"COMPLETE" "COMPLETE"
   "ACTIVE"  "IN WORK"
   "PLANNED" "PLANNED"
   "NOT APPLICABLE" "NOT APPLICABLE"
   "NOT PROVIDED" "NOT PROVIDED"} )

(defn migrate-up
  "Migrate CollectionProgress from string to enum."
  [c]
  (assoc c :CollectionProgress
           (get mapping-up (:CollectionProgress c) "NOT PROVIDED")))

(defn migrate-down
  "Migrate CollectionProgress from enum to string."
  [c]
  (assoc c :CollectionProgress
           (get mapping-down (:CollectionProgress c))))
