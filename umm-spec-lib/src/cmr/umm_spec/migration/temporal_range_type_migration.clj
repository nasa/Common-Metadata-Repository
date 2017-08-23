(ns cmr.umm-spec.migration.temporal-range-type-migration
  "Contains helper functions for migrating between different versions of UMM temporal range type."
  (:require
   [cmr.common.util :as util :refer [update-in-each]]))

(defn migrate-up
  "Migrate temporal range type from existing to non-existing."
  [c]
  (update-in-each c [:TemporalExtents] dissoc :TemporalRangeType)) 
