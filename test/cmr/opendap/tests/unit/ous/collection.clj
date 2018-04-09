(ns cmr.opendap.tests.unit.ous.collection
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.collection :as collection]))

(deftest params-keys
  (is (= #{:coverage :rangesubset}
         collection/ous-prototype-params-keys))
  (is (= #{:exclude-granules? :collection-id :variables :granules
           :bounding-box}
         collection/collection-params-keys)))

(deftest params?
  (is (collection/ous-prototype-params?
       (collection/map->OusPrototypeParams {})))
  (is (not (collection/ous-prototype-params?
            (collection/map->CollectionParams {}))))
  (is (not (collection/collection-params?
            (collection/map->OusPrototypeParams {}))))
  (is (collection/collection-params?
       (collection/map->CollectionParams {}))))
