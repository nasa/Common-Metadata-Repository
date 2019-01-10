(ns cmr.exchange.query.impl.giovanni
  (:require
   [cmr.exchange.query.impl.cmr :as cmr]
   [cmr.exchange.query.util :as util]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation of Giovanni Params API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO
;;  should service be included?
;;  should variableFacets be included?
;;  Timestamp type?
(defrecord CollectionGiovanniStyleParams
  [;; `starttime` is used to indicate the temporal subsetting start time
   ;;  value conforming to ISO 8601 datetime stamps
   starttime
   ;;
   ;; `endtime` is used to indicate the temporal subsetting end time
   ;;  value conforming to ISO 8601 datetime stamps
   endtime
   ;;
   ;; `bbox` describes the rectangular area of interest using four
   ;; comma-separated values:
   ;;  1. lower left longitude (west)
   ;;  2. lower left latitude (south)
   ;;  3. upper right longitude (east)
   ;;  4. upper right latitude (north)
   ;; For example:
   ;;  `bbox=-9.984375,56.109375,19.828125,67.640625`
   bbox
   ;;
   ;; `dataKeyword` is the desired variable keyword to be searched
   dataKeyword
   ;;
   ;; `data` are the variable names to be searched
   data])

(defn ->cmr
  [this]
  (-> this
      (assoc :collection-id (:collection-id this)
             :exclude-granules false
             :bounding-box (:bbox this)
             ;; FIXME :data and :dataKeyword search for variable concept-ids?
             :temporal (util/seq->str [(:starttime this) (:endttime this)]))
      (dissoc :data :dataKeyword :bbox :starttime :endtime)
      (cmr/map->CollectionCmrStyleParams)))

(defn ->query-string
  [this]
  ;; TODO once we are clear on the format of the giovanni query strings
  ;; we will need to add some special casings because the giovanni query
  ;; strings are full of edge cases.
  (util/->query-string this))

(def collection-behaviour
  {:->cmr ->cmr
   :->query-string ->query-string})

(def style? #(util/style? map->CollectionGiovanniStyleParams %))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create
  [params]
  (log/trace "Instantiating params protocol ...")
  (map->CollectionGiovanniStyleParams
    (assoc params
           :bbox (util/split-comma->coll (:bbox params))
           :starttime (:starttime params)
           :endtime (:endtime params)
           :dataKeyword (:dataKeyword params)
           :data (util/split-comma->coll (:data params)))))
