(ns cmr.exchange.query.impl.wcs
  (:require
   [cmr.exchange.query.const :as const]
   [cmr.exchange.query.impl.cmr :as cmr]
   [cmr.exchange.query.util :as util]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation of Collection Params API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CollectionWcsStyleParams
  [;; `format` is any of the formats supported by the target OPeNDAP server,
   ;; such as `json`, `ascii`, `nc`, `nc4`, `dods`, etc.
   format
   ;;
   ;; `coverage` can be:
   ;;  * a list of granule concept ids
   ;;  * a list of granule ccontept ids + a collection concept id
   ;;  * a single collection concept id
   coverage
   ;;
   ;; `rangesubset` is a list of UMM-Var concept ids
   rangesubset
   ;;
   ;; `subset` is used to indicate desired spatial subsetting and is a list of
   ;; lon/lat values, as used in WCS. It is parsed from URL queries like so:
   ;;  `?subset=lat(22,34)&subset=lon(169,200)`
   ;; giving values like so:
   ;;  `["lat(22,34)" "lon(169,200)"]`
   subset
   ;; `timeposition` is used to indicate temporal subsetting with starting
   ;; and ending values being ISO 8601 datetime stamps, separated by a comma.
   timeposition])

(defn ->cmr
  [this]
  (let [subset (:subset this)]
    (-> this
        (assoc :collection-id (or (:collection-id this)
                                  (util/coverage->collection (:coverage this)))
               :granules (util/coverage->granules (:coverage this))
               :variables (:rangesubset this)
               ;; There was never an analog in wcs for exclude-granules, so set
               ;; to false.
               :exclude-granules false
               :bounding-box (when (seq subset)
                              (util/subset->bounding-box subset))
               :temporal (:timeposition this))
        (dissoc :coverage :rangesubset :timeposition)
        (cmr/map->CollectionCmrStyleParams))))

(defn ->query-string
  [this]
  (util/->query-string this))

(def collection-behaviour
  {:->cmr ->cmr
   :->query-string ->query-string})

(def style? #(util/style? map->CollectionWcsStyleParams %))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create
  [params]
  (log/trace "Instantiating params protocol ...")
  (map->CollectionWcsStyleParams
    (assoc params :format (or (:format params)
                              const/default-format)
                  :coverage (util/split-comma->sorted-coll (:coverage params))
                  :rangesubset (util/split-comma->sorted-coll (:rangesubset params))
                  :timeposition (util/->coll (:timeposition params)))))
