(ns cmr.opendap.ous.v2-1
  "Version 2.1 was introduced in order to provide better support to EDSC users
  who would see their spatial query parameters removed if the variables in their
  results did not contain metadata for lat/lon dimensions (support for this was
  added in UMM-Var 1.2).

  By putting this change into a versioned portion of the API, EDSC (and anyone
  else) now has the ability to set the API version to 'v2' and thus still
  have the results previously expected when making calls against metadata that
  has not been updated to use UMM-Var 1.2."
  (:require
    [cmr.opendap.components.config :as config]
    [cmr.opendap.ous.common :as common]
    [cmr.opendap.ous.concepts.collection :as collection]
    [cmr.opendap.ous.concepts.granule :as granule]
    [cmr.opendap.ous.concepts.service :as service]
    [cmr.opendap.ous.concepts.variable :as variable]
    [cmr.opendap.results.core :as results]
    [cmr.opendap.results.errors :as errors]
    [cmr.opendap.results.warnings :as warnings]
    [cmr.opendap.util :as util]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Stages Overrides for URL Generation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn stage2
  [component coll-promise grans-promise {:keys [endpoint token params]}]
  (log/debug "Starting stage 2 ...")
  (let [granules (granule/extract-metadata grans-promise)
        coll (collection/extract-metadata coll-promise)
        data-files (map granule/extract-datafile-link granules)
        service-ids (collection/extract-service-ids coll)
        vars (common/apply-bounding-conditions endpoint token coll params)
        errs (apply errors/collect (concat [granules coll vars] data-files))]
    (when errs
      (log/error "Stage 2 errors:" errs))
    (log/trace "data-files:" (vec data-files))
    (log/trace "service ids:" service-ids)
    (log/debug "Finishing stage 2 ...")
    ;; XXX coll is returned here because it's needed in a workaround
    ;;     for different data sets using different starting points
    ;;     for their indices in OPeNDAP
    ;;
    ;; XXX This is being tracked in CMR-4982
    [coll params data-files service-ids vars errs]))

(defn stage3
  [component coll service-ids vars bounding-box {:keys [endpoint token params]}]
  ;; XXX coll is required as an arg here because it's needed in a
  ;;     workaround for different data sets using different starting
  ;;     points for their indices in OPeNDAP
  ;;
  ;; XXX This is being tracked in CMR-4982
  (log/debug "Starting stage 3 ...")
  (let [[vars params bounding-box gridded-warns]
        (common/apply-gridded-conditions vars params bounding-box)
        services-promise (service/async-get-metadata endpoint token service-ids)
        bounding-infos (if-not (seq gridded-warns)
                         (map #(variable/extract-bounding-info
                                coll % bounding-box)
                              vars)
                         [])
        errs (apply errors/collect bounding-infos)
        warns (warnings/collect gridded-warns)]
    (when errs
      (log/error "Stage 3 errors:" errs))
    (log/trace "variables bounding-info:" (vec bounding-infos))
    (log/debug "Finishing stage 3 ...")
    [services-promise vars params bounding-infos errs warns]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-opendap-urls
  [component user-token raw-params]
  (log/trace "Got params:" raw-params)
  (let [start (util/now)
        search-endpoint (config/get-search-url component)
        ;; Stage 1
        [params bounding-box grans-promise coll-promise s1-errs]
        (common/stage1 component
                       {:endpoint search-endpoint
                        :token user-token
                        :params raw-params})
        ;; Stage 2
        [coll params data-files service-ids vars s2-errs]
        (stage2 component
                coll-promise
                grans-promise
                {:endpoint search-endpoint
                 :token user-token
                 :params params})
        ;; Stage 3
        [services vars params bounding-info s3-errs s3-warns]
        (stage3 component
                coll
                service-ids
                vars
                bounding-box
                {:endpoint search-endpoint
                 :token user-token
                 :params params})
        ;; Stage 4
        [query s4-errs]
        (common/stage4 component
                       coll
                       services
                       bounding-box
                       bounding-info
                       {:endpoint search-endpoint
                        :token user-token
                        :params params})
        ;; Warnings for all stages
        warns (warnings/collect s3-warns)
        ;; Error handling for all stages
        errs (errors/collect
              start params bounding-box grans-promise coll-promise s1-errs
              data-files service-ids vars s2-errs
              services bounding-info s3-errs
              query s4-errs
              {:errors (errors/check
                        [not data-files errors/empty-gnl-data-files])})]
    (common/process-results {:params params
                             :data-files data-files
                             :query query} start errs warns)))
