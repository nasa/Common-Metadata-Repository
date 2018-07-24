(ns cmr.opendap.ous.v1
  (:require
    [cmr.opendap.components.config :as config]
    [cmr.opendap.ous.common :as common]
    [cmr.opendap.results.core :as results]
    [cmr.opendap.results.errors :as errors]
    [cmr.opendap.util :as util]
    [taoensso.timbre :as log]))

(defn get-opendap-urls
  [component user-token raw-params]
  (log/trace "Got params:" raw-params)
  (let [start (util/now)
        search-endpoint (config/get-search-url component)
        ;; Stage 1
        [params bounding-box grans-promise coll-promise s1-errs]
        (common/stage1 component
                       search-endpoint
                       user-token
                       raw-params)
        ;; Stage 2
        [coll params data-files service-ids vars s2-errs]
        (common/stage2 component
                       search-endpoint
                       user-token
                       params
                       coll-promise
                       grans-promise)
        ;; Stage 3
        [services bounding-info s3-errs]
        (common/stage3 component
                       coll
                       search-endpoint
                       user-token
                       bounding-box
                       service-ids
                       vars)
        ;; Stage 4
        [query s4-errs]
        (common/stage4 coll
                       bounding-box
                       services
                       bounding-info)
        ;; Error handling for all stages
        errs (errors/collect
              start params bounding-box grans-promise coll-promise s1-errs
              data-files service-ids vars s2-errs
              services bounding-info s3-errs
              query s4-errs
              {:errors (errors/check
                        [not data-files errors/empty-gnl-data-files])})]
    (log/trace "Got data-files:" (vec data-files))
    (if errs
      (do
        (log/error errs)
        errs)
      (let [urls-or-errs (common/data-files->opendap-urls
                          params data-files query)]
        ;; Error handling for post-stages processing
        (if (errors/erred? urls-or-errs)
          (do
            (log/error urls-or-errs)
            urls-or-errs)
          (do
            (log/debug "Generated URLs:" (vec urls-or-errs))
          (results/create urls-or-errs :elapsed (util/timed start))))))))
