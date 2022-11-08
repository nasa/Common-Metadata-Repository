(ns cmr.ous.impl.v2-1
  "Version 2.1 was introduced in order to provide better support to EDSC users
  who would see their spatial query parameters removed if the variables in their
  results did not contain metadata for lat/lon dimensions (support for this was
  added in UMM-Var 1.2).

  By putting this change into a versioned portion of the API, EDSC (and anyone
  else) now has the ability to set the API version to 'v2' and thus still
  have the results previously expected when making calls against metadata that
  has not been updated to use UMM-Var 1.2."
  (:require
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.common.results.warnings :as warnings]
   [cmr.exchange.common.util :as util]
   [cmr.metadata.proxy.concepts.collection :as collection]
   [cmr.metadata.proxy.concepts.granule :as granule]
   [cmr.metadata.proxy.concepts.service :as service]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [cmr.ous.common :as common]
   [cmr.ous.components.config :as config]
   [cmr.ous.util.geog :as geog]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Stages Overrides for URL Generation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn stage2
  [component coll-promise grans-promise {:keys [endpoint token params]}]
  (log/debug "Starting stage 2 ...")
  (let [granules (granule/extract-metadata grans-promise)
        gran-headers (granule/extract-header-data grans-promise)
        coll-body (collection/extract-body-metadata coll-promise)
        tag-data (get-in coll-body [:tags (keyword collection/opendap-regex-tag) :data])
        granule-links (map granule/extract-granule-links granules)
        sa-header (get gran-headers :cmr-search-after)
        hits-header (get gran-headers :cmr-hits)
        service-ids (collection/extract-service-ids coll-body)
        vars (common/apply-bounding-conditions endpoint token coll-body params)
        svcs (when (:service-id params)
               (service/get-metadata endpoint token [(:service-id params)]))
        errs (apply errors/collect (concat [granules coll-body vars] granule-links))]
    (when errs
      (log/error "Stage 2 errors:" errs))
    (log/trace "granule-links:" (vec granule-links))
    (log/trace "tag-data:" tag-data)
    (log/trace "service ids:" service-ids)
    (log/debug "Finishing stage 2 ...")
    [params coll-body granule-links sa-header hits-header service-ids vars tag-data errs svcs]))

(defn stage3
  [component service-ids vars bounding-box {:keys [endpoint token params]}]
  (log/debug "Starting stage 3 ...")
  (let [[vars params bounding-box gridded-warns]
        (common/apply-gridded-conditions vars params bounding-box)
        services-promise (service/async-get-metadata endpoint token service-ids)
        bounding-infos (if-not (seq gridded-warns)
                         (map #(geog/extract-bounding-info % bounding-box)
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
  ([component user-token raw-params input-sa-header]
   (get-opendap-urls component user-token "2" raw-params input-sa-header))
  ([component user-token dap-version raw-params input-sa-header]
   (log/trace "Got params:" raw-params)
   (let [start (util/now)
         search-endpoint (config/get-search-url component)
         ;; Stage 1
         [params bounding-box grans-promise coll-promise s1-errs]
         (common/stage1 component
                        {:endpoint search-endpoint
                         :token user-token
                         :params raw-params
                         :sa-header input-sa-header})
         ;; Stage 2
         [params coll granule-links sa-header hits-header service-ids vars tag-data s2-errs]
         (stage2 component
                 coll-promise
                 grans-promise
                 {:endpoint search-endpoint
                  :token user-token
                  :params params})
         ;; Stage 3
         [services vars params bounding-info s3-errs s3-warns]
         (stage3 component
                 service-ids
                 vars
                 bounding-box
                 {:endpoint search-endpoint
                  :token user-token
                  :params params})
         ;; Stage 4
         [query s4-errs]
         (common/stage4 component
                        services
                        bounding-box
                        bounding-info
                        {:endpoint search-endpoint
                         :token user-token
                         :dap-version dap-version
                         :params params})
         ;; Warnings for all stages
         warns (warnings/collect s3-warns)
         ;; Error handling for all stages
         errs (errors/collect
               start params bounding-box grans-promise coll-promise s1-errs
               granule-links service-ids vars s2-errs
               services bounding-info s3-errs
               query s4-errs
               {:errors (errors/check
                         [not granule-links metadata-errors/empty-gnl-data-files])})]
     (common/process-results {:params params
                              :dap-version dap-version
                              :granule-links granule-links
                              :sa-header sa-header
                              :hits-header hits-header
                              :tag-data tag-data
                              :query query} start errs warns))))
