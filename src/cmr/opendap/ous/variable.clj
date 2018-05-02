(ns cmr.opendap.ous.variable
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.util :as util]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants/Default Values   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-lon-lo -180)
(def default-lon-hi 180)
(def default-lat-lo -90)
(def default-lat-hi 90)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Records   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Dimensions [x y])
(defrecord ArrayLookup [low high])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn dominant-bounds
  "This function is intended to be used if spatial subsetting is not
  proivded in the query: in that case, all the bounds of all the variables
  will be counted, and the one most-used is what will be returned."
  [bounding-info]
  (->> bounding-info
       (map :bounds)
       util/most-frequent))

(defn dominant-dimensions
  "Get the most common dimensions from the bounding-info."
  [bounding-info]
  (->> bounding-info
       (map :dimensions)
       util/most-frequent))

(defn lon-lo->x
  [x-dim lon-lo]
  (-> (/ (* (- x-dim 1)
            (- lon-lo default-lon-lo))
         (- default-lon-hi default-lon-lo))
      Math/floor
      int))

(defn lon-hi->x
  [x-dim lon-hi]
  (-> (/ (* (- x-dim 1)
            (- lon-hi default-lon-lo))
         (- default-lon-hi default-lon-lo))
      Math/ceil
      int))

;; XXX Note that the following two functions were copied from this JS:
;;
;; var lats = value.replace("lat(","").replace(")","").split(",");
;; //hack for descending orbits (array index 0 is at 90 degrees north)
;; y_array_end = YDim - 1 - Math.floor((YDim-1)*(lats[0]-lat_begin)/(lat_end-lat_begin));
;; y_array_begin = YDim -1 - Math.ceil((YDim-1)*(lats[1]-lat_begin)/(lat_end-lat_begin));
;;
;; Note the "hack" JS comment ...
;;;
;; This is complicated by the fact that, immediately before those lines of
;; code are a conflicting set of lines overrwitten by the ones pasted above:
;;
;; y_array_begin = Math.floor((YDim-1)*(lats[0]-lat_begin)/(lat_end-lat_begin));
;; y_array_end = Math.ceil((YDim-1)*(lats[1]-lat_begin)/(lat_end-lat_begin));
;;
;; Currently, given no domain knowledge, I have no idea whether either of
;; these two implementations is correct.

;; XXX 🤮 I am REALLY not convinced the following function is right;
;;     🤮 it seems that the indicies for hi/lo lats have been swapped.
;;     🤮 This HAS TO BE investigated ...
(defn lat-lo->y
  ;[y-dim lat-lo] 🤮
  [y-dim lat-hi]
  (- y-dim
     1
     (-> (/ (* (- y-dim 1)
               ;(- lat-lo default-lat-lo)) 🤮
               (- lat-hi default-lat-lo))
            (- default-lat-hi default-lat-lo))
         Math/ceil
         int)))

;; XXX 🤮 I am REALLY not convinced the following function is right;
;;     🤮 it seems that the indicies for hi/lo lats have been swapped.
;;     🤮 This HAS TO BE investigated ...
(defn lat-hi->y
  ;[y-dim lat-hi] 🤮
  [y-dim lat-lo]
  (- y-dim
     1
     (-> (/ (* (- y-dim 1)
               ;(- lat-hi default-lat-lo)) 🤮
               (- lat-lo default-lat-lo))
            (- default-lat-hi default-lat-lo))
         Math/floor
         int)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Core Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-query
  [passed-vars default-vars]
  (string/join "&" (map #(str (codec/url-encode "concept_id[]")
                              "=" %)
                        (if (seq passed-vars)
                            passed-vars
                            default-vars))))

(defn get-metadata
  "Given a 'params' data structure with a ':variables' key (which may or may
  not have values) and a list of all collection variable-ids, return the
  metadata for the passed variables, if defined, and for all associated
  variables, if params does not contain any."
  [search-endpoint user-token params variable-ids]
  (log/debug "Getting variable metadata for:" variable-ids)
  (let [url (str search-endpoint
                 "/variables?"
                 (build-query (:variables params) variable-ids))
        results (request/async-get url
                 (-> {}
                     (request/add-token-header user-token)
                     (request/add-accept "application/vnd.nasa.cmr.umm+json"))
                 response/json-handler)]
    (log/debug "Got results from CMR variable search:" results)
    (:items @results)))

(defn parse-dimensions
  [dim]
  [(:Size (first (filter #(= "XDim" (:Name %)) dim)))
   (:Size (first (filter #(= "YDim" (:Name %)) dim)))])

(defn extract-dimensions
  [entry]
  (->> (get-in entry [:umm :Dimensions])
       (parse-dimensions)
       (apply ->Dimensions)))

(defn parse-annotated-bounds
  "Parse bounds that are annotated with Lat and Lon, returning values
  in the same order that CMR uses for spatial bounding boxes."
  [bounds]
  (let [lon-regex "Lon:\\s*(-?[0-9]+),\\s*(-?[0-9]+).*;\\s*"
        lat-regex "Lat:\\s*(-[0-9]+),\\s*(-?[0-9]+).*"
        [lon-lo lon-hi lat-lo lat-hi]
         (rest (re-find (re-pattern (str lon-regex lat-regex)) bounds))]
    [lon-lo lat-lo lon-hi lat-hi]))

(defn parse-cmr-bounds
  [bounds]
  "Parse a list of lat/lon values ordered according to the CMR convention
  of lower-left lon, lower-left lat, upper-right long, upper-right lat."
  (map string/trim (string/split bounds #",\s*")))

(defn parse-bounds
  [bounds]
  (if (string/starts-with? bounds "Lon")
    (parse-annotated-bounds bounds)
    (parse-cmr-bounds bounds)))

(defn extract-bounds
  [entry]
  (->> entry
       (#(get-in % [:umm :Characteristics :Bounds]))
       parse-bounds
       (map #(Float/parseFloat %))))

(defn create-opendap-bounds
  [{x-dim :x y-dim :y} [lon-lo lat-lo lon-hi lat-hi]]
  (let [x-lo (lon-lo->x x-dim lon-lo)
        x-hi (lon-hi->x x-dim lon-hi)
        y-lo (lat-lo->y y-dim lat-lo)
        y-hi (lat-hi->y y-dim lat-hi)]
    (map->ArrayLookup
      {:low (map->Dimensions {:x x-lo :y y-lo})
       :high (map->Dimensions {:x x-hi :y y-hi})})))

(defn extract-bounding-info
  [bounding-box entry]
  (let [dims (extract-dimensions entry)
        bounds (or bounding-box (extract-bounds entry))]
    {:concept-id (get-in entry [:meta :concept-id])
     :name (get-in entry [:umm :Name])
     :dimensions dims
     :bounds bounds
     :opendap (create-opendap-bounds dims bounds)
     :size (get-in entry [:umm :Characteristics :Size])}))


