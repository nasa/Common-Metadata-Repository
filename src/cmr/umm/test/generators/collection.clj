(ns cmr.umm.test.generators.collection
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.collection :as c]
            [cmr.umm.test.generators.collection.temporal :as t]
            [cmr.umm.test.generators.collection.science-keyword :as sk]
            [cmr.umm.test.generators.collection.product-specific-attribute :as psa]))

(def short-names
  (ext-gen/string-alpha-numeric 1 85))

(def version-ids
  (ext-gen/string-alpha-numeric 1 80))

(def long-names
  (ext-gen/string-alpha-numeric 1 1024))

(def processing-level-ids
  (ext-gen/string-alpha-numeric 1 80))

(def collection-data-types
  (gen/elements ["SCIENCE_QUALITY" "NEAR_REAL_TIME" "OTHER"]))

(def products
  (ext-gen/model-gen c/->Product
                     short-names
                     long-names
                     version-ids
                     (ext-gen/optional processing-level-ids)
                     (ext-gen/optional collection-data-types)))

(def dif-products
  (ext-gen/model-gen c/->Product short-names long-names version-ids (gen/return nil) (gen/return nil)))

(def data-provider-timestamps
  (ext-gen/model-gen c/->DataProviderTimestamps ext-gen/date-time ext-gen/date-time (ext-gen/optional ext-gen/date-time)))

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

(def distribution-center-organizations
  (ext-gen/model-gen c/->Organization (gen/return :distribution-center) org-names))

(def related-url
  (ext-gen/model-gen
    c/map->RelatedURL
    ;; we only test OnlineAccessURL here for simplification purpose
    (gen/hash-map :type (gen/return "GET DATA")
                  :url ext-gen/file-url-string
                  :description (ext-gen/string-ascii 1 80))))

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
                :data-provider-timestamps data-provider-timestamps
                :temporal t/temporals
                :spatial-keywords (ext-gen/nil-if-empty (gen/vector (ext-gen/string-ascii 1 80) 0 4))
                :science-keywords (ext-gen/nil-if-empty (gen/vector sk/science-keywords 0 3))
                :platforms (ext-gen/nil-if-empty (gen/vector platforms 0 4))
                :product-specific-attributes (ext-gen/nil-if-empty (gen/vector psa/product-specific-attributes 0 10))
                :projects (ext-gen/nil-if-empty (gen/vector campaigns 0 4))
                :two-d-coordinate-systems (ext-gen/nil-if-empty (gen/vector two-d-coordinate-systems 0 3))
                :related-urls (ext-gen/nil-if-empty (gen/vector related-url 0 5))
                :spatial-coverage (ext-gen/optional spatial-coverages))
              (ext-gen/optional processing-center-organizations)
              (ext-gen/optional archive-center-organizations))))

(def dif-collections
  (gen/fmap (fn [[attribs]]
              (let [product (:product attribs)]
                (c/map->UmmCollection (assoc attribs
                                             :entry-id (:short-name product)
                                             :entry-title (:long-name product)))))
            (gen/tuple
              (gen/hash-map
                :product dif-products
                :temporal t/dif-temporals
                ;:spatial-keywords (ext-gen/nil-if-empty (gen/vector (ext-gen/string-ascii 1 80) 0 4))
                :science-keywords (gen/vector sk/science-keywords 1 3)
                ;:platforms (ext-gen/nil-if-empty (gen/vector platforms 0 4))
                ;:product-specific-attributes (ext-gen/nil-if-empty (gen/vector psa/product-specific-attributes 0 10))
                :projects (ext-gen/nil-if-empty (gen/vector campaigns 0 4))
                :related-urls (ext-gen/nil-if-empty (gen/vector related-url 0 5))
                :spatial-coverage (ext-gen/optional spatial-coverages)
                :organizations (gen/vector distribution-center-organizations 1 3)))))

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



