(ns cmr.opendap.ous.v2-1
  (:require
   [taoensso.timbre :as log]))

(defn apply-gridded-conditions
  "This function is responsible for identifying whether data is girdded or not.
  Originally, the plan was to use processing level to make this determination,
  but due to issues with bad values in the metadata (for processing level),
  that wasn't practical. Instead, we decided to use the presence or absence of
  dimensional metadata that includes latitude and longitude references (new as
  of UMM-Var 1.2).

  This function is thus responsible for:

  * examining each variable for the presence or absence of lat/lon dimensional
    metadata
  * flagging the granule as gridded when all vars have this metadata
  * implicitly flagging the granule as non-gridded when some vars don't have
    this metadata
  * removing bounding information from params when the granule is non-gridded
  * adding a warning to API consumers that the spatial subsetting parameters
    have been stripped due to non-applicability."
  [vars params bounding-box]
  (let [warnings []]
    (log/error "vars:" vars)
    (log/error "params:" params)
    (log/error "bounding-box:" bounding-box)
    [vars params bounding-box warnings]))

; (defn stage3
;   [component coll search-endpoint user-token params bounding-box service-ids vars]
;   ;; XXX coll is required as an arg here because it's needed in a
;   ;;     workaround for different data sets using different starting
;   ;;     points for their indices in OPeNDAP
;   ;;
;   ;; XXX This is being tracked in CMR-4982
;   (log/debug "Starting stage 3 ...")
;   (let [[vars params bounding-box gridded-warns]
;         (apply-gridded-conditions vars
;                                   params
;                                   bounding-box)
;         services-promise (service/async-get-metadata
;                           search-endpoint user-token service-ids)
;         bounding-infos (map #(variable/extract-bounding-info
;                               coll % bounding-box)
;                             vars)
;         errs (apply errors/collect bounding-infos)
;         warns (errors/collect-warnings gridded-warns)]
;     (when errs
;       (log/error "Stage 3 errors:" errs))
;     (log/trace "variables bounding-info:" (vec bounding-infos))
;     (log/debug "Finishing stage 3 ...")
;     [services-promise vars params bounding-infos errs warns]))

; (defn get-opendap-urls
;   [component user-token raw-params]
;   (log/trace "Got params:" raw-params)
;   (let [start (util/now)
;         search-endpoint (config/get-search-url component)
;         ;; Stage 1
;         [params bounding-box grans-promise coll-promise s1-errs]
;         (stage1 component
;                 search-endpoint
;                 user-token
;                 raw-params)
;         ;; Stage 2
;         [coll params data-files service-ids vars s2-errs]
;         (stage2 component
;                 search-endpoint
;                 user-token
;                 params
;                 coll-promise
;                 grans-promise)
;         ;; Stage 3
;         [services vars params bounding-info s3-errs s3-warns]
;         (stage3 component
;                 coll
;                 search-endpoint
;                 user-token
;                 params
;                 bounding-box
;                 service-ids
;                 vars)
;         ;; Stage 4
;         [query s4-errs]
;         (stage4 coll
;                 bounding-box
;                 services
;                 bounding-info)
;         ;; Warnings for all stages
;         warns (errors/collect-warnings s3-warns)
;         ;; Error handling for all stages
;         errs (errors/collect
;               start params bounding-box grans-promise coll-promise s1-errs
;               data-files service-ids vars s2-errs
;               services bounding-info s3-errs
;               query s4-errs
;               {:errors (errors/check
;                         [not data-files errors/empty-gnl-data-files])})]
;     (log/trace "Got data-files:" (vec data-files))
;     (if errs
;       (do
;         (log/error errs)
;         errs)
;       (let [urls-or-errs (data-files->opendap-urls params
;                                                    data-files
;                                                    query)]
;         ;; Error handling for post-stages processing
;         (if (errors/erred? urls-or-errs)
;           (do
;             (log/error urls-or-errs)
;             urls-or-errs)
;           (do
;             (log/debug "Generated URLs:" (vec urls-or-errs))
;           (results/create urls-or-errs :elapsed (util/timed start))))))))
