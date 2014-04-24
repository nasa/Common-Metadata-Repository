(ns cmr.system-int-test.data2.collection
  "Contains data generators for example based testing in system integration tests."
  (:require [cmr.umm.collection :as c]
            [cmr.system-int-test.data2.core :as d])
  (:import [cmr.umm.collection
            Product
            UmmCollection]))


(comment

  (type (collection {:foo 4 :short-name "foo"}))
  (product {:foo 4 :short-name "foo"})

  (type (first (cmr.umm.collection.ProductSpecificAttribute/getBasis)))

  )

(defn psa
  "Creates product specific attribute"
  [name type]
  (c/map->ProductSpecificAttribute
    {:name name
     :description "Generated"
     :data-type type}))

(defn product
  [attribs]
  (let [attribs (select-keys attribs (d/record-fields Product))
        minimal-product {:short-name (d/unique-str "short-name")
                         :long-name (d/unique-str "long-name")
                         :version-id (d/unique-str "V")}]
    (c/map->Product (merge minimal-product attribs))))

(defn collection
  "Creates a collection"
  [attribs]
  (let [product (product attribs)
        minimal-coll {:entry-id (str (:short-name product) "_" (:version-id product))
                      :entry-title (str (:long-name product) " " (:version-id product))
                      :product product}
        attribs (select-keys attribs (d/record-fields UmmCollection))
        attribs (merge minimal-coll attribs)]
    (c/map->UmmCollection attribs)))