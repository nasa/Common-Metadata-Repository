(ns cmr.sizing.spatial)

(defn tiling-type->size
  [^String tiling-type]
  (case tiling-type
    "MODIS Tile EASE" [10 10]
    "MODIS Tile SIN" [10 10]
    [360 180]))

(defn ->square-degrees
  [ranges]
  (Math/abs (reduce * 1 ranges)))

(defn tiling-type->square-degrees
  [^String tiling-type]
  (->square-degrees (tiling-type->size tiling-type)))

(defn bbox->square-degrees
  ""
  [bbox]
  (when bbox
    (let [[ll-lon ll-lat ur-lon ur-lat] bbox]
      (when (and ll-lon ll-lat ur-lon ur-lat)
        (->square-degrees (- ur-lon ll-lon)
                          (- ur-lat ll-lat))))))

(defn estimate-size
  [format-estimate results]
  (let [coll (:collection-metadata results)
        tiling-type (get-in coll [:TilingIdentificationSystems
                                  :TilingIdentificationSystemName])
        tt-sq-degs (tiling-type->square-degrees tiling-type)
        bbox-sq-degs (bbox->square-degrees
                      (get-in results [:params :bounding-box]))]
    (cond (nil? bbox-sq-degs)
          format-estimate

          (> tt-sq-degs bbox-sq-degs)
          (* format-estimate (/ bbox-sq-degs tt-sq-degs))

          :else
          format-estimate)))
