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

;; For now these are the valid values
(def org-types (gen/elements ["archive-center" "processing-center"]))

;; Generate a value from above domain
(def org-type-gen
  (gen/fmap (fn [[type]] type)
            (gen/tuple org-types)))


;; Simulated values of archive center and processing center names
(def org-short-names
  (ext-gen/string-ascii 1 80))

(def organizations
  (ext-gen/model-gen c/->Organization org-type-gen org-short-names))

(defn ordered-orgs
  "Maintain processing and archive center elem order and ensure only one elem of each type is present"
  [orgs]
  (let [org-cnt (count orgs)]
    (cond (= 0 org-cnt) []
          (= 1 org-cnt) orgs
          (= 2 org-cnt) (vector (assoc-in (first  orgs) [:type] "processing-center")
                                (assoc-in (last  orgs) [:type] "archive-center")))))

(def collections
  (gen/fmap (fn [[entry-title product temporal psa campaigns two-ds orgs]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))
                    adjusted-orgs (ordered-orgs orgs)]
                (c/->UmmCollection entry-id entry-title product temporal psa campaigns two-ds adjusted-orgs)))
            (gen/tuple
              entry-titles
              products
              t/temporals
              (ext-gen/nil-if-empty (gen/vector psa/product-specific-attributes 0 10))
              (ext-gen/nil-if-empty (gen/vector campaigns 0 4))
              (ext-gen/nil-if-empty (gen/vector two-d-coordinate-systems 0 3))
              (ext-gen/nil-if-empty (gen/vector organizations 0 2)))))

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



