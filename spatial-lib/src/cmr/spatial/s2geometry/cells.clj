(ns cmr.spatial.s2geometry.cells
  "Functions for working with S2 cells"
  (:require
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.polygon :as polygon]
   [cmr.spatial.point :as point]
   [cmr.spatial.line-string :as line-string]
   [clojure.string :as string])
  (:import
   (cmr.spatial.mbr Mbr)
   (cmr.spatial.polygon Polygon)
   (cmr.spatial.point Point)
   (cmr.spatial.line_string LineString)
   (com.google.common.geometry S2LatLng S2LatLngRect S2Loop S2Polygon S2RegionCoverer S2CellId S2Cell S2Polyline)
   (java.util ArrayList)
   (org.apache.logging.log4j LogManager)))


(defn all-child-positions
  "Returns all child positions for the given cell id and cell level. This is used to get the covering cells for the shape."
  [cell-id cell-level]
  ;; Loop from 1 to cell-level and concatenate each value of .childPosition to get the cell string
  (let [positions (map #(str (.childPosition cell-id %)) (range 1 (inc cell-level)))]
    (string/join positions)))

(defn cell-id->cell-string
  "Converts a cell id to a string. This is used to store the cell ids in a more compact form."
  [cell-id]
  (let [face (.face cell-id)
        level (.level cell-id)
        ;; Loop from 1 to cell-level and concatenate each value of the position to get the cell string
        positions (all-child-positions cell-id level)
        ;; pos (.childPosition cell-id cell-level)
        ]
    (format "%d/%s" face positions)))

(defn s2-rect->s2-polygon
  "Converts an S2LatLngRect to an S2Polygon. This is used to get the covering cells for the shape."
  [s2-rect]
  (try
    (let [swLatLng (.getVertex s2-rect 0)
          neLatLng (.getVertex s2-rect 2)
          swPoint (.toPoint swLatLng)
          sePoint (.toPoint (S2LatLng. (.lat swLatLng) (.lng neLatLng)))
          nePoint (.toPoint neLatLng)
          nwPoint (.toPoint (S2LatLng. (.lat neLatLng) (.lng swLatLng)))
          s2Points (ArrayList. ^Iterable [swPoint sePoint nePoint nwPoint])
          s2Loop (S2Loop. ^java.util.List s2Points)
          polygon (S2Polygon. s2Loop)]
      polygon)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to convert S2LatLngRect [%s] to S2Polygon" s2-rect) t)
      (throw (ex-info "An exception occurred converting S2LatLngRect to S2Polygon" {:s2-rect s2-rect} t)))))

(defn shape->s2polygon
  "Converts a shape to an S2Polygon. This is used to get the covering cells for the shape."
  [shape]
  (try
    (let [points (map #(S2LatLng/fromDegrees (:lat %) (:lon %)) (:points (first (:rings shape))))
          s2Points (ArrayList. ^Iterable (map #(.toPoint %) points))
          s2Loop (S2Loop. ^java.util.List s2Points)
          polygon (S2Polygon. s2Loop)]
      polygon)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to convert shape [%s] to S2Polygon" shape) t)
      (throw (ex-info "An exception occurred converting shape to S2Polygon" {:shape shape} t)))))

(defn shape->s2latlngrect
  "Converts a shape to an S2LatLngRect. This is used to get the covering cells for the shape."
  [shape]
  (try
    (let [south (:south shape)
          west (:west shape)
          north (:north shape)
          east (:east shape)
          swLatLng (S2LatLng/fromDegrees south west)
          neLatLng (S2LatLng/fromDegrees north east)
          s2latlngrect (S2LatLngRect. swLatLng neLatLng)]
      s2latlngrect)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to convert shape [%s] to S2LatLngRect" shape) t)
      (throw (ex-info "An exception occurred converting shape to S2LatLngRect" {:shape shape} t)))))

(defn s2polygon->cell-ids
  "Returns the cell ids for the given S2Polygon. This is used to get the covering cells for the shape."
  [s2-polygon cell-level]
  (try
    (let [first-point (.vertex (.loop s2-polygon 0) 0)
          output-cells (ArrayList.)
          _ (S2RegionCoverer/getSimpleCovering s2-polygon first-point cell-level output-cells)
          cells (seq output-cells)]
      cells)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for S2Polygon [%s]" s2-polygon) t)
      (throw (ex-info "An exception occurred getting covering cells for S2Polygon s2polygon->cell-ids" {:s2-polygon s2-polygon} t)))))

(defn s2latlngrect->cell-ids
  "Returns the cell ids for the given S2LatLngRect. This is used to get the covering cells for the shape."
  [s2-rect cell-level]
  (try
    (let [first-latlng (.getVertex s2-rect 0)
          first-point (.toPoint first-latlng)
          output-cells (ArrayList.)
          _ (S2RegionCoverer/getSimpleCovering s2-rect first-point cell-level output-cells)
          cells (seq output-cells)]
      cells)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for S2LatLngRect [%s]" s2-rect) t)
      (throw (ex-info "An exception occurred getting covering cells for S2LatLngRect" {:s2-rect s2-rect} t)))))

(defmulti shape->fancy-cell-ids
  "Returns the cell ids for the given shape. This is used to get the covering cells for the shape."
  (fn [shape _cell-level]
    (class shape)))

(defmethod shape->fancy-cell-ids :default
  [shape _cell-level]
  (throw (ex-info (format "shape->fancy-cell-ids Unsupported shape type [%s]" (class shape)) {:shape shape})))

(defmethod shape->fancy-cell-ids Polygon
  [shape cell-level]
  (try
    (let [s2-polygon (shape->s2polygon shape)
          cell-ids (s2polygon->cell-ids s2-polygon cell-level)
          cell-ids-string (map #(cell-id->cell-string %) cell-ids)
          ;_ (println "cell-ids-string class: " (class cell-ids-string) " cell-ids-string: " cell-ids-string)
          ]
      cell-ids-string)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for Shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells for Shape shape->fancy-cell-ids" {:shape shape} t)))))

(defmethod shape->fancy-cell-ids Mbr
  [shape cell-level]
  (try
    (let [s2-rect (shape->s2latlngrect shape)
          cell-ids (s2latlngrect->cell-ids s2-rect cell-level)
          cell-ids-string (map #(cell-id->cell-string %) cell-ids)
          ;_ (println "cell-ids-string class: " (class cell-ids-string) " cell-ids-string: " cell-ids-string)
          ]
      cell-ids-string)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for Shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells for Shape shape->fancy-cell-ids" {:shape shape} t)))))

(def my-coverer-configured
  (-> (S2RegionCoverer/builder)
      (.setMinLevel 5)
      (.setMaxLevel 15)
      (.setMaxCells 50)
      (.build)))

(defn s2polygon->cell-id-range
  "Returns the cell ids for the given S2Polygon. This is used to get the covering cells for the shape."
  [s2-polygon min-level max-level]
  (try
    (let [coverer (-> (S2RegionCoverer/builder)
                      (.setMinLevel min-level)
                      (.setMaxLevel max-level)
                      (.setMaxCells 1000)
                      (.build))
          ;; builder (S2RegionCoverer/builder)
          ;; _ (.setMinLevel builder min-level)
          ;; _ (.setMaxLevel builder max-level)
          ;; _ (.setMaxCells builder 400)
          ;; _ (println "builder: " builder)
          ;; covering (.getCovering builder s2-polygon)
          covering (.getCovering coverer s2-polygon)
          ;_ (println "covering: " covering)
          ]
      covering)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for S2Polygon [%s]" s2-polygon) t)
      (throw (ex-info "An exception occurred getting covering cells for S2Polygon s2polygon->cell-id-range" {:s2-polygon s2-polygon} t)))))

(defn s2rect->cell-ids-range
  "Returns the cell ids for the given S2LatLngRect. This is used to get the covering cells for the shape."
  [s2-rect min-level max-level]
  (try
    (let [coverer (-> (S2RegionCoverer/builder)
                      (.setMinLevel min-level)
                      (.setMaxLevel max-level)
                      (.setMaxCells 1000)
                      (.build))
          covering (.getCovering coverer s2-rect)]
      covering)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for S2LatLngRect [%s]" s2-rect) t)
      (throw (ex-info "An exception occurred getting covering cells for S2LatLngRect s2rect->cell-ids-range" {:s2-rect s2-rect} t)))))

(defn s2polyline->cell-ids
  "Returns the cell ids for the given S2Polyline. This is used to get the covering cells for the shape."
  [s2-polyline cell-level]
  (try
    (let [first-point (.vertex s2-polyline 0)
          output-cells (ArrayList.)
          _ (S2RegionCoverer/getSimpleCovering s2-polyline first-point cell-level output-cells)
          cells (seq output-cells)]
      cells)
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for S2Polyline [%s]" s2-polyline) t)
      (throw (ex-info "An exception occurred getting covering cells for S2Polyline" {:s2-polyline s2-polyline} t)))))

(defn cell-id->token
  "Converts a cell id to a token. This is used to store the cell ids in a more compact form."
  [cell-id]
  (.toToken ^S2CellId cell-id))

(defmulti get-s2-cell-ids-range
  "Returns the cell ids for the given shape and cell level range. This is used to get the covering cells for the shape."
  (fn [shape _min-level _max-level]
    (class shape)))

(defmethod get-s2-cell-ids-range :default
  [shape _min-level _max-level]
  (throw (ex-info (format "get-s2-cell-ids-range Unsupported shape type [%s]" (class shape)) {:shape shape})))

(defmethod get-s2-cell-ids-range Polygon
  [shape min-level max-level]
  (try
    (let [s2-polygon (shape->s2polygon shape)
          cell-ids (s2polygon->cell-id-range s2-polygon min-level max-level)
          cell-ids-string (map #(cell-id->cell-string %) cell-ids)
          ;_ (println "cell-ids-string: " cell-ids-string)
          grouped-cells (group-by #(.contains s2-polygon (S2Cell. %)) cell-ids)
          interior-cells (map #(str (cell-id->cell-string %)) (get grouped-cells true))
          exterior-cells (map #(str (cell-id->cell-string %)) (get grouped-cells false))
          interior-cells-str (string/join " " interior-cells)
          exterior-cells-str (string/join " " exterior-cells)]
      {:cell-ids cell-ids-string
       :s2-cell-interiors interior-cells-str
       :s2-cell-exteriors exterior-cells-str})
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells" {:shape shape} t)))))

(defmethod get-s2-cell-ids-range Mbr
  [shape min-level max-level]
  (try
    (let [s2-rect (shape->s2latlngrect shape)
          cell-ids (s2rect->cell-ids-range s2-rect min-level max-level)
          cell-ids-string (map #(cell-id->cell-string %) cell-ids)
          ;_ (println "cell-ids-string: " cell-ids-string)
          grouped-cells (group-by #(.contains s2-rect (S2Cell. %)) cell-ids)
          interior-cells (map #(str (cell-id->cell-string %)) (get grouped-cells true))
          exterior-cells (map #(str (cell-id->cell-string %)) (get grouped-cells false))
          interior-cells-str (string/join " " interior-cells)
          exterior-cells-str (string/join " " exterior-cells)]
      {:cell-ids cell-ids-string
       :s2-cell-interiors interior-cells-str
       :s2-cell-exteriors exterior-cells-str})
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells" {:shape shape} t)))))

(defmethod get-s2-cell-ids-range Point
  [shape min-level max-level]
  (try
    (let [s2-point (.toPoint (S2LatLng/fromDegrees (:lat shape) (:lon shape)))
          cell (S2Cell. s2-point)
          cell-id (.parent (.id cell) max-level)
          cell-token (cell-id->token cell-id)]
      {:cell-tokens [cell-token]
       :s2-cell-interiors ""
       :s2-cell-exteriors cell-token})
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells" {:shape shape} t)))))

(defmethod get-s2-cell-ids-range LineString
  [shape min-level max-level]
  (try
    (let [points (map #(S2LatLng/fromDegrees (:lat %) (:lon %)) (:points shape))
          s2Points (ArrayList. ^Iterable (map #(.toPoint %) points))
          s2Polyline (S2Polyline. s2Points)
          cell-ids (s2polyline->cell-ids s2Polyline max-level)
          cell-tokens (map cell-id->token cell-ids)
          exterior-cells-str (string/join " " cell-tokens)]
      {:cell-tokens cell-tokens
       :s2-cell-interiors ""
       :s2-cell-exteriors exterior-cells-str})
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells" {:shape shape} t)))))

(defmulti get-s2-cell-tokens
  "Returns the cell tokens for the given shape. This is used to get the covering cells for the shape."
  (fn [shape _cell-level]
    (class shape)))

(defmethod get-s2-cell-tokens :default
  [shape _cell-level]
  (throw (ex-info (format "get-s2-cell-tokens Unsupported shape type [%s]" (class shape)) {:shape shape})))

(defmethod get-s2-cell-tokens Polygon
  [shape cell-level]
  (try
    (let [s2-polygon (shape->s2polygon shape)
          cell-ids (s2polygon->cell-ids s2-polygon cell-level)
          cell-ids-string (map #(cell-id->cell-string %) cell-ids)
          ;_ (println "cell-ids-string: " cell-ids-string)
          cell-tokens (map cell-id->token cell-ids)
          grouped-cells (group-by #(.contains s2-polygon (S2Cell. %)) cell-ids)
          interior-cells (map #(str (cell-id->token %)) (get grouped-cells true))
          exterior-cells (map #(str (cell-id->token %)) (get grouped-cells false))
          interior-cells-str (string/join " " interior-cells)
          exterior-cells-str (string/join " " exterior-cells)]
      {:cell-tokens cell-tokens
       :s2-cell-interiors interior-cells-str
       :s2-cell-exteriors exterior-cells-str})
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells" {:shape shape} t)))))


(defmethod get-s2-cell-tokens Mbr
  [shape cell-level]
  (try
    (let [s2-rect (shape->s2latlngrect shape)
          cell-ids (s2latlngrect->cell-ids s2-rect cell-level)
          cell-tokens (map cell-id->token cell-ids)
          grouped-cells (group-by #(.contains s2-rect (S2Cell. %)) cell-ids)
          interior-cells (map #(str (cell-id->token %)) (get grouped-cells true))
          exterior-cells (map #(str (cell-id->token %)) (get grouped-cells false))
          interior-cells-str (string/join " " interior-cells)
          exterior-cells-str (string/join " " exterior-cells)]
      {:cell-tokens cell-tokens
       :s2-cell-interiors interior-cells-str
       :s2-cell-exteriors exterior-cells-str})
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells" {:shape shape} t)))))

(defmethod get-s2-cell-tokens Point
  [shape cell-level]
  (try
    (let [s2-point (.toPoint (S2LatLng/fromDegrees (:lat shape) (:lon shape)))
          cell (S2Cell. s2-point)
          cell-id (.parent (.id cell) cell-level)
          cell-token (cell-id->token cell-id)]
      {:cell-tokens [cell-token]
       :s2-cell-interiors ""
       :s2-cell-exteriors cell-token})
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells" {:shape shape} t)))))

(defmethod get-s2-cell-tokens LineString
  [shape cell-level]
  (try
    (let [points (map #(S2LatLng/fromDegrees (:lat %) (:lon %)) (:points shape))
          s2Points (ArrayList. ^Iterable (map #(.toPoint %) points))
          s2Polyline (S2Polyline. s2Points)
          cell-ids (s2polyline->cell-ids s2Polyline cell-level)
          cell-tokens (map cell-id->token cell-ids)
          exterior-cells-str (string/join " " cell-tokens)]
      {:cell-tokens cell-tokens
       :s2-cell-interiors ""
       :s2-cell-exteriors exterior-cells-str})
    (catch Throwable t
      (.error (LogManager/getLogger "cmr_s2_cells") (format "Unable to get covering cells for shape [%s]" shape) t)
      (throw (ex-info "An exception occurred getting covering cells" {:shape shape} t)))))
