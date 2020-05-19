(ns cmr.system-int-test.search.granule-orbit-search-test
  "Tests for spatial search with orbital back tracking."
  (:require
   [clojure.string :as s]
   [clojure.test :refer :all]
   [cmr.common.util :as u]
   [cmr.spatial.codec :as codec]
   [cmr.spatial.derived :as derived]
   [cmr.spatial.kml :as kml]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as point]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.search.granule-spatial-search-test :as st]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm.umm-spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- make-gran
  ([coll ur asc-crossing start-lat start-dir end-lat end-dir]
   (make-gran coll ur asc-crossing start-lat start-dir end-lat end-dir {}))
  ([coll ur asc-crossing start-lat start-dir end-lat end-dir other-attribs]
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
         (merge {:granule-ur ur
                 :spatial-coverage (apply
                                     dg/spatial
                                     orbit
                                     nil)}
                other-attribs))))))

(deftest orbit-bug-CMR-4722
  (let [coll (d/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-collection.xml"
                                                  {:provider-id "PROV1"
                                                   :concept-type :collection
                                                   :native-id "OMSO2-collection"
                                                   :format-key :dif10})
        granule (d/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule.xml"
                                                     {:provider-id "PROV1"
                                                      :concept-type :granule
                                                      :concept-id "C4-PROV1"
                                                      :native-id "OMSO2-granule"
                                                      :format-key :echo10})]
    (index/wait-until-indexed)

    (testing "Orbit search crossing the equator for OMSO2 granules."
      (u/are3
        [items wnes]
        (let [found (search/find-refs :granule
                                      {:bounding-box (codec/url-encode (apply m/mbr wnes))
                                       :provider "PROV1"
                                       :page-size 50})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :granule-ur) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        "Rectangle that should find the granule"
        [granule] [150 70 170 60]

        "Rectangle not crossing the equator that should not find the granule"
        [] [-128.32 53.602 -46.758 1.241]

        "CMR-4722: Search crossing the equator should not erroneously find the granule"
        [] [-128.32 53.602 -46.758 -1.241]))))

(deftest orbit-bug-CMR-6431
  (let [coll (d/ingest-concept-with-metadata-file "CMR-6431/sample-orbit-collection.xml"
                                                  {:provider-id "PROV1"
                                                   :concept-type :collection
                                                   :native-id "orbit-collection"
                                                   :format-key :dif10})
        coll2 (d/ingest-concept-with-metadata-file "CMR-6431/iso-orbit-collection.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :native-id "orbit-collection-2"
                                                    :format-key :iso19115})]
    (index/wait-until-indexed)

    (testing "Orbit search will not throw exception when crossing range is nil"
      (let [coords [0.0 0.08983152841195585 -0.07779640160194513 -0.044915750404640076
                    0.07779640160194509 -0.04491575040464015 0.0 0.08983152841195585]
            found (search/find-refs :granule
                                    {:polygon (apply st/search-poly coords)
                                     :provider "PROV1"})]
        (d/refs-match? [] found)))

    (testing "Orbit search will not throw exception when no segment candidate is found"
      (let [coords [0.0 89.08983152841175 -4.258184122587005 88.95219247122816
                    4.258184122587003 88.95219247122816 0.0 89.08983152841175]
            found (search/find-refs :granule
                                    {:polygon (apply st/search-poly coords)
                                     :provider "PROV1"})]
        (d/refs-match? [] found)))))

(deftest orbit-bug-CMR-5007
  (let [coll (d/ingest-concept-with-metadata-file "CMR-5007/dif10_Collection_C1239966837-GES_DISC.xml"
                                                  {:provider-id "PROV1"
                                                   :concept-type :collection
                                                   :native-id "GES_DISC-collection"
                                                   :format-key :dif10})
        granule (d/ingest-concept-with-metadata-file "CMR-5007/echo10_Granlue_G1278223734-GES_DISC.xml"
                                                     {:provider-id "PROV1"
                                                      :concept-type :granule
                                                      :concept-id "C4-PROV1"
                                                      :native-id "GES_DISC-granule"
                                                      :format-key :echo10})]
    (index/wait-until-indexed)

    (testing "Polygon search for granules near poles."
      (u/are3 [items coords params]
              (let [found (search/find-refs
                            :granule
                            (merge {:polygon
                                    (apply st/search-poly coords)
                                    :provider "PROV1"
                                    :page-size 50} params))
                    matches? (d/refs-match? items found)]
                (when-not matches?
                  (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                  (println "Actual:" (->> found :refs (map :name) sort pr-str)))
                matches?)

        "CMR-5007: Search north pole with altitude > 86.52 won't throw 500 error."
        [granule] [-30,87 90,87 150,87 -150,87 -90,87 -30,87] nil

        "CMR-5007: Search south pole with altitude < -86.52 won't throw 500 error."
        [granule] [-30,-87 -90,-87 -150,-87 150,-87 90,-87 -30,-87] nil))

    (testing "line searches"
      (u/are3 [items coords params]
              (let [found (search/find-refs
                            :granule
                            {:line (codec/url-encode (l/ords->line-string :geodetic coords))
                             :provider "PROV1"
                             :page-size 50})
                    matches? (d/refs-match? items found)]
                (when-not matches?
                  (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                  (println "Actual:" (->> found :refs (map :name) sort pr-str)))
                matches?)
        "Line search with altitude < -86.52 won't throw 500 error."
        [granule] [-180,-86.6,0,0] nil

        "Line search with altitude > 86.52 won't throw 500 error."
        [granule] [0,0 180, 86.6] nil))))

;; This tests searching for bounding boxes or polygons that cross the start circular
;; latitude of the collection with fractional orbit granules. This was added to test
;; the fix for this issue as described in CMR-1168 and uses the collection/granules from
;; that issue.
(deftest fractional-orbit-non-zero-start-clat
  (let [orbit-parameters {:swath-width 2
                          :period 96.7
                          :inclination-angle 94.0
                          :number-of-orbits 0.25
                          :start-circular-latitude 50.0}
        coll1 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "orbit-params1"
                           :spatial-coverage (dc/spatial {:gsr :orbit
                                                          :orbit orbit-parameters})}))
        g1 (make-gran coll1 "gran1" 70.80471 50.0 :asc  50.0 :desc)
        g2 (make-gran coll1 "gran2" 70.80471 50.0 :desc -50.0 :desc)
        g3 (make-gran coll1 "gran3" 70.80471 -50.0 :desc -50.0 :asc)
        g4 (make-gran coll1 "gran4" 70.80471 -50.0 :asc 50 :asc)]
    (index/wait-until-indexed)

    (testing "Bounding box"
      (u/are2 [items wnes params]
              (let [found (search/find-refs
                            :granule
                            (merge {:bounding-box
                                    (codec/url-encode (apply m/mbr wnes))
                                    :provider "PROV1"
                                    :page-size 50} params))
                    matches? (d/refs-match? items found)]
                (when-not matches?
                  (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                  (println "Actual:" (->> found :refs (map :name) sort pr-str)))
                matches?)

              "Rectangle crossing the start circular latitude of the collection"
              [g1] [54.844 52.133 97.734 25.165] nil
              "Rectangle crossing the start circular latitdue of the colletion on the other side
              of the earth"
              [g1 g2] [-125.156 52.133 -82.266 25.165] nil
              "Rectangle crossing the start circular latitude * -1"
              [g3 g4] [54.844 -25.165 97.734 -52.133] nil
              "Rectangle touching the north pole"
              [g1] [-90 90 90 85] nil
              "Rectangle touching the south pole"
              [g3] [0 -85 180 -90] nil
              "Rectangle crossing the antimeridian and the collection start circular latitude"
              [g1 g2] [125.156 52.133 -82.266 25.165] nil
              "The whole earth"
              [g1 g2 g3 g4] [-180 90 180 -90] nil))
    (testing "Polygon"
      (u/are2 [items coords params]
              (let [found (search/find-refs
                            :granule
                            (merge {:polygon
                                    (apply st/search-poly coords)
                                    :provider "PROV1"
                                    :page-size 50} params))
                    matches? (d/refs-match? items found)]
                (when-not matches?
                  (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                  (println "Actual:" (->> found :refs (map :name) sort pr-str)))
                matches?)

              "Rectangle crossing the start circular latitude of the collection"
              [g1] [54.844,25.165 97.734,25.165 97.734,52.133 54.844,52.133 54.844,25.165] nil
              "Rectangle crossing the start circular latitdue of the colletion on the other side
              of the earth"
              [g1 g2] [-125.156,52.133 -125.156,25.165 -82.266,25.165 -82.266,52.133 -125.156,52.133] nil
              "Rectangle crossing the start circular latitude * -1"
              [g3 g4] [97.734,-52.133 97.734,-25.165 54.844,-25.165 54.844,-52.133 97.734,-52.133] nil
              "Triangle touching the north pole"
              [g1] [0,90 -45,85 45,85 0,90] nil
              "Pentagon touching the south pole"
              [g3 g4] [97.734,-52.133 97.734,-25.165 54.844,-25.165 54.844,-52.133 97.734,-90 97.734,-52.133] nil
              "Rectangle crossing the antimeridian and the collection start circular latitude"
              [g1 g2] [125.156,52.133 125.156,25.165 -82.266,25.165 -82.266,52.133 125.156,52.133] nil
              "Pentagon over the north pole"
              [g1] [0,80 72,80 144,80 -144,80 -72,80 0,80] nil
              "Pentagon over the south pole"
              [g3] [0,-80 -72,-80 -144,-80 144,-80 72,-80 0,-80] nil))))

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
        ;; orbit parameters with missing value to test defaulting to 0
        op3-bad {:swath-width 2
                 :period 96.7
                 :inclination-angle 94
                 :number-of-orbits 0.25
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
    (index/wait-until-indexed)

    (testing "bounding rectangle searches"
      (u/are2 [items wnes params]
              (let [found (search/find-refs
                            :granule
                            (merge {:bounding-box
                                    (codec/url-encode (apply m/mbr wnes))
                                    :provider "PROV1"
                                    :page-size 50} params))
                    matches? (d/refs-match? items found)]
                (when-not matches?
                  (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                  (println "Actual:" (->> found :refs (map :name) sort pr-str)))
                matches?)

              "Orbits crossing a rectangle over the equator and anti-meridian"
              [g2 g7] [145 45 -145 -45] nil
              "Orbits crossing a rectangle over the equator and meridian"
              [g1 g3 g8] [-45 45 45 -45] nil
              "Orbits crossing a rectangle in the western hemisphere near the north pole"
              [g5] [-90 89 -45 85] nil
              "Orbits crossing a rectangle in the southern hemisphere crossing the anti-meridian"
              [g2 g3 g4 g7] [145 -45 -145 -85] nil
              "Specifying parent collection"
              [g2] [145 45 -145 -45] {:concept-id (:concept-id coll1)}))

    (testing "point searches"
      (are [items lon_lat params]
           (let [found (search/find-refs :granule {:point (codec/url-encode (apply point/point lon_lat))
                                                   :provider "PROV1"
                                                   :page-size 50})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :granule-ur) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           [g3] [-45,45] nil
           [g1] [0,-20] nil
           [g2] [-180,0] nil))


    (testing "line searches"
      (are [items coords params]
           (let [found (search/find-refs
                         :granule
                         {:line (codec/url-encode (l/ords->line-string :geodetic coords))
                          :provider "PROV1"
                          :page-size 50})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :granule-ur) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           ;; Line crossing prime meridian and equator
           [g1 g8] [-45,-45 45,45] nil))
           ;; Line crossing the antimeridian - This case gets different results from catalog-rest
           ;; (returns g2 & g7) and will fail if uncommented. Need to determine if it
           ;; is supposed to return both granules or not.
           ; [g2] [179,-45 -170, 30] nil


    (testing "polygon searches"
      (u/are2 [items coords params]
              (let [found (search/find-refs
                            :granule
                            (merge {:polygon
                                    (apply st/search-poly coords)
                                    :provider "PROV1"
                                    :page-size 50} params))
                    matches? (d/refs-match? items found)]
                (when-not matches?
                  (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                  (println "Actual:" (->> found :refs (map :name) sort pr-str)))
                matches?)

              "Triangle crossing prime meridian"
              [g1 g8] [-45,-45 45,-45 45,0, -45,-45] nil
              "Pentagon over the north pole"
              [g1 g2 g3 g4 g5 g9] [0,80 72,80 144,80 -144,80 -72,80 0,80] nil
              "Concave polygon crossing antimeridian and equator"
              [g2 g4 g7 g9] [170,-70 -170,-80 -175,20 -179,-10 175,25 170,-70] nil))))

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
    (index/wait-until-indexed)

    (testing "bounding rectangle searches"
      (u/are2 [items wnes params]
              (let [found (search/find-refs
                            :granule
                            (merge {:bounding-box
                                    (codec/url-encode (apply m/mbr wnes))
                                    :provider "PROV1"
                                    :page-size 50} params))
                    matches? (d/refs-match? items found)]
                (when-not matches?
                  (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                  (println "Actual:" (->> found :refs (map :name) sort pr-str)))
                matches?)

              "Search large box that finds the granule"
              [g1] [-134.648 69.163 76.84 65.742] {:concept-id (:concept-id coll1)}
              "Search near equator that doesn't intersect granule"
              [] [0 1 1 0] {:concept-id (:concept-id coll1)}
              "Search near equator that intersects granule"
              [g1] [1 11 15 1] {:concept-id (:concept-id coll1)}
              "Search near north pole - FIXME"
              [] [1 89.5 1.5 89] {:concept-id (:concept-id coll1)}
              "Search for granules with exactly one orbit does not match areas seen on the second pass"
              [] [175.55 38.273 -164.883 17.912] {:concept-id (:concept-id coll2)}))))
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
              ; "Broken test"
              ; [] [0 1 6 0] nil

(deftest ascending-crossing-precision-test
  (let [coll (d/ingest-concept-with-metadata-file "iso-samples/CMR-5269-IsoMendsCollection.xml"
                                                  {:provider-id "PROV1"
                                                   :concept-type :collection
                                                   :format-key :iso19115})
        gran (d/ingest-concept-with-metadata-file "iso-samples/CMR-5269-IsoSmapGranule.xml"
                                                  {:provider-id "PROV1"
                                                   :concept-type :granule
                                                   :format-key :iso-smap})
        _ (index/wait-until-indexed)
        json-response (search/find-concepts-json :granule {:concept-id (:concept-id gran)})
        granule-json (-> json-response :results :entries first)
        expected-points-in-polygons [(point/point 25.235719457640535 -21.19078458692315)
                                     (point/point 23.13297924763377 -39.771484792279814)
                                     (point/point 22.646805419273353 -43.610681332179986)
                                     (point/point 22.199982651743795 -43.5950693405511)
                                     (point/point 22.711995770988835 -39.7567775481513)
                                     (point/point 24.8885475222218 -21.178659983890096)
                                     (point/point 25.235719457640535 -21.19078458692315)]
        expected-ascending-crossing -140.637396
        expected-equator-crossings [-140.637396]
        actual-ascending-crossing (-> granule-json :orbit :ascending-crossing)
        actual-equator-crossings (->> granule-json
                                      :orbit-calculated-spatial-domains
                                      (keep :equator-crossing-longitude))
        actual-points-in-polygons (->> granule-json
                                       :shapes
                                       (mapcat :rings)
                                       (mapcat :points))]
    (is (= expected-points-in-polygons actual-points-in-polygons))
    (is (= expected-ascending-crossing actual-ascending-crossing))
    (is (= expected-equator-crossings actual-equator-crossings))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Visualizations
;;

;; Ingests for visualizations


(defn- ingest-orbit-coll-and-granule-swath
  []
  (let [coll (d/ingest "PROV1"
                       (dc/collection
                         {:entry-title "orbit-params1"
                          :spatial-coverage (dc/spatial {:gsr :orbit
                                                         :orbit {:inclination-angle 98.2
                                                                 :period 100.0
                                                                 :swath-width 2
                                                                 :start-circular-latitude 0
                                                                 :number-of-orbits 0.25}})}))]

    [coll (make-gran coll "gran1" 88.0 0 :asc 88 :asc)]))


(defn- ingest-orbit-coll-and-granule
  []
  (let [coll (d/ingest "PROV1"
                       (dc/collection
                         {:entry-title "orbit-params1"
                          :spatial-coverage (dc/spatial {:gsr :orbit
                                                         :orbit {:swath-width 2
                                                                 :period 96.7
                                                                 :inclination-angle 94
                                                                 :number-of-orbits 0.25
                                                                 :start-circular-latitude
                                                                 50}})}))]
                                                                 ; -50
                                                                 ; 0


    [coll (make-gran coll "gran1"
                     70.80471 ; ascending crossing
                     -50 :asc ; start lat, start dir
                     50 :asc ; end lat end dir
                     {:beginning-date-time "2003-09-27T17:03:27.000000Z"
                      :ending-date-time "2003-09-27T17:30:23.000000Z"
                      :orbit-calculated-spatial-domains [{:orbit-number 3838
                                                          :equator-crossing-longitude 70.80471
                                                          :equator-crossing-date-time "2003-09-27T15:40:15Z"}
                                                         {:orbit-number 3839
                                                          :equator-crossing-longitude 46.602737
                                                          :equator-crossing-date-time "2003-09-27T17:16:56Z"}]})]))


(defn- ingest-orbit-coll-and-granules-north-pole
  []
  (let [coll (d/ingest "PROV1"
                       (dc/collection
                         {:entry-title "orbit-params1"
                          :spatial-coverage (dc/spatial {:gsr :orbit
                                                         :orbit {:swath-width 2
                                                                 :period 96.7
                                                                 :inclination-angle 94
                                                                 :number-of-orbits 0.25
                                                                 :start-circular-latitude
                                                                 ;50
                                                                 -50}})}))]
                                                                 ; 0


    [coll (make-gran coll "gran1" 31.48193 50 :desc -50 :desc)]))

(defn- ingest-orbit-coll-and-granules-prime-meridian
  []
  (let [coll (d/ingest "PROV1"
                       (dc/collection
                         {:entry-title "orbit-params1"
                          :spatial-coverage (dc/spatial {:gsr :orbit
                                                         :orbit {:swath-width 2
                                                                 :period 96.7
                                                                 :inclination-angle 94
                                                                 :number-of-orbits 0.25
                                                                 :start-circular-latitude
                                                                 -50}})}))]

    [coll (make-gran coll "gran1" 7.28116 -50 :asc 50 :asc)]))

(defn- ingest-CMR-4722-data
  []
  (let [coll1 (d/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-collection.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :native-id "orbit3"
                                                    :format-key :dif10})
        g1 (d/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule.xml"
                                                {:provider-id "PROV1"
                                                 :concept-type :granule
                                                 :concept-id "C1-PROV1"
                                                 :native-id "granule1"
                                                 :format-key :echo10})]
    [coll1 g1]))

(defn- ingest-CMR-5765-data-1
  []
  (let [coll1 (d/ingest-concept-with-metadata-file "CMR-5765/C1200190069-EDF_DEV05.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :native-id "orbit3"
                                                    :format-key :iso19115})
        g1 (d/ingest-concept-with-metadata-file "CMR-5765/G1200355212-EDF_DEV05.xml"
                                                {:provider-id "PROV1"
                                                 :concept-type :granule
                                                 :concept-id "C1-PROV1"
                                                 :native-id "granule1"
                                                 :format-key :iso-smap})]
    [coll1 g1]))

(defn- ingest-CMR-5765-data-2
  []
  (let [coll1 (d/ingest-concept-with-metadata-file "CMR-5765/C1200190069-EDF_DEV05.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :native-id "orbit3"
                                                    :format-key :iso19115})
        g1 (d/ingest-concept-with-metadata-file "CMR-5765/G1200355211-EDF_DEV05.xml"
                                                {:provider-id "PROV1"
                                                 :concept-type :granule
                                                 :concept-id "C1-PROV1"
                                                 :native-id "granule1"
                                                 :format-key :iso-smap})]
    [coll1 g1]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Visualization code

(defn- mbr-finds-granule?
  [mbr]
  (let [resp (search/find-refs :granule {:bounding-box (codec/url-encode mbr) :page-size 0 :provider "PROV1"})]
    (> (:hits resp) 0)))

(defn- polygon-finds-granule?
  [coords]
  (let [resp (search/find-refs
               :granule
               {:provider "PROV1"
                :polygon (apply st/search-poly coords)
                :page-size 0})]
    (> (:hits resp) 0)))

(defn- create-mbrs
  "Split an area on the earth into a bunch of bounding rectangles and return them as a list"
  ([]
   (create-mbrs -180.0 180.0 -90.0 90.0 3.0))
  ([min-lon max-lon min-lat max-lat step]
   (for [[west east] (partition 2 1 (range min-lon (inc max-lon) step))
         [south north] (partition 2 1 (range min-lat (inc max-lat) step))]
     (m/mbr west north east south))))

(defn- mbr->polygon-coords
  "Get the coordinates from the corners of an mbr"
  [mbr]
  (let [corners (-> (m/corner-points mbr)
                    distinct
                    reverse)
        points (conj (vec corners) (first corners))]
    (point/points->ords points)))


(defn- create-polygons
  "Split an area on the earth into a bunch of box shaped polygons and return them as a list"
  ([]
   (create-polygons -180.0 180.0 -90.0 90.0 30.0))
  ([min-lon max-lon min-lat max-lat step]
   (let [mbrs (create-mbrs min-lon max-lon min-lat max-lat step)]
     (map mbr->polygon-coords mbrs))))


(comment

  ;; 1. Perform setup
  ;; evaluate this block
  ;; First modify the metadata in ingest-orbit-coll-and-granule if you want.
  (do
    (dev-sys-util/reset)
    (taoensso.timbre/set-level! :warn) ; turn down log level
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})
    ; (ingest-orbit-coll-and-granules-north-pole)
    ; (ingest-orbit-coll-and-granules-prime-meridian)
    ; (ingest-orbit-coll-and-granule)
    ; (ingest-orbit-coll-and-granule-swath)
    ; (ingest-CMR-4722-data))
    ; (ingest-CMR-5765-data-1)
    (ingest-CMR-5765-data-2))

  ;; Figure out how many mbrs we're going to search with to get an idea of how long things will take
  (count (create-mbrs 45.0 90.0 -55.0 55.0 3))
  (count (create-mbrs))

  ;; 2. Evaluate this block to find all the mbrs.
  ;; It will print out "Elapsed time: XXXX msecs" when it's done
  (def matching-mbrs
    "Creates mbrs all over the globe as search areas. Returns any mbrs which find granules in the
    local system. Takes awhile to run, so performed as a future."
    (future (time (doall (filter mbr-finds-granule? (create-mbrs -180.0 180.0 -90.0 90.0 3))))))


  ;; Or evaluate this block to use polygons instead of mbrs
  (def matching-polys
    (future (time (doall (keep (fn [coords] (when (polygon-finds-granule? coords)
                                              (umm-s/set-coordinate-system
                                                :geodetic
                                                (apply st/polygon coords))))
                               (create-polygons -46.0 46.0 -89.0 89.0 3))))))

  (mbr-finds-granule? (m/mbr 40 30 45 24))

  ;; How many were found? This will block on the future
  (count @matching-mbrs)
  (count @matching-polys)

  ;; 3. Evaluate a block like this to save the mbrs to kml and open in google earth.
  ;; Google Earth will open when you evaluate it. (as long as you've installed it)
  ;; You can give different tests unique names. Otherwise it will overwrite the file.
  (kml/display-shapes @matching-mbrs "start_circ_pos_50.kml")

  (kml/display-shapes @matching-polys "start_circ_pos_50_poly.kml")

  ;; visualize the kml representation
  (do (spit "granule_kml.kml"
            (:out (clojure.java.shell/sh "curl" "--silent" "http://localhost:3003/granules.kml")))
    (clojure.java.shell/sh "open" "granule_kml.kml")))
