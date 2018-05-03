(ns cmr.opendap.ous.core
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.opendap.ous.collection.core :as collection]
   [cmr.opendap.ous.collection.params.core :as params]
   [cmr.opendap.ous.collection.results :as results]
   [cmr.opendap.ous.granule :as granule]
   [cmr.opendap.ous.service :as service]
   [cmr.opendap.ous.variable :as variable]
   [cmr.opendap.util :as util]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Notes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; General caveat: the logic for this code was translated -- without domain
;; knowledge -- from Node.js code that was established as faulty and buggy.
;; ALL OF THIS needs to be REASSESSED with an intelligent, knowledgable eye.

;; Notes on representing spatial extents.
;;
;; EDSC uses URL-encoded long/lat numbers representing a bounding box
;; Note that the ordering is the same as that used by CMR (see below).
;;  `-9.984375%2C56.109375%2C19.828125%2C67.640625`
;; which URL-decodes to:
;;  `-9.984375,56.109375,19.828125,67.640625`
;;
;; OPeNDAP download URLs have something I haven't figured out yet; given that
;; one of the numbers if over 180, it can't be degrees ... it might be what
;; WCS uses for `x` and `y`?
;;  `Latitude[22:34],Longitude[169:200]`
;;
;; The OUS Prototype uses the WCS standard for lat/long:
;;  `SUBSET=axis[,crs](low,high)`
;; For lat/long this takes the following form:
;;  `subset=lat(56.109375,67.640625)&subset=lon(-9.984375,19.828125)`
;;
;; CMR supports bounding spatial extents by describing a rectangle using four
;; comma-separated values:
;;  1. lower left longitude
;;  2. lower left latitude
;;  3. upper right longitude
;;  4. upper right latitude
;; For example:
;;  `bounding_box==-9.984375,56.109375,19.828125,67.640625`
;;
;; Google's APIs use lower left, upper right, but the specify lat first, then
;; long:
;;  `southWest = LatLng(56.109375,-9.984375);`
;;  `northEast = LatLng(67.640625,19.828125);`

;;; We're going to codify parameters with records to keep things well
;;; documented. Additionally, this will make converting between parameter
;;; schemes an explicit operation on explicit data.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility/Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX This next horrible function was created to replace the subsets.forEach
;; call made at the end of processVariable in the ous.js file. Peter L. Smith
;; had this to say, after I commented/asked about it's somewhat anti-intuitive
;; behaviour:
;;    "The code in the forEach just computes the indices into the data
;;     arrays when start/end lat or long values are provided, and yep -
;;     if you specify multiple lat (or long) pairs, it will effectively
;;     just take the last."

(defn spatial-subset->arrays
  [subset]
  )

(defn ->pixels
  [{x-dim :x y-dim :y} [lon-lo lat-lo lon-hi lat-hi]]
  (let [x-pixel-size (/ (- lon-hi lon-lo) x-dim)
        y-pixel-size (/ (- lat-hi lat-lo) y-dim)]
    ;; XXX Note that the x and y offsets were added here simply due to the
    ;;     fact that these were defined in the Node.js version; upon closer
    ;;     inspection of the Node code, it seems that these values were
    ;;     never actually used ... let's come back and clean this up, once
    ;;     the port has been completed.
    ;; XXX Followup: x and y pixel sizes are only ever used in the Node.js code
    ;;     when calculating the offsets, and those were never used ... so maybe
    ;;     we can delete this whole function?
    {:x {:pixel-size x-pixel-size
         :offset (Math/floor (/ lon-lo x-pixel-size))}
     :y {:pixel-size y-pixel-size
         :offset (Math/floor (/ lat-lo y-pixel-size))}}))

(defn bounding-info->pixels
  [bounding-info]
  (map #(->pixels (:dimensions %) (:bounds %)) bounding-info))


(defn bounding-info->opendap-lat-lon
  [{var-name :name opendap-bounds :opendap}]
  (variable/format-opendap-bounds var-name opendap-bounds))

(defn bounding-info->opendap-query
  ([bounding-info]
    (bounding-info->opendap-query bounding-info nil))
  ([bounding-info bounding-box]
   (when (seq bounding-info)
     (str
      (->> bounding-info
           (map bounding-info->opendap-lat-lon)
           (string/join ",")
           (str "?"))
      ","
      (variable/format-opendap-bounds
       (variable/create-opendap-bounds bounding-box))))))

;; XXX WARNING!!! The pattern matching code has been taken from the Node.js
;;                prototype ... and IT IS AWFUL. This is only temporary ...

(def fallback-pattern #"(.*)(/datapool/)(.*)")
(def fallback-replacement "/opendap/")

(defn data-file->opendap-url
  [pattern-info data-file]
  (let [pattern (re-pattern (:pattern-match pattern-info))
        data-url (:link-href data-file)]
    (if (re-matches pattern data-url)
      (do
        (log/debug "Granule URL matched provided pattern ...")
        (string/replace data-url
                        pattern
                        (str (:pattern-subs pattern-info) "$2")))
      (do
        (log/debug
         "Granule URL didn't match provided pattern; trying default ...")
        (if (re-matches fallback-pattern data-url)
          (string/replace data-url
                          fallback-pattern
                          (str "$1" fallback-replacement "$3")))))))

(defn data-files->opendap-urls
  [params pattern-info data-files query-string]
  (->> data-files
       (map (partial data-file->opendap-url pattern-info))
       (map #(str % "." (:format params) query-string))))

(defn get-opendap-urls
  [search-endpoint user-token raw-params]
  (log/trace "Got params:" raw-params)
  (let [start (util/now)
        params (params/parse raw-params)
        bounding-box (:bounding-box params)
        granules (granule/get-metadata search-endpoint user-token params)
        data-files (map granule/extract-datafile-link granules)
        coll (collection/get-metadata search-endpoint user-token params)
        service-ids (collection/extract-service-ids coll)
        services (service/get-metadata search-endpoint user-token service-ids)
        pattern-info (service/extract-pattern-info (first services))
        all-vars (collection/extract-variable-ids coll)
        vars (variable/get-metadata search-endpoint user-token params all-vars)
        bounding-info (map #(variable/extract-bounding-info % bounding-box)
                           vars)
        query (bounding-info->opendap-query bounding-info bounding-box)]
    (log/debug "data-files:" (into [] data-files))
    (log/debug "pattern-info:" pattern-info)
    (log/debug "all variable ids:" all-vars)
    (log/debug "variable bounding-info:" (into [] bounding-info))
    (log/debug "query:" query)
    (results/create
     (data-files->opendap-urls params pattern-info data-files query)
     :elapsed (util/timed start))))
