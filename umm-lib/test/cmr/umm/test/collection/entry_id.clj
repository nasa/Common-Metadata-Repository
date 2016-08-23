(ns cmr.umm.test.collection.entry-id
  "Test construction of entry-id and normalizing version-id"
  (:require [clojure.test :refer :all]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.collection.entry-id :as eid]))

(deftest entry-id
  (testing "entry-id with and without version-id"
    (are [umm-map exp-eid]
         (let [product (c/map->Product umm-map)
               umm-c (c/map->UmmCollection {:product product})]
           (is (= exp-eid (eid/umm->entry-id umm-c))))

         {:short-name "S1" :version-id eid/DEFAULT_VERSION} "S1"
         {:short-name "S1" :version-id "Complete"} "S1_Complete")))
