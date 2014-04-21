(ns cmr.system-int-test.data.granule-helper
  "Provides function to generate UMM granule with generated values and use the given values if provided."
  (:require [cmr.umm.test.generators.granule :as gran-gen]
            [clojure.test.check.generators :as gen]))

(defn- fill-in-value
  "Fill the value if present in the given field of the granule, returns the granule"
  [granule field value]
  (if value
    (assoc granule field value)
    granule))

(defn- fill-in-collection-value
  "Fill the value if present in the given collection field of the granule, returns the granule"
  [granule field value]
  (if value
    (assoc-in granule [:collection-ref field] value)
    granule))

(defn granule
  "Returns a generated granule with the given values,
  fields with no given values will be filled in with generated ones."
  [values]
  (let [{:keys [entry-title short-name version-id granule-ur]} values]
        (-> (first (gen/sample gran-gen/granules-entry-title 1))
            (fill-in-value :granule-ur granule-ur)
            (fill-in-collection-value :entry-title entry-title)
            (fill-in-collection-value :short-name short-name)
            (fill-in-collection-value :version-id version-id))))
