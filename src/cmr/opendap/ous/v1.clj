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
                       {:endpoint search-endpoint
                        :token user-token
                        :params raw-params})
        ;; Stage 2
        [coll params data-files service-ids vars s2-errs]
        (common/stage2 component
                       coll-promise
                       grans-promise
                       {:endpoint search-endpoint
                        :token user-token
                        :params params})
        ;; Stage 3
        [services bounding-info s3-errs]
        (common/stage3 component
                       coll
                       service-ids
                       vars
                       bounding-box
                       {:endpoint search-endpoint
                        :token user-token
                        :params raw-params})
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
