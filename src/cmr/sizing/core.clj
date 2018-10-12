(ns cmr.sizing.core
  (:require
    [cmr.exchange.common.results.core :as results]
    [cmr.exchange.common.results.errors :as errors]
    [cmr.exchange.common.results.warnings :as warnings]
    [cmr.exchange.common.util :as util]
    [cmr.exchange.query.core :as query]
    [cmr.metadata.proxy.concepts.granule :as granule]
    [cmr.metadata.proxy.concepts.variable :as variable]
    [cmr.metadata.proxy.results.errors :as metadata-errors]
    [cmr.ous.common :as ous-common]
    [cmr.ous.components.config :as config]
    [cmr.ous.impl.v2-1 :as ous]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX Let's move this into cmr-exchange-common ...
(defn data-type->bytes
  [data-type]
  (case data-type
    :byte 1
    :float 4
    :float32 4
    :float64 8
    :double 8
    :ubyte 1
    :ushort 2
    :uint 4
    :uchar 1
    :string 2
    :char8 1
    :uchar8 1
    :short 2
    :long 4
    :int 4
    :int8 1
    :int16 2
    :int32 4
    :int64 8
    :uint8 1
    :uint16 2
    :uint32 4
    :uint64 8))

(defn get-measurement
  [variable]
  (let [dims (get-in variable [:umm :Dimensions])
        total-dimensionality (reduce * (map :Size dims))
        data-type (get-in variable [:umm :DataType])]
    (* total-dimensionality
       (data-type->bytes data-type))))

(defn estimate-binary-size
  [granules variables]
  (let [compression 1
        metadata 0
        measurements (reduce + (map get-measurement variables))]
    (+ (* (count granules) compression measurements) metadata)))

(defn- -estimate-size
  [fmt granule-count vars]
  (case fmt
    :nc (estimate-binary-size granule-count vars)
    :not-implemented))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn estimate-size
  [component user-token raw-params]
  (let [start (util/now)
        search-endpoint (config/get-search-url component)
        ;; Stage 1
        [params bounding-box grans-promise coll-promise s1-errs]
        (ous-common/stage1
          component
          {:endpoint search-endpoint
           :token user-token
           :params raw-params})
        ;; Stage 2
        [params data-files service-ids vars s2-errs]
        (ous/stage2
          component
          coll-promise
          grans-promise
          {:endpoint search-endpoint
           :token user-token
           :params params})
        ;; Warnings; in SES, no warnings are defined for the above stages
        warns nil
        ;; Error handling for all stages
        errs (errors/collect
              params bounding-box grans-promise coll-promise s1-errs
              data-files service-ids vars s2-errs
              {:errors (errors/check
                        [not data-files metadata-errors/empty-gnl-data-files])})
        fmt (:format params)]
    (log/trace "raw-params:" raw-params)
    (log/debug "Got format:" fmt)
    (ous-common/process-results
      {:params params
       :data-files data-files
       :estimate (-estimate-size fmt (count data-files) vars)}
      start
      errs
      warns)))
