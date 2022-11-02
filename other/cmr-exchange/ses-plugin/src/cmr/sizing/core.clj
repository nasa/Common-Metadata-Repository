(ns cmr.sizing.core
  (:require
    [clojure.string :as string]
    [cmr.exchange.common.results.core :as results]
    [cmr.exchange.common.results.errors :as errors]
    [cmr.exchange.common.results.warnings :as warnings]
    [cmr.exchange.common.util :as util]
    [cmr.exchange.query.core :as query]
    [cmr.metadata.proxy.concepts.core :as concept]
    [cmr.metadata.proxy.concepts.granule :as granule]
    [cmr.metadata.proxy.concepts.variable :as variable]
    [cmr.metadata.proxy.results.errors :as metadata-errors]
    [cmr.ous.common :as ous-common]
    [cmr.ous.components.config :as config]
    [cmr.ous.impl.v2-1 :as ous]
    [cmr.sizing.formats :as formats]
    [cmr.sizing.spatial :as spatial]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- format-estimate
  "Formats estimate to two significant digits, while avoiding scientific notation for really
   small numbers"
  [estimate type]
  (if (and (double? estimate)
           (not= type :bytes))
    (->> estimate
         (format "%.2f")
         read-string)
    (-> estimate
        Math/ceil
        long)))

(defn- create-empty-result-with-error
  "This returns an empty result with warnings indicating what went wrong."
  [results start errs]
  (results/create [{:bytes 0
                    :mb 0
                    :gb 0}]
                    :request-id (get-in results [:params :request-id])
                    :elapsed (util/timed start)
                    :warnings {:warnings errs}))

;; XXX This function is nearly identical to one of the same name in
;;     cmr.ous.common -- we should put this somewhere both can use,
;;     after generalizing to take a func and the func's args ...
(defn process-results
  ([results start errs]
   (process-results results start errs {:warnings nil}))
  ([results start errs warns]
   (log/trace "Got granule-links:" (vec (:granule-links results)))
   (log/trace "Process-results tag-data:" (:tag-data results))
   (if errs
     (do
       (log/error errs)
       (create-empty-result-with-error results start errs))
     (let [sample-granule-metadata-size (count (.getBytes (:granule-metadata results)))
           formats-estimate (formats/estimate-size
                             (:svcs results)
                             (count (:granule-links results))
                             (:vars results)
                             sample-granule-metadata-size
                             (:params results))]
       ;; Error handling for post-stages processing
       (if-let [errs (errors/erred? formats-estimate)]
         (do
           (log/error errs)
           (create-empty-result-with-error results start errs))
         (let [spatial-estimate (spatial/estimate-size
                                 formats-estimate
                                 results)
               params (:params results)]
           (if-let [errs (errors/erred? spatial-estimate)]
             (do
               (log/error errs)
               (create-empty-result-with-error results start errs))
             (let [estimate spatial-estimate
                   elapsed (util/timed start)]
               (log/info (format "request-id: %s estimate: %s elapsed: %s"
                                 (:request-id params)
                                 estimate
                                 elapsed))
               (results/create [{:bytes (format-estimate estimate :bytes)
                                 :mb (format-estimate (/ estimate (Math/pow 2 20)) :mb)
                                 :gb (format-estimate (/ estimate (Math/pow 2 30)) :gb)}]
                               :request-id (:request-id params)
                               :elapsed elapsed
                               :warnings warns)))))))))

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
        [params coll granule-links service-ids vars tag-data s2-errs svcs]
        (ous/stage2
         component
         coll-promise
         grans-promise
         {:endpoint search-endpoint
          :token user-token
          :params params})
        sample-granule-id (first (:granules params))
        granule-metadata (concept/get-metadata
                          search-endpoint user-token
                          (assoc params :concept-id sample-granule-id))
        ;; Error handling for all stages
        errs (errors/collect
              params bounding-box grans-promise coll-promise s1-errs
              granule-links service-ids vars tag-data s2-errs svcs
              granule-metadata
              {:errors (errors/check
                        [not granule-links metadata-errors/empty-gnl-data-files])})

        params (assoc params :total-granule-input-bytes (:total-granule-input-bytes raw-params))
        fmt (:format params)]
    (log/trace "raw-params:" raw-params)
    (log/debug "Got format:" fmt)
    (log/debug "Got granule-links:" (vec granule-links))
    (log/debug "Got vars:" vars)
    (log/debug "Got svcs:" svcs)
    (log/debug "Got total-granule-input-bytes:" (:total-granule-input-bytes raw-params))
    (process-results
      {:params params
       :granule-links granule-links
       :vars vars
       :svcs svcs 
       :collection-metadata coll
       :granule-metadata granule-metadata}
      start
      errs)))
