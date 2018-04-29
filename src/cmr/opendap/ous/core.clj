(ns cmr.opendap.ous.core
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.opendap.ous.collection.core :as collection]
   [cmr.opendap.ous.collection.params.core :as params]
   [cmr.opendap.ous.collection.results :as results]
   [cmr.opendap.ous.granule :as granule]
   [cmr.opendap.ous.service :as service]
   [cmr.opendap.util :as util]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Notes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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




;; XXX WARNING!!! The pattern matching code has been taken from the Node.js
;;                prototype ... and IT IS AWFUL. This is only temporary ...

(def fallback-pattern #"(.*)(/datapool/)(.*)")
(def fallback-replacement "/opendap/")

(defn data-file->opendap-url
  [pattern-info data-file]
  (let [pattern (re-pattern (:pattern-match pattern-info))
        data-url (:link-href data-file)
        replacment (string/replace data-url
                                   pattern
                                   (str (:pattern-subs pattern-info) "$2"))]
    (if (re-matches pattern data-url)
      (do
        (log/debug "Matched!")
        (log/debug "pattern:" pattern)
        (log/debug "data-url:" data-url)
        (log/debug "replacment:" replacment)
        replacment)
      (do
        (log/debug "Didn't match; trying default ...")
        (if (re-matches fallback-pattern data-url)
          (string/replace data-url
                          fallback-pattern
                          (str "$1" fallback-replacement "$3")))))))

(defn data-files->opendap-urls
  [params pattern-info data-files]
  (->> data-files
       (map (partial data-file->opendap-url pattern-info))
       (map #(str % "." (:format params)))))

(defn get-opendap-urls
  [search-endpoint user-token raw-params]
  (log/trace "Got params:" raw-params)
  (let [start (util/now)
        params (params/parse raw-params)
        granules (granule/get-metadata search-endpoint user-token params)
        data-files (map granule/extract-datafile-link granules)
        coll (collection/get-metadata search-endpoint user-token params)
        service-ids (collection/extract-service-ids coll)
        services (service/get-metadata search-endpoint user-token service-ids)
        pattern-info (service/extract-pattern-info (first services))]
    (log/warn "data-files:" (into [] data-files))
    ; (log/warn "services:" services)
    (log/warn "pattern-info:" pattern-info)
    (results/create
     ;(assoc params :granules granules)
     ; data-files
     ;services
     (data-files->opendap-urls params pattern-info data-files)
     :elapsed (util/timed start))))
