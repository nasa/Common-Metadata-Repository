(ns cmr.umm.test.generators.granule
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.umm.test.generators.collection :as c]
            [cmr.umm.test.generators.granule.temporal :as gt]
            [cmr.umm.test.generators.collection.product-specific-attribute :as psa]
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

(def product-specific-attribute-refs
  (ext-gen/model-gen g/->ProductSpecificAttributeRef psa/names (gen/vector psa/string-values 1 3)))

(def granules
  (gen/fmap (fn [[granule-ur coll-ref temporal-coverage psas]]
              (g/->UmmGranule granule-ur coll-ref temporal-coverage psas))
            (gen/tuple granule-urs
                       coll-refs
                       gt/temporal-coverage
                       (ext-gen/nil-if-empty (gen/vector product-specific-attribute-refs 0 5)))))

;; Generator that only returns collection ref with entry-title
;; DEPRECATED - this will go away in the future. Don't use it.
(def granules-entry-title
  (gen/fmap (fn [[granule-ur coll-ref]]
              (g/map->UmmGranule {:granule-ur granule-ur
                                  :collection-ref coll-ref}))
            (gen/tuple granule-urs coll-refs-w-entry-title)))
