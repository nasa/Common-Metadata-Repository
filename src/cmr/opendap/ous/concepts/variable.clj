(ns cmr.opendap.ous.concepts.variable
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.const :as const]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.ous.util.geog :as geog]
   [cmr.opendap.results.core :as results]
   [cmr.opendap.results.errors :as errors]
   [cmr.opendap.util :as util]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX We can pull this from configuration once async-get-metadata function
;;     signatures get updated to accept the system data structure as an arg.
(def variables-api-path "/variables")
(def pinned-variable-schema-version "1.2")
(def results-content-type "application/vnd.nasa.cmr.umm_results+json")
(def charset "charset=utf-8")
(def accept-format "%s; version=%s; %s")
(def accept-header (format accept-format
                           results-content-type
                           pinned-variable-schema-version
                           charset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def lat-type :LATITUDE_DIMENSION)
(def lon-type :LONGITUDE_DIMENSION)
(def lat-type? #(= % lat-type))
(def lon-type? #(= % lon-type))
(def lat-lon-type? #(or (lat-type? %) (lon-type? %)))

(defn lat-dim
  [dim]
  (or ;; This first one is all that will be needed by UMM-Var 1.2+
      (lat-type dim)
      ;; The remaining provide backwards compatibility with older Vars
      (:Latitude dim)
      (:latitude dim)
      (:lat dim)
      (:YDim dim)))

(defn lon-dim
  [dim]
  (or ;; This first one is all that will be needed by UMM-Var 1.2+
      (lon-type dim)
      ;; The remaining provide backwards compatibility with older Vars
      (:Longitude dim)
      (:longitude dim)
      (:lon dim)
      (:XDim dim)))

(defn restructure-dim
  [dim]
  (let [type (keyword (:Type dim))
        name (keyword (:Name dim))]
    [(if (lat-lon-type? type)
         type
         name) {:Size (:Size dim)
                :Name name
                :Type type}]))

(defn restructure-dims
  [dims]
  (->> dims
       (map restructure-dim)
       (into {})))

(defn normalize-lat-lon
  "This function normalizes the names of lat/lon in order to simplify internal,
  CMR OPeNDAP-only logic.

  Note that the original names are preserved in the data structure (done as part
  of `restructure-dim`)."
  [dim]
  (-> dim
      (assoc :Latitude (lat-dim dim)
             :Longitude (lon-dim dim))
      (dissoc lat-type lon-type)
      ;; The dissoc here is only applicable to pre-1.2 UMM-Vars
      (dissoc :latitude :longitude :lat :lon :YDim :XDim)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Core Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-query
  [variable-ids]
  (string/join
   "&"
   (conj
    (map #(str (codec/url-encode "concept_id[]")
               "=" %)
         variable-ids)
    (str "page_size=" (count variable-ids)))))

(defn async-get-metadata
  "Given a 'params' data structure with a ':variables' key (which may or may
  not have values) and a list of all collection variable-ids, return the
  metadata for the passed variables, if defined, and for all associated
  variables, if params does not contain any."
  [search-endpoint user-token {variable-ids :variables}]
  (if (seq variable-ids)
    (let [url (str search-endpoint variables-api-path)
          payload (build-query variable-ids)]
      (log/debug "Variables query CMR URL:" url)
      (log/debug "Variables query CMR payload:" payload)
      (request/async-post
       url
       (-> {}
           (request/add-token-header user-token)
           (request/add-accept accept-header)
           (request/add-form-ct)
           (request/add-payload payload)
           ((fn [x] (log/trace "Full request:" x) x)))
       response/json-handler))
    (deliver (promise) [])))

(defn extract-metadata
  [promise]
  (let [rslts @promise]
    (if (errors/erred? rslts)
      (do
        (log/error errors/variable-metadata)
        (log/error rslts)
        rslts)
      (do
        (log/trace "Got results from CMR variable search:"
                   (results/elided rslts))
        (log/trace "Remaining results:" (results/remaining-items rslts))
        (:items rslts)))))

(defn get-metadata
  [search-endpoint user-token variables]
  (let [promise (async-get-metadata search-endpoint user-token variables)]
    (extract-metadata promise)))

(defn extract-dimensions
  [entry]
  (restructure-dims
   (get-in entry [:umm :Dimensions])))

(defn extract-indexranges
  [entry]
  (when entry
    (let [ranges (get-in entry [:umm :Characteristics :IndexRanges])
          lo-lon (geog/parse-lon-low (first (:LonRange ranges)))
          hi-lon (geog/parse-lon-high (last (:LonRange ranges)))
          lo-lat (geog/parse-lat-low (first (:LatRange ranges)))
          hi-lat (geog/parse-lat-high (last (:LatRange ranges)))
          reversed? (geog/lat-reversed? lo-lat hi-lat)]
      (geog/create-array-lookup lo-lon lo-lat hi-lon hi-lat reversed?))))

(defn create-opendap-bounds
  ([bounding-box]
   (create-opendap-bounds bounding-box {:reversed? false}))
  ([bounding-box opts]
   (create-opendap-bounds {:Longitude const/default-lon-abs-hi
                           :Latitude const/default-lat-abs-hi}
                          bounding-box
                          opts))
  ([{lon-max :Longitude lat-max :Latitude :as dimensions}
    bounding-box
    opts]
   (log/trace "Got dimensions:" dimensions)
   (when bounding-box
     (geog/bounding-box->lookup-record
      (:Size lon-max)
      (:Size lat-max)
      bounding-box
      (:reversed? opts)))))

(defn replace-defaults-lat-lon
  [bounding-info stride [k v]]
  (let [v (or (:Size v) v)]
    (cond (= k :Longitude) (geog/format-opendap-dim-lon
                            (:opendap bounding-info) stride)
          (= k :Latitude) (geog/format-opendap-dim-lat
                           (:opendap bounding-info) stride)
          :else (geog/format-opendap-dim 0 stride (dec v)))))

(defn format-opendap-dims
  ([bounding-info]
    (format-opendap-dims bounding-info geog/default-dim-stride))
  ([bounding-info stride]
    (if (:opendap bounding-info)
      (->> bounding-info
           :dimensions
           (map (partial replace-defaults-lat-lon bounding-info stride))
           (apply str))
      "")))

(defn get-lat-lon-names
  [bounding-info]
  [(name (get-in bounding-info [:dimensions :Longitude :Name]))
   (name (get-in bounding-info [:dimensions :Latitude :Name]))])

(defn format-opendap-lat-lon
  ([bounding-info]
   (format-opendap-lat-lon bounding-info geog/default-lat-lon-stride))
  ([bounding-info stride]
   (geog/format-opendap-lat-lon (:opendap bounding-info)
                                (get-lat-lon-names bounding-info)
                                stride)))

(defn format-opendap-bounds
  ([bounding-info]
   (format-opendap-bounds bounding-info geog/default-lat-lon-stride))
  ([{bound-name :name :as bounding-info} stride]
   (log/trace "Bounding info:" bounding-info)
   (format "%s%s"
           bound-name
           (format-opendap-dims bounding-info stride))))

(defn extract-bounding-info
  "This function is executed at the variable level, however it has general,
  non-variable-specific bounding info passed to it in order to support
  spatial subsetting"
  [entry bounding-box]
  (log/trace "Got variable entry:" entry)
  (log/trace "Got bounding-box:" bounding-box)
  (if (:umm entry)
    (let [dims (normalize-lat-lon (extract-dimensions entry))
          var-array-lookup (extract-indexranges entry)
          reversed? (:lat-reversed? var-array-lookup)]
      (geog/map->BoundingInfo
        {:concept-id (get-in entry [:meta :concept-id])
         :name (get-in entry [:umm :Name])
         :dimensions dims
         :bounds bounding-box
         :opendap (create-opendap-bounds
                   dims
                   bounding-box
                   {:reversed? reversed?})
         :size (get-in entry [:umm :Characteristics :Size])
         :lat-reversed? reversed?}))
    {:errors [errors/variable-metadata]}))
