(ns cmr.system-int-test.search.defershapetest
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common-app.api.routes :as routes]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.log :refer [debug]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.middleware.shapefile :as shapefile-middleware]
   [cmr.search.services.parameters.converters.shapefile :as shapefile]
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
  {"GeoJSON" {:extension "geojson" :mime-type mt/geojson}})

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

        ;; Bounding rectangles
        whole-world (make-gran "whole-world" (m/mbr -180 90 180 -90))
        almost-whole-world (make-gran "almost-whole-world" (m/mbr -179 89 179 -89))]
    (index/wait-until-indexed)

    (doseq [fmt (keys formats)
            :let [{extension :extension mime-type :mime-type} (get formats fmt)]]
      (testing (format "Search by %s shapefile" fmt)
        (are3 [shapefile items]
              (let [{:keys [hits headers] :as result1}
                    (search/find-refs-with-multi-part-form-post
                            :granule
                            [{:name "shapefile"
                              :content (io/file (io/resource (str "shapefiles/" shapefile "." extension)))
                              :mime-type mime-type}
                             {:name "provider"
                              :content "PROV1"}
                             {:name "scroll"
                              :content "true"}
                             {:name "page_size"
                              :content "1"}])
                    scroll-id (:CMR-Scroll-Id headers)
                    result2 (search/find-refs
                             :granule {:scroll true} {:headers {routes/SCROLL_ID_HEADER scroll-id}})]
                ; (println "Result1" result1)
                ; (println "HEADERS - Result1 headers" headers)
                ; (println "THE SCROLL ID" scroll-id)
                ; (println "Result2" result2)
                ; (println "Items are" items)
                (d/assert-refs-match [whole-world] result1)
                (d/assert-refs-match [almost-whole-world] result2))

          "Single Polygon box around VA and DC"
          "box" [whole-world almost-whole-world])))))
