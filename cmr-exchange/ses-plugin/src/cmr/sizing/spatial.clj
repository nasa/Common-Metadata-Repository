(ns cmr.sizing.spatial
  (:require
    [taoensso.timbre :as log]))

(defn tiling-type->size
  "Convert the tiling type to height and width, with can be used calculating square degrees."
  [^String tiling-type]
  (case tiling-type
    "EASE-Grid" [10 10]
    "SIN Grid" [10 10]
    "MODIS Tile EASE" [10 10]
    "MODIS Tile SIN" [10 10]
    [360 180]))

(defn ->square-degrees
  "Calculate square degrees with given range, range is 2 numbers."
  [ranges]
  (Math/abs (reduce * 1 ranges)))

(defn tiling-type->square-degrees
  "Convert tiling-type to square degrees."
  [^String tiling-type]
  (->square-degrees (tiling-type->size tiling-type)))

(defn bbox->square-degrees
  "Convert bounding-box to square degrees."
  [bbox]
  (when bbox
    (let [[ll-lon ll-lat ur-lon ur-lat] bbox]
      (when (and ll-lon ll-lat ur-lon ur-lat)
        (->square-degrees [(- ur-lon ll-lon)
                           (- ur-lat ll-lat)])))))

(defn estimate-size
  "If the tiling square degrees is greater than the bounding-box square degrees, we want to
   adjust the estimate by the ratio of bounding-box square degrees to tiling square degrees.
   Otherwise, pass through the estimate."
  [format-estimate results]
  (let [;; For now we will determine the tiling-type by parsing the collections entry title.
        ;; Eventually, metadata proxy will be able to give us this value from out of the UMM.
        tiling-type (as-> (:collection-metadata results) value
                          (get value :dataset_id)
                          (re-find #"SIN Grid|EASE-Grid" value))
        tt-sq-degs (tiling-type->square-degrees tiling-type)
        bbox-sq-degs (bbox->square-degrees
                      (get-in results [:params :bounding-box]))]
    (log/info (format (str "request-id: %s format-estimate: %s tiling-type: %s tt-sq-degs: %s "
                           "bbox-sq-degs: %s")
                      (get-in results [:params :request-id]) format-estimate tiling-type
                      tt-sq-degs bbox-sq-degs))
    (cond (nil? bbox-sq-degs)
          format-estimate

          (> tt-sq-degs bbox-sq-degs)
          (* format-estimate (/ bbox-sq-degs tt-sq-degs))

          :else
          format-estimate)))
