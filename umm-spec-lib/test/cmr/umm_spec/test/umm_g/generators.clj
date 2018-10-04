(ns cmr.umm-spec.test.umm-g.generators
  "Generator functions for UMM-G property based tests."
  (:require
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext-gen]
   [cmr.umm.test.generators.granule :as gran-gen]
   [cmr.umm.umm-granule :as umm-lib-g]))

(def umm-g-coll-refs
  "Generator for UMM-G granule collection ref. It does not support entry-id,
  only entry title, short name & version."
  (gen/one-of [gran-gen/coll-refs-w-entry-title gran-gen/coll-refs-w-short-name-version]))

(def umm-g-tiling-identification-system-gen
  "Generator for UMM-G two-d-coordinate-system using enum values."
  (let [coords-gen (gen/fmap sort (gen/vector (ext-gen/choose-double 0 1000) 1 2))]
    (gen/fmap
      (fn [[name [start-coordinate-1 end-coordinate-1] [start-coordinate-2 end-coordinate-2]]]
        (umm-lib-g/map->TwoDCoordinateSystem {:name name
                                              :start-coordinate-1 start-coordinate-1
                                              :end-coordinate-1 end-coordinate-1
                                              :start-coordinate-2 start-coordinate-2
                                              :end-coordinate-2 end-coordinate-2}))
      (gen/tuple (gen/elements ["CALIPSO" "MISR" "MODIS Tile EASE" "MODIS Tile SIN" "SMAP Tile EASE"
                                "WELD Alaska Tile" "WELD CONUS Tile" "WRS-1" "WRS-2"])
                 coords-gen
                 coords-gen))))

(def umm-g-data-granules
  (ext-gen/model-gen
    umm-lib-g/map->DataGranule
    (gen/hash-map :producer-gran-id (ext-gen/optional (ext-gen/string-ascii 1 10))
                  :day-night (gen/elements ["Day" "Night" "Both" "Unspecified"])
                  :production-date-time ext-gen/date-time
                  :size (ext-gen/choose-double 0 1024))))

(defn replace-generators
  "Function to replace umm-lib generators with ones that will work for UMM-G elements"
  [granule-model-gen]
  (-> granule-model-gen
      (assoc :collection-ref (gen/generate umm-g-coll-refs))
      (assoc :data-granule (gen/generate (ext-gen/optional umm-g-data-granules)))
      (assoc :two-d-coordinate-system (gen/generate
                                       (ext-gen/optional
                                        umm-g-tiling-identification-system-gen)))))

(def umm-g-granules
  "Generator for UMM-G granule in umm-lib Granule model."
  (gen/fmap replace-generators gran-gen/granules))
