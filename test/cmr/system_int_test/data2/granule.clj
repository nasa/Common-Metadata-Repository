(ns cmr.system-int-test.data2.granule
  "Contains data generators for example based testing in system integration tests."
  (:require [cmr.umm.granule :as g]
            [cmr.system-int-test.data2.core :as d]))

(defn psa
  "Creates product specific attribute ref"
  [name values]
  (g/map->ProductSpecificAttributeRef
    {:name name
     :values values}))

(defn granule
  "Creates a granule"
  [collection attribs]
  (let [coll-ref (g/collection-ref (:entry-title collection))
        minimal-gran {:granule-ur (d/unique-str "ur")
                      :collection-ref coll-ref}]
    (g/map->UmmGranule (merge minimal-gran attribs))))