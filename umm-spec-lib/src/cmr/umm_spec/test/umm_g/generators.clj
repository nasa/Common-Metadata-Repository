(ns cmr.umm-spec.test.umm-g.generators
  "Generator functions for UMM-G property based tests."
  (:require
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext-gen]
   [cmr.common.util :as util]
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

(def qa-stats
  (ext-gen/non-empty-obj-gen
   (ext-gen/model-gen
    umm-lib-g/map->QAStats
    (gen/not-empty
     (gen/hash-map :qa-percent-missing-data (ext-gen/optional (ext-gen/choose-double 0 100))
                   :qa-percent-out-of-bounds-data (ext-gen/optional (ext-gen/choose-double 0 100))
                   :qa-percent-interpolated-data (ext-gen/optional (ext-gen/choose-double 0 100))
                   :qa-percent-cloud-cover (ext-gen/optional (ext-gen/choose-double 0 100)))))))

(def qa-auto-flag
  (gen/elements ["Passed" "Failed" "Suspect" "Undetermined"]))

(def qa-op-flag
  (gen/elements ["Passed" "Failed" "Being Investigated" "Not Investigated"
                 "Inferred Passed" "Inferred Failed" "Suspect" "Undetermined"]))

(def qa-science-flag
  (gen/elements ["Passed" "Failed" "Being Investigated" "Not Investigated"
                 "Inferred Passed" "Inferred Failed" "Suspect" "Hold" "Undetermined"]))

(def qa-flags
  (ext-gen/non-empty-obj-gen
   (ext-gen/model-gen
    umm-lib-g/map->QAFlags
    (gen/fmap
     (fn [[flag-map explanation-map]]
       (when (not-empty (util/remove-nil-keys flag-map))
         (merge flag-map explanation-map)))
     (gen/tuple
       (gen/hash-map :automatic-quality-flag (ext-gen/optional (gen/one-of
                                                                [(ext-gen/string-ascii 1 10)
                                                                 qa-auto-flag]))
                     :operational-quality-flag (ext-gen/optional (gen/one-of
                                                                  [(ext-gen/string-ascii 1 10)
                                                                   qa-op-flag]))
                     :science-quality-flag (ext-gen/optional (gen/one-of
                                                              [(ext-gen/string-ascii 1 10)
                                                               qa-science-flag])))
       (gen/hash-map :automatic-quality-flag-explanation (ext-gen/optional (ext-gen/string-ascii 1 10))
                     :operational-quality-flag-explanation (ext-gen/optional (ext-gen/string-ascii 1 10))
                     :science-quality-flag-explanation (ext-gen/optional (ext-gen/string-ascii 1 10))))))))

(def umm-g-measured-parameter-gen
  "Generator for UMM-G measured-parameter using enum values and required fields in qa-flags AND
   qa-stats."
  (ext-gen/model-gen
   umm-lib-g/->MeasuredParameter
   gran-gen/measured-parameter-names
   (ext-gen/optional qa-stats)
   (ext-gen/optional qa-flags)))

(def longitude
  (ext-gen/choose-double -180 180))

(def orbital-model-name
  (ext-gen/string-ascii 1 10))

(def orbit-numbers
  (gen/fmap sort (gen/vector gen/int 1 2)))

(def orbit-calculated-spatial-domain
  (gen/fmap (fn [[omn ecl ecdt [start-orbit-number stop-orbit-number]]]
              (if stop-orbit-number
                (umm-lib-g/map->OrbitCalculatedSpatialDomain
                 {:orbital-model-name omn
                  :start-orbit-number start-orbit-number
                  :stop-orbit-number stop-orbit-number
                  :equator-crossing-longitude ecl
                  :equator-crossing-date-time ecdt})
                (umm-lib-g/map->OrbitCalculatedSpatialDomain
                 {:orbital-model-name omn
                  :orbit-number start-orbit-number
                  :equator-crossing-longitude ecl
                  :equator-crossing-date-time ecdt})))
            (gen/tuple orbital-model-name
                       longitude
                       ext-gen/date-time
                       orbit-numbers)))

(defn replace-generators
  "Function to replace umm-lib generators with ones that will work for UMM-G elements"
  [granule-model-gen]
  (-> granule-model-gen
      (assoc :collection-ref (gen/generate umm-g-coll-refs))
      (assoc :measured-parameters (-> umm-g-measured-parameter-gen
                                      (gen/vector-distinct  {:min-elements 0 :max-elements 5})
                                      ext-gen/nil-if-empty
                                      ext-gen/optional
                                      gen/generate))
      (assoc :orbit-calculated-spatial-domains (-> orbit-calculated-spatial-domain
                                                   (gen/vector 0 5)
                                                   ext-gen/nil-if-empty
                                                   gen/generate))
      (assoc :two-d-coordinate-system (gen/generate
                                       (ext-gen/optional
                                        umm-g-tiling-identification-system-gen)))))

(def umm-g-granules
  "Generator for UMM-G granule in umm-lib Granule model."
  (gen/fmap replace-generators gran-gen/granules))
