(ns cmr.system-int-test.search.granule-counts-search-test
  "This tests the granule counts search feature which allows retrieving counts of granules per collection."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(comment

  (ingest/reset)
  (ingest/create-provider "provguid1" "PROV1")


)


;; TODO add tests incorporating ACLs for granules

;; TODO add tests searching with invalid value for :include-granule-counts

;; TODO test :include-granule-counts with AQL search

;; TODO test granule search rejects :include-granule-counts

(defn temporal-range
  "Creates attributes for collection or granule defining a temporal range between start and stop
  which should be single digit integers."
  [start stop]
  (let [n-to-date-str #(str "200" % "-01-01T00:00:00Z")]
    {:beginning-date-time (n-to-date-str start)
     :ending-date-time (n-to-date-str stop)}))

(defn assert-granule-counts
  "Takes a map of collections to counts and reference response and checks that the references
  were found and that the granule counts are correct."
  [expected-counts refs-result]
  (is (d/refs-match? (keys expected-counts) refs-result))
  (let [count-map (into {} (for [[coll granule-count] expected-counts]
                             [(:entry-title coll) granule-count]))
        actual-count-map (into {} (for [{:keys [name granule-count]} (:refs refs-result)]
                                    [name granule-count]))]
    (is (= count-map actual-count-map))))

(deftest find-granule-counts-per-collection-test
  (let [make-coll (fn [n shape temporal-attribs]
                    (let [spatial-attribs (when shape
                                            {:spatial-coverage
                                             (dc/spatial :geodetic :geodetic shape)})
                          coll-attribs (merge {:entry-title (str "coll" n)}
                                              spatial-attribs
                                              temporal-attribs)]
                      (d/ingest "PROV1" (dc/collection coll-attribs))))
        make-gran (fn [coll shape temporal-attribs]
                    (let [spatial-attribs (when shape {:spatial-coverage (dg/spatial shape)})
                          gran-attribs (merge {} spatial-attribs temporal-attribs)]
                      (d/ingest "PROV1" (dg/granule coll gran-attribs))))

        ;; Create collections
        ;; whole world, no temporal
        coll1 (make-coll 1 m/whole-world nil)
        ;; western hemisphere
        coll2 (make-coll 2 (m/mbr -180 90 0 -90) (temporal-range 1 3))
        ;; eastern hemisphere
        coll3 (make-coll 3 (m/mbr 0 90 180 -90) (temporal-range 2 4))
        ;; northern hemisphere
        coll4 (make-coll 4 (m/mbr -180 90 180 0) (temporal-range 3 5))
        ;; southern hemisphere
        coll5 (make-coll 5 (m/mbr -180 0 180 -90) (temporal-range 4 6))
        ;; no spatial
        coll6 (make-coll 6 nil (temporal-range 1 6))]

    ;; -- Make granules --
    ;; Coll1 granules
    (make-gran coll1 (p/point 0 0) nil)
    (make-gran coll1 (p/point 0 90) nil)
    (make-gran coll1 (p/point 0 -90) nil)
    (make-gran coll1 (p/point -135 0) nil)
    (make-gran coll1 (p/point 135 0) nil)

    ;; Coll2 granules
    ;; There are none (should always be 0)

    ;; Coll3 granules
    (make-gran coll3 (p/point 0 90) (temporal-range 2 2))
    (make-gran coll3 (p/point 135 0) (temporal-range 3 3))
    (make-gran coll3 (p/point 0 -90) (temporal-range 4 4))

    ;; Coll4 granules
    (make-gran coll4 (p/point 0 90) (temporal-range 3 3))
    (make-gran coll4 (p/point 135 45) (temporal-range 4 4))
    (make-gran coll4 (p/point -135 45) (temporal-range 5 5))

    ;; Coll5 granules
    (make-gran coll5 (p/point 0 -90) (temporal-range 4 4))
    (make-gran coll5 (p/point 135 -45) (temporal-range 5 5))
    (make-gran coll5 (p/point -135 -45) (temporal-range 6 6))

    ;; Coll6 granules
    (make-gran coll6 nil (temporal-range 1 1))
    (make-gran coll6 nil (temporal-range 3 3))
    (make-gran coll6 nil (temporal-range 6 6))
    (index/refresh-elastic-index)

    (testing "granule counts for all collections"
      (let [refs (search/find-refs :collection {:include-granule-counts true})]
        (assert-granule-counts {coll1 5 coll2 0 coll3 3 coll4 3 coll5 3 coll6 3} refs)))

    ;; TODO add more tests

    ))










