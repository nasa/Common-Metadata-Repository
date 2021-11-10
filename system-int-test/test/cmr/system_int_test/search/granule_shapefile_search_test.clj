(ns cmr.system-int-test.search.granule-shapefile-search-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [cmr.common-app.api.routes :as routes]
   [cmr.common.log :refer [debug]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util :refer [are3]]
   [cmr.common-app.test.side-api :as side]
   [cmr.search.services.parameters.converters.shapefile :as shapefile]
   [cmr.search.middleware.shapefile :as shapefile-middleware]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm.umm-spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order.
  The polygon will be closed automatically."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(def formats
  "Shapfile formats to be tested"
  {"ESRI" {:extension "zip" :mime-type mt/shapefile}
   "GeoJSON" {:extension "geojson" :mime-type mt/geojson}
   "KML" {:extension "kml" :mime-type mt/kml}})

(deftest granule-shapefile-failure-cases
  (side/eval-form `(shapefile/set-enable-shapefile-parameter-flag! true))
  (let [saved-shapefile-max-size (shapefile-middleware/max-shapefile-size)
        _ (side/eval-form `(shapefile-middleware/set-max-shapefile-size! 50000))
        saved-shapefile-max-features (shapefile/max-shapefile-features)
        _ (side/eval-form `(shapefile/set-max-shapefile-features! 2))
        saved-shapefile-max-points (shapefile/max-shapefile-points)
        _ (side/eval-form `(shapefile/set-max-shapefile-points! 50))]

    (testing "ESRI Shapefile Failure cases"
      (are3 [shapefile additional-params regex]
        (is (re-find regex
                      (first (:errors (search/find-refs-with-multi-part-form-post
                                        :granule
                                        (let [params [{:name "shapefile"
                                                       :content (io/file (io/resource (str "shapefiles/" shapefile)))
                                                       :mime-type "application/shapefile+zip"}]]
                                          (if (seq additional-params)
                                            (conj params additional-params)
                                            params)))))))

        "All granules query"
        "box.zip" {} #"The CMR does not allow querying across granules in all collections with a spatial condition"

        "Corrupt zip file"
        "corrupt_file.zip" {:name "provider" :content "PROV1"} #"Error while uncompressing zip file: invalid END header \(bad central directory offset\)"

        "Missing .shp file"
        "missing_shapefile_shp.zip" {:name "provider" :content "PROV1"} #"Incomplete shapefile: missing .shp file"

        "Shapefile is too big"
        "too_big.zip" {:name "provider" :content "PROV1"} #"Shapefile size exceeds the 50000 byte limit"

        "Shapefile has too many features"
        "too_many_features.zip" {:name "provider" :content "PROV1"} #"Shapefile feature count \[3\] exceeds the 2 feature limit"

        "Shapefile has too many points"
        "too_many_points.zip" {:name "provider" :content "PROV1"} #"Number of points in shapefile exceeds the limit of 50"

        "Shapefile has no features"
        "no_features.zip" {:name "provider" :content "PROV1"} #"Shapefile has no features"))


    (testing "GeoJSON Failure cases"
      (are3 [shapefile additional-params regex]
        (is (re-find regex
                      (first (:errors (search/find-refs-with-multi-part-form-post
                                        :granule
                                        (let [params [{:name "shapefile"
                                                       :content (io/file (io/resource (str "shapefiles/" shapefile)))
                                                       :mime-type "application/geo+json"}]]
                                          (if (seq additional-params)
                                            (conj params additional-params)
                                            params)))))))

        "All granules query"
        "polygon_with_hole.geojson" {} #"The CMR does not allow querying across granules in all collections with a spatial condition"

        "Failed to parse shapefile"
        "invalid_json.geojson" {:name "provider" :content "PROV1"} #"Failed to parse shapefile"

        "Shapefile has too many features"
        "too_many_features.geojson" {:name "provider" :content "PROV1"} #"Shapefile feature count \[3\] exceeds the 2 feature limit"

        "Shapefile has too many points"
        "too_many_points.geojson" {:name "provider" :content "PROV1"} #"Number of points in shapefile exceeds the limit of 50"

        "Shapefile has no features"
        "no_features.geojson" {:name "provider" :content "PROV1"} #"Shapefile has no features"))


    (testing "KML Failure cases"
      (are3 [shapefile additional-params regex]
        (is (re-find regex
                      (first (:errors (search/find-refs-with-multi-part-form-post
                                        :granule
                                        (let [params [{:name "shapefile"
                                                       :content (io/file (io/resource (str "shapefiles/" shapefile)))
                                                       :mime-type "application/vnd.google-earth.kml+xml"}]]
                                          (if (seq additional-params)
                                            (conj params additional-params)
                                            params)))))))

        "All granules query"
        "polygon_with_hole.kml" {} #"The CMR does not allow querying across granules in all collections with a spatial condition"

        "Failed to parse kml file"
        "invalid.kml" {:name "provider" :content "PROV1"} #"Failed to parse shapefile"

        "Shapefile has too many features"
        "too_many_features.kml" {:name "provider" :content "PROV1"} #"Shapefile feature count \[3\] exceeds the 2 feature limit"

        "Shapefile has too many points"
        "too_many_points.kml" {:name "provider" :content "PROV1"} #"Number of points in shapefile exceeds the limit of 50"

        "Shapefile has no features"
        "no_features.kml" {:name "provider" :content "PROV1"} #"Shapefile has no features"))


    (side/eval-form `(shapefile-middleware/set-max-shapefile-size! ~saved-shapefile-max-size))
    (side/eval-form `(shapefile/set-max-shapefile-features! ~saved-shapefile-max-features))
    (side/eval-form `(shapefile/set-max-shapefile-points! ~saved-shapefile-max-points))))

(deftest granule-shapefile-search-test
  (side/eval-form `(shapefile/set-enable-shapefile-parameter-flag! true))
  (let [geodetic-coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                                                                                    :EntryTitle "E1"
                                                                                    :ShortName "S1"
                                                                                    :Version "V1"}))
        cartesian-coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:SpatialExtent (data-umm-c/spatial {:gsr "CARTESIAN"})
                                                                                     :EntryTitle "E2"
                                                                                     :ShortName "S2"
                                                                                     :Version "V2"}))
        geodetic-coll-cid (get-in geodetic-coll [:concept-id])
        cartesian-coll-cid (get-in cartesian-coll [:concept-id])
        make-gran (fn [ur & shapes]
                    (let [shapes (map (partial umm-s/set-coordinate-system :geodetic) shapes)]
                      (d/ingest "PROV1" (dg/granule-with-umm-spec-collection geodetic-coll geodetic-coll-cid
                                                    {:granule-ur ur
                                                     :spatial-coverage (apply dg/spatial shapes)}))))
        make-cart-gran (fn [ur & shapes]
                         (let [shapes (map (partial umm-s/set-coordinate-system :cartesian) shapes)]
                           (d/ingest "PROV1" (dg/granule-with-umm-spec-collection cartesian-coll cartesian-coll-cid
                                                         {:granule-ur ur
                                                          :spatial-coverage (apply dg/spatial shapes)}))))

        ;; Lines
        normal-line (make-gran "normal-line" (l/ords->line-string :geodetic [22.681 -8.839, 18.309 -11.426, 22.705 -6.557]))
        along-am-line (make-gran "along-am-line" (l/ords->line-string :geodetic [-180 0 -180 85]))
        normal-line-cart (make-cart-gran "normal-line-cart" (l/ords->line-string :cartesian [16.439 -13.463, 31.904 -13.607, 31.958 -10.401]))

        ;; Bounding rectangles
        whole-world (make-gran "whole-world" (m/mbr -180 90 180 -90))
        touches-np (make-gran "touches-np" (m/mbr 45 90 55 70))
        touches-sp (make-gran "touches-sp" (m/mbr -160 -70 -150 -90))
        across-am-br (make-gran "across-am-br" (m/mbr 170 10 -170 -10))
        normal-brs (make-gran "normal-brs"
                              (m/mbr 10 10 20 0)
                              (m/mbr -20 0 -10 -10))

        ;; Geodetic Polygons
        wide-south (make-gran "wide-south" (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30))
        across-am-poly (make-gran "across-am-poly" (polygon 170 -10, -175 -10, -170 10, 175 10, 170 -10))
        on-np (make-gran "on-np" (polygon 45 85, 135 85, -135 85, -45 85, 45 85))
        on-sp (make-gran "on-sp" (polygon -45 -85, -135 -85, 135 -85, 45 -85, -45 -85))

        ;; polygon with holes
        outer (umm-s/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (umm-s/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (umm-s/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes (make-gran "polygon-with-holes" (poly/polygon [outer hole1 hole2]))

        ;; Cartesian Polygons
        wide-south-cart (make-cart-gran "wide-south-cart" (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30))
        very-wide-cart (make-cart-gran "very-wide-cart" (polygon -180 40, -180 35, 180 35, 180 40, -180 40))
        very-tall-cart (make-cart-gran "very-tall-cart" (polygon -160 90, -160 -90, -150 -90, -150 90, -160 90))

        outer-cart (umm-s/ords->ring -5.26 -22.59 11.56 -22.77 10.47 -11.29 -5.86 -11.37 -5.26 -22.59)
        hole1-cart (umm-s/ords->ring 6.95 -17.95 2.98 -17.94 3.92 -20.08 6.95 -17.95)
        hole2-cart (umm-s/ords->ring 5.18 -13.08 -1.79 -12.99 -2.65 -15 4.29 -14.95 5.18 -13.08)
        polygon-with-holes-cart (make-cart-gran "polygon-with-holes-cart" (poly/polygon [outer-cart hole1-cart hole2-cart]))

        ;; Points
        washington-dc (make-gran "washington-dc" (p/point -77 38.9))
        richmond (make-gran "richmond" (p/point -77.4 37.54))
        north-pole (make-gran "north-pole" (p/point 0 90))
        south-pole (make-gran "south-pole" (p/point 0 -90))
        am-point (make-gran "am-point" (p/point 180 22))]
    (index/wait-until-indexed)

    (doseq [fmt (keys formats)
            :let [{extension :extension mime-type :mime-type} (get formats fmt)]]
      (testing (format "Search by %s shapefile" fmt)
        (are3 [shapefile items]
              (let [found (search/find-refs-with-multi-part-form-post
                            :granule
                            [{:name "shapefile"
                              :content (io/file (io/resource (str "shapefiles/" shapefile "." extension)))
                              :mime-type mime-type}
                             {:name "provider"
                              :content "PROV1"}])]
                (d/assert-refs-match items found))

          "Single Polygon box around VA and DC"
          "box" [whole-world very-wide-cart washington-dc richmond]

          "Single Polygon box over North pole"
          "np_poly" [north-pole touches-np on-np whole-world very-tall-cart along-am-line]

          "Single Polygon over Antarctica"
          "antarctica" [south-pole touches-sp on-sp whole-world very-tall-cart]

          "Single Polygon over Southern Africa"
          "southern_africa" [whole-world polygon-with-holes polygon-with-holes-cart normal-line normal-line-cart normal-brs wide-south-cart]

          "Single Polygon around Virgina with hole around DC"
          "polygon_with_hole" [whole-world very-wide-cart richmond]

          "Single feature, multiple polygons around DC and Richnmond"
          "multi_poly" [whole-world very-wide-cart washington-dc richmond]

          "Multiple feature, single Polygons around DC and Richnmond"
          "multi_feature" [whole-world very-wide-cart washington-dc richmond]

          "Polygon across the antimeridian"
          "antimeridian" [whole-world across-am-poly across-am-br am-point very-wide-cart along-am-line]

          "Line near North pole"
          "np_line" [on-np whole-world]

          "Line from DC to Richmond"
          "dc_richmond_line" [whole-world very-wide-cart washington-dc richmond]

          "Single Point Washington DC"
          "single_point_dc" [whole-world very-wide-cart washington-dc]))

      (testing (format "Scrolling with results on first page for %s shapefile" fmt)
        (let [expected-items [whole-world touches-sp on-sp very-tall-cart south-pole]
              {:keys [hits headers] :as initial-scroll-request}
              (search/find-refs-with-multi-part-form-post
                :granule
                [{:name "shapefile"
                  :content (io/file (io/resource (str "shapefiles/antarctica." extension)))
                  :mime-type mime-type}
                 {:name "provider"
                   :content "PROV1"}
                 {:name "scroll"
                   :content "true"}
                 {:name "page_size"
                   :content "1"}])
              scroll-id (:CMR-Scroll-Id headers)]
          (d/assert-refs-match [(first expected-items)] initial-scroll-request)
          (doseq [item (rest expected-items)
                  :let [refs (search/find-refs
                              :granule
                              {:scroll true}
                              {:headers {routes/SCROLL_ID_HEADER scroll-id}})]]
            (d/assert-refs-match [item] refs))
          (search/clear-scroll scroll-id)))

      (testing (format "Deferred scrolling for %s shapefile" fmt)
        (let [expected-items [whole-world very-wide-cart washington-dc richmond]
              {:keys [hits headers]}
              (search/find-refs-with-multi-part-form-post
                :granule
                [{:name "shapefile"
                  :content (io/file (io/resource (str "shapefiles/box." extension)))
                  :mime-type mime-type}
                 {:name "provider"
                   :content "PROV1"}
                 {:name "scroll"
                   :content "defer"}
                 {:name "page_size"
                   :content "1"}])
              scroll-id (:CMR-Scroll-Id headers)]
          (doseq [item expected-items
                  :let [refs (search/find-refs
                              :granule
                              {:scroll true}
                              {:headers {routes/SCROLL_ID_HEADER scroll-id}})]]
            (d/assert-refs-match [item] refs))
          (search/clear-scroll scroll-id))))))
