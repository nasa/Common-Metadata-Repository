(ns cmr.umm.test.generators.granule
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.umm.test.generators.collection :as c]
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

(def granules
  (gen/fmap (fn [[granule-ur coll-ref]]
              (g/->UmmGranule granule-ur coll-ref))
            (gen/tuple granule-urs coll-refs)))

;; Generator that only returns collection ref with entry-title
(def granules-entry-title
  (gen/fmap (fn [[granule-ur coll-ref]]
              (g/->UmmGranule granule-ur coll-ref))
            (gen/tuple granule-urs coll-refs-w-entry-title)))
