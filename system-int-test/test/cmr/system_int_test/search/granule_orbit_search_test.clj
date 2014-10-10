(ns cmr.system-int-test.search.granule-orbit-search-test
  "Tests for spatial search with obital back tracking."
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.search.granule-spatial-search-test :as st]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as derived]
            [cmr.spatial.codec :as codec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- make-gran
  [coll ur asc-crossing start-lat start-dir end-lat end-dir]
  (let [orbit (dg/orbit
                asc-crossing
                start-lat
                start-dir
                end-lat
                end-dir)]
    (d/ingest
      "PROV1"
      (dg/granule
        coll
        {:granule-ur ur
         :spatial-coverage (apply
                             dg/spatial
                             orbit
                             nil)}))))

(deftest orbit-search
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
        ;; orbit parameters with missing start-circular latititude to test guards that prevent
        ;; previous null pointer exception caused by this
        op3-bad {:swath-width 1450
                 :period 98.88
                 :inclination-angle 98.15
                 :number-of-orbits 0.5
                 :start-circular-latitude nil}
        coll1 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "orbit-params1"
                           :spatial-coverage (dc/spatial {:gsr :orbit
                                                          :orbit op1})}))
        coll2 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "orbit-params2"
                           :spatial-coverage (dc/spatial {:gsr :orbit
                                                          :orbit op2})}))
        coll3 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "orbit-params3"
                           :spatial-coverage (dc/spatial {:gsr :orbit
                                                          :orbit op3-bad})}))
        g1 (make-gran coll1 "gran1" -158.1 81.8 :desc  -81.8 :desc)
        g2 (make-gran coll1 "gran2" 177.16 -81.8 :asc 81.8 :asc)
        g3 (make-gran coll1 "gran3" 127.73 81.8 :desc -81.8 :desc)
        g4 (make-gran coll1 "gran4" 103 -81.8 :asc 81.8 :asc)
        g5 (make-gran coll2 "gran5" 79.88192 50 :asc 50 :desc)
        g6 (make-gran coll2 "gran6" 55.67938 -50 :asc 50 :asc)
        g7 (make-gran coll2 "gran7" 31.48193 50 :desc -50 :desc)
        g8 (make-gran coll2 "gran8" 7.28116 -50 :asc 50 :asc)
        g9 (make-gran coll3 "gran9" 127.73 81.8 :desc -81.8 :desc)]
    (index/refresh-elastic-index)

    (testing "bounding rectangle searches"
      (are [items wnes params]
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
           [g2 g7] [145 45 -145 -45] nil
           ;; Search for orbits crossing a rectangle over the equator and meridian
           [g1 g3 g8] [-45 45 45 -45] nil
           ;; Search for orbits crossing a rectangle in the western hemisphere near the north pole
           [g5] [-90 89 -45 85] nil
           ;; Search for orbits crossing a rectangle in the southern hemisphere crossing the anti-meridian
           [g2 g3 g4 g7] [145 -45 -145 -85] nil
           ;; Search while specifying parent collection
           [g2] [145 45 -145 -45] {:concept-id (:concept-id coll1)}))

    (testing "point searches"
      (are [items lon_lat params]
           (let [found (search/find-refs :granule {:point (codec/url-encode (apply p/point lon_lat))
                                                   :page-size 50})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :granule-ur) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           [g3] [-45,45] nil
           [g1] [0,-20] nil
           [g2] [-180,0] nil
           ))

    (testing "line searches"
      (are [items coords params]
           (let [found (search/find-refs
                         :granule
                         {:line (codec/url-encode (apply l/ords->line-string :geodetic coords))
                          :page-size 50})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :granule-ur) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           ;; Line crossing prime meridian and equator
           [g1 g8] [-45,-45 45,45] nil
           ;; Line crossing the antimeridian - TODO - This case gets different results from
           ;; catalog-rest (returns g2 & g7) and will fail if uncommented. Need to determine if it
           ;; is supposed to return both granules or not.
           ;[g2] [179,-45 -170, 30] nil
           ))

    (testing "polygon searches"
      (are [items coords params]
           (let [found (search/find-refs
                         :granule
                         (merge {:polygon
                                 (apply st/search-poly coords)
                                 :page-size 50} params))
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :granule-ur) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           ;; Trangle crossing prime meridian
           [g1 g8] [-45,-45 45,-45 45,0, -45,-45] nil
           ;; Pentagon over the north pole
           [g1 g2 g3 g4 g5] [0,80 72,80 144,80 -144,80 -72,80 0,80] nil
           ;; Concave polygon crossing antimeridian and equator
           [g2 g4 g7] [170,-70 -170,-80 -175,20 -179,-10 175,25 170,-70] nil))))

(deftest multi-orbit-search
  (let [;; orbit parameters
        op1 {:swath-width 2
             :period 96.7
             :inclination-angle 94
             :number-of-orbits 14
             :start-circular-latitude 50}
        op2 {:swath-width 400
             :period 98.88
             :inclination-angle 98.3
             :number-of-orbits 1
             :start-circular-latitude 0}
        coll1 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "orbit-params1"
                           :spatial-coverage (dc/spatial {:gsr :orbit
                                                          :orbit op1})}))
        coll2 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "orbit-params2"
                           :spatial-coverage (dc/spatial {:gsr :orbit
                                                          :orbit op2})}))
        g1 (make-gran coll1 "gran1" 104.0852 50 :asc 50 :asc)
        g2 (make-gran coll2 "gran2" 31.946 70.113955 :asc  -71.344289 :desc)]
    (index/refresh-elastic-index)

    (testing "bounding rectangle searches"
      (are [items wnes params]
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

           ;; Search with large box that finds the granule
           [g1] [-134.648 69.163 76.84 65.742] {:concept-id (:concept-id coll1)}
           ;; Search near equator that doesn't intersect granule
           [] [0 1 1 0] {:concept-id (:concept-id coll1)}
           ;; Search near equator that intersects granule
           [g1] [1 11 15 1] {:concept-id (:concept-id coll1)}
           ;; Search near north pole
           [] [1 89.5 1.5 89] {:concept-id (:concept-id coll1)}
           ;; Search for granules with exactly one orbit does not match areas seen on the second pass
           [] [175.55 38.273 -164.883 17.912] {:concept-id (:concept-id coll2)}
           ;; The following test is deliberately commented out because it is marked as @broken
           ;; in ECHO. It is included here for completeness.
           ;;
           ;; The following comment is repeated verbatim from the ECHO cucumber test:
           ;;
           ;; Orbit searching is known to have some issues. The following query successfully finds a granule
           ;; when it appears from the drawing of the granule in Reverb that it shouldn't.  I manually verified that
           ;; the query does match the query output in echo-catalog when searching the legacy provider schemas.
           ;; At some point it would be a good idea for someone in the ECHO organization to have a better understanding
           ;; of the backtrack algorithm and be able to definitively state when an orbit granule doesn intersect a
           ;; particular bounding box.
           ;;
           ;[] [0 1 6 0] nil
           ))))



