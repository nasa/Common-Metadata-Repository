(ns cmr.umm.test.generators.collection
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.collection :as c]
            [cmr.umm.test.generators.collection.temporal :as t]
            [cmr.umm.test.generators.collection.product-specific-attribute :as psa]))

(def short-names
  (ext-gen/string-alpha-numeric 1 85))

(def version-ids
  (ext-gen/string-alpha-numeric 1 80))

(def long-names
  (ext-gen/string-alpha-numeric 1 1024))

(def processing-level-ids
  (ext-gen/string-alpha-numeric 1 80))

(def products
  (ext-gen/model-gen c/->Product short-names long-names version-ids (ext-gen/optional processing-level-ids)))

(def entry-titles
  (ext-gen/string-alpha-numeric 1 1030))

(def sensor-short-names
  (ext-gen/string-ascii 1 80))

(def sensors
  (ext-gen/model-gen c/->Sensor sensor-short-names))

(def instrument-short-names
  (ext-gen/string-ascii 1 80))

(def instruments
  (ext-gen/model-gen c/->Instrument
                     instrument-short-names
                     (ext-gen/nil-if-empty (gen/vector sensors 0 4))))

(def platform-short-names
  (ext-gen/string-ascii 1 80))

(def platform-long-names
  (ext-gen/string-ascii 1 1024))

(def platform-types
  (ext-gen/string-ascii 1 80))

(def platforms
  (ext-gen/model-gen c/->Platform
                     platform-short-names
                     platform-long-names
                     platform-types
                     (ext-gen/nil-if-empty (gen/vector instruments 0 4))))

(def campaign-short-names
  (ext-gen/string-ascii 1 40))

(def campaign-long-names
  (ext-gen/string-ascii 1 80))

(def campaigns
  (ext-gen/model-gen c/->Project campaign-short-names (ext-gen/optional campaign-long-names)))


(def two-d-names
  (ext-gen/string-ascii 1 80))

(def two-d-coordinate-systems
  (ext-gen/model-gen c/->TwoDCoordinateSystem two-d-names))

(def org-names
  (ext-gen/string-ascii 1 80))

(def archive-center-organizations
  (ext-gen/model-gen c/->Organization (gen/return :archive-center) org-names))

(def processing-center-organizations
  (ext-gen/model-gen c/->Organization (gen/return :processing-center) org-names))

(def granule-spatial-representations
  (gen/elements c/granule-spatial-representations))

(def spatial-coverages
  (ext-gen/model-gen c/->SpatialCoverage granule-spatial-representations))

(def collections
  (gen/fmap (fn [[attribs proc-org archive-org]]
              (let [product (:product attribs)]
                (c/map->UmmCollection (assoc attribs
                                             :entry-id (str (:short-name product) "_" (:version-id product))
                                             :organizations (seq (remove nil? [proc-org archive-org]))))))
            (gen/tuple
              (gen/hash-map
                :entry-title entry-titles
                :product products
                :temporal t/temporals
                :platforms (ext-gen/nil-if-empty (gen/vector platforms 0 4))
                :product-specific-attributes (ext-gen/nil-if-empty (gen/vector psa/product-specific-attributes 0 10))
                :projects (ext-gen/nil-if-empty (gen/vector campaigns 0 4))
                :two-d-coordinate-systems (ext-gen/nil-if-empty (gen/vector two-d-coordinate-systems 0 3))
                :spatial-coverage (ext-gen/optional spatial-coverages))
              (ext-gen/optional processing-center-organizations)
              (ext-gen/optional archive-center-organizations))))

; Generator for basic collections that only have the bare minimal fields
;; DEPRECATED - this will go away in the future. Don't use it.
(def basic-collections
  (gen/fmap (fn [[entry-title product]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))]
                (c/map->UmmCollection
                  {:entry-id entry-id
                   :entry-title entry-title
                   :product product})))
            (gen/tuple entry-titles products)))


(comment
  ;;;;;;;;;;;;
  (clojure.repl/dir clojure.test.check.generators)
  (ordered-orgs (last (gen/sample  (gen/vector organizations 0 2) 1)))

  (gen/sample collections 1)
  ;;;;;;;;;;;;
  )



