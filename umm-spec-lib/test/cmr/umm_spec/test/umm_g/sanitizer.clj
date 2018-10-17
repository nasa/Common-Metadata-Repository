(ns cmr.umm-spec.test.umm-g.sanitizer
  "Namespace containing functions for sanitizing generated umm-lib granule records
   used in testing."
  (:require [cmr.common.util :as util]))

(defn- sanitize-operation-modes
  "Sanitizer for operation-modes, if sequence it removes duplicates, if nil it inserts a not provided."
  [operation-modes]
  (when (seq operation-modes)
    (distinct operation-modes)))

(defn sanitize-granule
  "Sanitizes umm-lib generated granule."
  [umm]
  (-> umm
      (update :project-refs (fn [x] (when (seq x) (distinct x))))
      (update :platform-refs (fn [x] (when (seq x) (distinct x))))
      (util/update-in-each
       [:platform-refs]
       #(util/update-in-each
         % [:instrument-refs] update :operation-modes sanitize-operation-modes))))
