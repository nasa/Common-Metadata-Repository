(ns cmr.umm.test.generators.granule
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.umm.test.generators.collection :as c]
            [cmr.umm.test.generators.granule.temporal :as gt]
            [cmr.umm.test.generators.collection.product-specific-attribute :as psa]
            [cmr.umm.test.generators.granule.orbit-calculated-spatial-domain :as ocsd]
            [cmr.umm.granule :as g]))

;;; granule related
(def granule-urs
  (ext-gen/string-ascii 1 80))

(def coll-refs-w-entry-title
  (ext-gen/model-gen g/collection-ref c/entry-titles))

(def coll-refs-w-short-name-version
  (ext-gen/model-gen g/collection-ref c/short-names c/version-ids))

(def coll-refs
  (gen/one-of [coll-refs-w-entry-title coll-refs-w-short-name-version]))

(def proj-refs
  (gen/vector (ext-gen/string-ascii 1 80)))

(def product-specific-attribute-refs
  (ext-gen/model-gen g/->ProductSpecificAttributeRef psa/names (gen/vector psa/string-values 1 3)))

(def related-url
  (ext-gen/model-gen
    g/map->RelatedURL
    ;; we only test OnlineAccessURL here for simplification purpose
    (gen/hash-map :type (gen/return "GET DATA")
                  :url ext-gen/file-url-string
                  :description (ext-gen/string-ascii 1 80))))

(def data-granules
  (ext-gen/model-gen
    g/map->DataGranule
    (gen/hash-map :producer-gran-id (ext-gen/optional (ext-gen/string-ascii 1 128))
                  :day-night (gen/elements ["DAY" "NIGHT" "BOTH" "UNSPECIFIED"])
                  :production-date-time ext-gen/date-time)))

(def cloud-cover-values
  (ext-gen/optional  (gen/frequency [[7 (ext-gen/choose-double 0.0 100.0)] [3 (ext-gen/choose-double 0.0 1.0)]])))

(def granules
  (ext-gen/model-gen
    g/map->UmmGranule
    (gen/hash-map
      :granule-ur granule-urs
      :collection-ref coll-refs
      :data-granule (ext-gen/optional data-granules)
      :temporal gt/temporal
      :orbit-calculated-spatial-domains (ext-gen/nil-if-empty (gen/vector ocsd/orbit-calculated-spatial-domains 0 5))
      :project-refs (ext-gen/nil-if-empty proj-refs)
      :cloud-cover cloud-cover-values
      :related-urls (ext-gen/nil-if-empty (gen/vector related-url 0 5))
      :product-specific-attributes (ext-gen/nil-if-empty (gen/vector product-specific-attribute-refs 0 5)))))

;; Generator that only returns collection ref with entry-title
;; DEPRECATED - this will go away in the future. Don't use it.
(def granules-entry-title
  (gen/fmap (fn [[granule-ur coll-ref]]
              (g/map->UmmGranule {:granule-ur granule-ur
                                  :collection-ref coll-ref}))
            (gen/tuple granule-urs coll-refs-w-entry-title)))
