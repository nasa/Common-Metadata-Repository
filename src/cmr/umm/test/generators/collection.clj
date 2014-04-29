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

(def products
  (ext-gen/model-gen c/->Product short-names long-names version-ids))

(def entry-titles
  (ext-gen/string-alpha-numeric 1 1030))

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

(def archive-center-org-type (gen/elements ["archive-center"]))

(def processing-center-org-type (gen/elements ["processing-center"]))

(def archive-center-organizations
  (ext-gen/model-gen c/->Organization archive-center-org-type org-names))

(def processing-center-organizations
  (ext-gen/model-gen c/->Organization processing-center-org-type org-names))

(def collections
  (gen/fmap (fn [[entry-title product temporal psa campaigns two-ds proc-org archive-org]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))
                    orgs [proc-org archive-org]]
                (c/->UmmCollection entry-id entry-title product temporal psa campaigns two-ds (remove nil? orgs))))
            (gen/tuple
              entry-titles
              products
              t/temporals
              (ext-gen/nil-if-empty (gen/vector psa/product-specific-attributes 0 10))
              (ext-gen/nil-if-empty (gen/vector campaigns 0 4))
              (ext-gen/nil-if-empty (gen/vector two-d-coordinate-systems 0 3))
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



