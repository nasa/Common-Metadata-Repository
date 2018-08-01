(ns cmr.opendap.ous.v1
  (:require
    [cmr.opendap.components.config :as config]
    [cmr.opendap.ous.common :as common]
    [cmr.opendap.ous.concepts.collection :as collection]
    [cmr.opendap.ous.concepts.granule :as granule]
    [cmr.opendap.results.core :as results]
    [cmr.opendap.results.errors :as errors]
    [cmr.opendap.util :as util]
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
      {:errors [errors/unsupported-processing-level
                (format errors/problem-processing-level
                        level
                        (:id coll))]})))

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
        params (apply-level-conditions coll params)
        vars (common/apply-bounding-conditions endpoint token coll params)
        errs (apply errors/collect (concat [granules coll vars] data-files))]
    (when errs
      (log/error "Stage 2 errors:" errs))
    (log/trace "data-files:" (vec data-files))
    (log/trace "service ids:" service-ids)
    (log/debug "Finishing stage 2 ...")
    [params data-files service-ids vars errs]))

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
        [params data-files service-ids vars s2-errs]
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
              data-files service-ids vars s2-errs
              services bounding-info s3-errs
              query s4-errs
              {:errors (errors/check
                        [not data-files errors/empty-gnl-data-files])})]
    (common/process-results {:params params
                             :data-files data-files
                             :query query} start errs)))
