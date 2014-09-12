(ns cmr.system-int-test.search.granule-orbit-search-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as derived]
            [cmr.spatial.codec :as codec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-orbit
  (let [;; orbit parameters
        op1 {:swath-width 1450
             :period 98.88
             :inclination-angle 98.15
             :number-of-orbits 0.5
             :start-circular-latitude -90}
        op2 {:swath-width 2
             :period 96.7
             :inclination-angle 94
             :number-of-orbits 0.25
             :start-circular-latitude -50}
        make-gran (fn [coll ur asc-crossing start-lat start-dir end-lat end-dir]
                    (let [orbit (dg/orbit
                                  asc-crossing
                                  start-lat
                                  start-dir
                                  end-lat
                                  end-dir)]
                      (d/ingest "PROV1" (dg/granule coll
                                                    {:granule-ur ur
                                                     :spatial-coverage (apply
                                                                         dg/spatial
                                                                         orbit
                                                                         nil)}))))
        coll1 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "orbit-params1"
                           :spatial-coverage (dc/spatial :geodetic op1)}))
        coll2 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "orbit-params2"
                           :spatial-coverage (dc/spatial :geodetic op2)}))
        g1 (make-gran coll1 "gran1" -158.1 81.8 :desc  -81.8 :desc)
        g2 (make-gran coll1 "gran2" 177.16 -81.8 :asc 81.8 :asc)
        g3 (make-gran coll1 "gran3" 127.73 81.8 :desc -81.8 :desc)
        g4 (make-gran coll1 "gran4" 103 -81.8 :asc 81.8 :asc)
        g5 (make-gran coll2 "gran5" 79.88192 50 :asc 50 :desc)
        g6 (make-gran coll2 "gran6" 55.67938 -50 :asc 50 :asc)
        g7 (make-gran coll2 "gran7" 31.48193 50 :desc -50 :desc)
        g8 (make-gran coll2 "gran8" 7.28116 -50 :asc 50 :asc)]
    (index/refresh-elastic-index)

    (testing "bounding rectangle searches"
      (are [wnes items params]
           (let [found (search/find-refs
                         :granule
                         (merge {:bounding-box
                                 (codec/url-encode (apply m/mbr wnes))
                                 :page-size 50} params))
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :granule-ur) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           ;; Search for orbits crossing a rectangle over the equator and anti-meridian
           [145 45 -145 -45] [g2 g7] nil
           ;; Search for orbits crossing a rectangle over the equator and meridian
           [-45 45 45 -45] [g1 g3 g8] nil
           ;; Search for orbits crossing a rectangle in the western hemisphere near the north pole
           [-90 89 -45 85] [g5] nil
           ;; Search for orbits crossing a rectangle in the southern hemisphere crossing the anti-meridian
           [145 -45 -145 -85] [g2 g3 g4 g7] nil
           ;; Search while specifying parent collection
           [145 45 -145 -45] [g2] {:concept-id (:concept-id coll1)}))))
