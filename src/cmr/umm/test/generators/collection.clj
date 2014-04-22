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

(def collections
  (gen/fmap (fn [[entry-title product temporal-coverage psa]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))]
                (c/->UmmCollection entry-id entry-title product temporal-coverage psa)))
            (gen/tuple
              entry-titles
              products
              t/temporal-coverages
              (ext-gen/nil-if-empty (gen/vector psa/product-specific-attributes 0 10)))))

; Generator for basic collections that only have the bare minimal fields
(def basic-collections
  (gen/fmap (fn [[entry-title product]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))]
                (c/->UmmCollection entry-id entry-title product nil)))
            (gen/tuple entry-titles products)))

