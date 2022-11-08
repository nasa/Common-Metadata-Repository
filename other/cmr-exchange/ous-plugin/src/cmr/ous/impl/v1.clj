(ns cmr.ous.impl.v1
  (:require
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.common.util :as util]
   [cmr.metadata.proxy.concepts.collection :as collection]
   [cmr.metadata.proxy.concepts.granule :as granule]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [cmr.ous.common :as common]
   [cmr.ous.components.config :as config]
   [cmr.ous.results.errors :as ous-errors]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Defaults   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def defualt-processing-level "3")

(def supported-processing-levels
  #{"3" "4"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility/Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sanitize-processing-level
  [level]
  (if (or (= "NA" level)
          (= "Not Provided" level))
    defualt-processing-level
    level))

(defn extract-processing-level
  [entry]
  (log/trace "Collection entry:" entry)
  (sanitize-processing-level
   (or (:processing_level_id entry)
       (get-in entry [:umm :ProcessingLevel :Id])
       defualt-processing-level)))

(defn apply-level-conditions
  ""
  [coll params]
  (let [level (extract-processing-level coll)]
    (log/info "Got level:" level)
    (if (contains? supported-processing-levels level)
      params
      {:errors [ous-errors/unsupported-processing-level
                (format ous-errors/problem-processing-level
                        level
                        (:id coll))]})))

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
        granule-links (map granule/extract-granule-links (:body granules))
        sa-header (get gran-headers :cmr-search-after)
        hits-header (get gran-headers :cmr-hits)
        service-ids (collection/extract-service-ids coll-body)
        params (apply-level-conditions coll-body params)
        vars (common/apply-bounding-conditions endpoint token coll-body params)
        errs (apply errors/collect (concat [granules coll-body vars] granule-links))]
    (when errs
      (log/error "Stage 2 errors:" errs))
    (log/trace "granule-links:" (vec granule-links))
    (log/trace "tag-data:" tag-data)
    (log/trace "service ids:" service-ids)
    (log/debug "Finishing stage 2 ...")
    [params coll-body granule-links sa-header hits-header service-ids vars tag-data errs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-opendap-urls
  [component user-token raw-params input-sa-header]
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
        [services bounding-info s3-errs]
        (common/stage3 component
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
                        :params params})
        ;; Error handling for all stages
        errs (errors/collect
              start params bounding-box grans-promise coll-promise s1-errs
              granule-links service-ids vars s2-errs
              services bounding-info s3-errs
              query s4-errs
              {:errors (errors/check
                        [not granule-links metadata-errors/empty-gnl-data-files])})]
    (common/process-results {:params params
                             :granule-links granule-links
                             :sa-header sa-header
                             :hits hits-header
                             :tag-data tag-data
                             :query query} start errs)))
