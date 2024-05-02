(ns cmr.umm.test.generators.granule
  "Provides clojure.test.check generators for use in testing other projects."
  (:require
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext-gen]
   [cmr.umm.test.generators.collection :as coll]
   [cmr.umm.test.generators.granule.temporal :as gt]
   [cmr.umm.test.generators.collection.product-specific-attribute :as psa]
   [cmr.umm.umm-granule :as granule]
   [cmr.umm.test.generators.spatial :as spatial-gen]))

;;; granule related
(def granule-urs
  (ext-gen/string-ascii 1 10))

(def coll-refs-w-entry-title
  (ext-gen/model-gen
    granule/map->CollectionRef
    (gen/hash-map :entry-title coll/entry-titles)))

(def coll-refs-w-short-name-version
  (ext-gen/model-gen
    granule/map->CollectionRef
    (gen/hash-map :short-name coll/short-names
                  :version-id coll/version-ids)))

(def coll-refs-w-entry-id
  (ext-gen/model-gen
    granule/map->CollectionRef
    (gen/hash-map :entry-id coll/entry-ids)))

(def coll-refs
  (gen/one-of [coll-refs-w-entry-title coll-refs-w-short-name-version coll-refs-w-entry-id]))

(def product-specific-attribute-refs
  (ext-gen/model-gen granule/->ProductSpecificAttributeRef psa/names (gen/vector psa/string-values 1 3)))

(def data-granules
  (ext-gen/model-gen
    granule/map->DataGranule
    (gen/hash-map :producer-gran-id (ext-gen/optional (ext-gen/string-ascii 1 10))
                  :crid-ids (ext-gen/nil-if-empty (gen/vector (ext-gen/string-ascii 1 10)))
                  :feature-ids (ext-gen/nil-if-empty (gen/vector (ext-gen/string-ascii 1 10)))
                  :day-night (ext-gen/optional (gen/elements ["DAY" "NIGHT" "BOTH" "UNSPECIFIED"]))
                  :production-date-time ext-gen/date-time
                  :size (ext-gen/choose-double 0 1024))))

(def pge-version-classes
  (ext-gen/model-gen
   granule/map->PGEVersionClass
   (gen/hash-map :pge-name (ext-gen/string-ascii 1 10)
                 :pge-version (ext-gen/string-ascii 1 10))))

(def cloud-cover-values
  (gen/fmap double gen/ratio))

(def characteristic-ref-names
  (ext-gen/string-ascii 1 10))

(def characteristic-ref-values
  (ext-gen/string-ascii 1 10))

(def characteristic-refs
  (ext-gen/model-gen granule/->CharacteristicRef characteristic-ref-names characteristic-ref-values))

(def sensor-ref-short-names
  (ext-gen/string-ascii 1 10))

(def sensor-refs
  (ext-gen/model-gen granule/->SensorRef
                     sensor-ref-short-names
                     (ext-gen/nil-if-empty (gen/vector characteristic-refs 0 4))))

(def instrument-ref-short-names
  (ext-gen/string-ascii 1 10))

(def operation-modes
  (ext-gen/string-ascii 1 10))

(def instrument-refs
  (ext-gen/model-gen granule/->InstrumentRef
                     instrument-ref-short-names
                     (ext-gen/nil-if-empty (gen/vector-distinct characteristic-refs
                                                                {:min-elements 0 :max-elements 4}))
                     (ext-gen/nil-if-empty (gen/vector-distinct sensor-refs
                                                                {:min-elements 0 :max-elements 4}))
                     (ext-gen/nil-if-empty (gen/vector-distinct operation-modes
                                                                {:min-elements 0 :max-elements 4}))))

(def platform-ref-short-names
  (ext-gen/string-ascii 1 10))

(def platform-refs
  (ext-gen/model-gen granule/->PlatformRef
                     platform-ref-short-names
                     (ext-gen/nil-if-empty (gen/vector instrument-refs 0 4))))

(def measured-parameter-names
  (ext-gen/string-ascii 1 10))

(def qa-stats
  (ext-gen/non-empty-obj-gen
    (ext-gen/model-gen
      granule/map->QAStats
      (gen/hash-map :qa-percent-missing-data (ext-gen/optional (ext-gen/choose-double 0 100))
                    :qa-percent-out-of-bounds-data (ext-gen/optional (ext-gen/choose-double 0 100))
                    :qa-percent-interpolated-data (ext-gen/optional (ext-gen/choose-double 0 100))
                    :qa-percent-cloud-cover (ext-gen/optional (ext-gen/choose-double 0 100))))))

(def qa-flags
  (ext-gen/non-empty-obj-gen
    (ext-gen/model-gen
      granule/map->QAFlags
      (gen/hash-map :automatic-quality-flag (ext-gen/optional (ext-gen/string-ascii 1 10))
                    :automatic-quality-flag-explanation (ext-gen/optional (ext-gen/string-ascii 1 10))
                    :operational-quality-flag (ext-gen/optional (ext-gen/string-ascii 1 10))
                    :operational-quality-flag-explanation (ext-gen/optional (ext-gen/string-ascii 1 10))
                    :science-quality-flag (ext-gen/optional (ext-gen/string-ascii 1 10))
                    :science-quality-flag-explanation (ext-gen/optional (ext-gen/string-ascii 1 10))))))

(def measured-parameters
  (ext-gen/model-gen granule/->MeasuredParameter
                     measured-parameter-names
                     (ext-gen/optional qa-stats)
                     (ext-gen/optional qa-flags)))

(def two-d-coordinate-system
  (let [coords-gen (gen/fmap sort (gen/vector (ext-gen/choose-double 0 1000) 1 2))]
    (gen/fmap
      (fn [[name [start-coordinate-1 end-coordinate-1] [start-coordinate-2 end-coordinate-2]]]
        (granule/map->TwoDCoordinateSystem {:name name
                                            :start-coordinate-1 start-coordinate-1
                                            :end-coordinate-1 end-coordinate-1
                                            :start-coordinate-2 start-coordinate-2
                                            :end-coordinate-2 end-coordinate-2}))
      (gen/tuple (ext-gen/string-ascii 1 10)
                 coords-gen
                 coords-gen))))

(def spatial-coverages
  (ext-gen/model-gen
    granule/map->SpatialCoverage
    (gen/one-of
      [(gen/hash-map :geometries (gen/vector-distinct spatial-gen/geometries {:min-elements 1 :max-elements 5}))
       (gen/hash-map :orbit spatial-gen/orbits)])))

(def data-provider-timestamps
  (ext-gen/model-gen granule/->DataProviderTimestamps
                     ext-gen/date-time ext-gen/date-time (ext-gen/optional ext-gen/date-time)))

(def granules
  (ext-gen/model-gen
    granule/map->UmmGranule
    (gen/hash-map
      :granule-ur granule-urs
      :data-provider-timestamps data-provider-timestamps
      :collection-ref coll-refs
      :data-granule (ext-gen/optional data-granules)
      :pge-version-class (ext-gen/optional pge-version-classes)
      :access-value (ext-gen/optional (ext-gen/choose-double -10 10))
      :temporal gt/temporal
      :orbit-calculated-spatial-domains (ext-gen/nil-if-empty
                                          (gen/vector
                                            spatial-gen/orbit-calculated-spatial-domains 0 5))
      :platform-refs (ext-gen/nil-if-empty (gen/vector platform-refs 0 4))
      :project-refs (ext-gen/nil-if-empty (gen/vector-distinct (ext-gen/string-ascii 1 10) {:min-elements 0 :max-elements 3}))
      :cloud-cover (ext-gen/optional cloud-cover-values)
      :two-d-coordinate-system (ext-gen/optional two-d-coordinate-system)
      :related-urls (ext-gen/nil-if-empty (gen/vector coll/related-url 0 5))
      :spatial-coverage (ext-gen/optional spatial-coverages)
      :measured-parameters (ext-gen/optional
                             (ext-gen/nil-if-empty (gen/vector measured-parameters 0 5)))
      :product-specific-attributes (ext-gen/nil-if-empty
                                     (gen/vector product-specific-attribute-refs 0 5)))))
