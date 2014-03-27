(ns cmr.umm.test.generators
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.collection :as c]
            [cmr.umm.granule :as g]))

(def short-names
  (ext-gen/string-ascii 1 85))

(def version-ids
  (ext-gen/string-ascii 1 80))

(def long-names
  (ext-gen/string-ascii 1 1024))

(def products
  (ext-gen/model-gen c/->Product short-names long-names version-ids))

(def entry-titles
  (ext-gen/string-ascii 1 1030))

(def collections
  (gen/fmap (fn [[entry-title product]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))]
                (c/->UmmCollection entry-id entry-title product)))
            (gen/tuple entry-titles products)))

;;; granule related
(def granule-urs
  (ext-gen/string-ascii 1 1024))

(def entry-ids
  (ext-gen/string-ascii 1 85))

;; all of the collection elements would have DataSetId
(def coll-refs
  (ext-gen/model-gen g/->CollectionRef entry-ids short-names version-ids))

;; TODO - make this work
(def coll-refs1
  (ext-gen/model-gen g/->CollectionRef entry-ids "" ""))

;; TODO - make this work
(def coll-refs2
  (ext-gen/model-gen g/->CollectionRef "" short-names version-ids))

(def granules
  (gen/fmap (fn [[granule-ur coll-ref]]
              (g/->UmmEchoGranule granule-ur coll-ref))
            (gen/tuple granule-urs coll-refs)))
