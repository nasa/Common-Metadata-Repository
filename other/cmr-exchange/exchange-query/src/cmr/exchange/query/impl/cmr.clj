(ns cmr.exchange.query.impl.cmr
  (:require
   [cmr.exchange.common.util :as util]
   [cmr.exchange.query.const :as const]
   [cmr.exchange.query.util :as query-util]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation of Collection Params API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CollectionCmrStyleParams
  [;; `collection-id` is the concept id for the collection in question. Note
   ;; that the collection concept id is not provided in query params,
   ;; but in the path as part of the REST URL. Regardless, we offer it here as
   ;; a record field.
   collection-id
   ;;
   ;; `format` is any of the formats supported by the target OPeNDAP server,
   ;; such as `json`, `ascii`, `nc`, `nc4`, `dods`, etc.
   format
   ;;
   ;; `granules` is list of granule concept ids; default behaviour is a
   ;; whitelist.
   granules
   ;;
   ;; `exclude-granules` is a boolean when set to true causes granules list
   ;; to be a blacklist.
   exclude-granules
   ;;
   ;; `variables` is a list of variables to be specified when creating the
   ;; OPeNDAP URL. This is used for subsetting.
   variables
   ;;
   ;; `service-id` concept id for a service.
   service-id
   ;;
   ;; `subset` is used the same way as `subset` for WCS where latitudes,
   ;; lower then upper, are given together and then longitude (again, lower
   ;; then upper) are given together. For instance, to indicate desired
   ;; spatial subsetting in URL queries:
   ;;  `?subset=lat(56.109375,67.640625)&subset=lon(-9.984375,19.828125)`
   subset
   ;;
   ;; `bounding-box` is provided for CMR/EDSC-compatibility as an alternative
   ;; to using `subset` for spatial-subsetting. This parameter describes a
   ;; rectangular area of interest using four comma-separated values:
   ;;  1. lower left longitude
   ;;  2. lower left latitude
   ;;  3. upper right longitude
   ;;  4. upper right latitude
   ;; For example:
   ;;  `bounding_box==-9.984375,56.109375,19.828125,67.640625`
   bounding-box
   ;; `temporal` is used to indicate temporal subsetting with starting
   ;; and ending values being ISO 8601 datetime stamps.
   temporal])

(def style? #(query-util/style? map->CollectionCmrStyleParams
                                %
                                #{(keyword "granules[]")
                                  (keyword "temporal[]")
                                  (keyword "variables[]")}))

(defn ->query-string
  [this]
  (query-util/->query-string this))

(def collection-behaviour
  {:->cmr identity
   :->query-string ->query-string})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create
  [params]
  (log/trace "Instantiating params protocol ...")
  (let [bounding-box (query-util/split-comma->coll (:bounding-box params))
        subset (:subset params)
        granules-array (query-util/get-array-param params :granules)
        variables-array (query-util/get-array-param params :variables)
        temporal-array (query-util/get-array-param params :temporal)]
    (log/trace "original bounding-box:" (:bounding-box params))
    (log/trace "bounding-box:" bounding-box)
    (log/trace "subset:" subset)
    (when granules-array
      (log/trace "granules-array:" granules-array))
    (when variables-array
      (log/trace "variables-array:" variables-array))
    (map->CollectionCmrStyleParams
      (-> params
          (assoc
           :format (:format params)
           :granules (if (query-util/not-array? granules-array)
                       (query-util/split-comma->sorted-coll (:granules params))
                       granules-array)
           :variables (if (query-util/not-array? variables-array)
                        (query-util/split-comma->sorted-coll (:variables params))
                        variables-array)
           :exclude-granules (util/bool (:exclude-granules params))
           :subset (if (seq bounding-box)
                    (query-util/bounding-box->subset bounding-box)
                    (:subset params))
           :bounding-box (if (seq bounding-box)
                           (mapv #(Float/parseFloat %) bounding-box)
                           (when (seq subset)
                             (query-util/subset->bounding-box subset)))
           :temporal (if (query-util/not-array? temporal-array)
                       (query-util/->coll (:temporal params))
                       temporal-array))
          (dissoc (keyword "granules[]")
                  (keyword "temporal[]")
                  (keyword "variables[]"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation of Collections Params API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord CollectionsCmrStyleParams
  [;; This isn't defined for the OUS Prototype, since it didn't support
   ;; submitting multiple collections at a time. As such, there is no
   ;; prototype-oriented record for this.
   ;;
   ;; `collections` is a list of `CollectionCmrStyleParams` records.
   collections])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def collections-behaviour
  ;; Reserved for later use.
  {})
