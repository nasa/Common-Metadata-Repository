(ns cmr.indexer.test.api.test-api
  "APIs for testing only"
  (:require
    [cmr.acl.core :as acl]
    [cmr.common.api.context :as context]
    [cmr.common.cache :as cache]
    [cmr.indexer.api.routes :as production-routes]
    [cmr.indexer.services.index-service :as index-svc]
    [cmr.indexer.services.index-set-service :as index-set-svc]
    [compojure.core :refer [defroutes POST routes context]]))

(def ^:private reset-routes
  (context "/index-sets" []
    (POST "/reset" {request-context :request-context}
      (acl/verify-ingest-management-permission request-context :update)
      (cache/reset-caches request-context)
      (index-set-svc/reset request-context)
      {:status 204})))

;; Combine it with the production routes
;(defn make-test-app [system]
;  (routes
;    (context (:relative-root-url system) []
;      ;; reset operation available just for development purposes
;      ;; delete configured elastic indexes and create them back
;      (POST "/reset" {:keys [request-context]}
;        (acl/verify-ingest-management-permission request-context :update)
;        (cache/reset-caches request-context)
;        (index-svc/reset request-context)
;        {:status 204})
;
;      reset-routes)
;
;    (production-routes/make-api system)))

(defn make-test-app [system]
  (let [;; Define your raw test routes
        raw-test-routes (routes
                          (context (:relative-root-url system) []
                            (POST "/reset" {:keys [request-context]}
                              (acl/verify-ingest-management-permission request-context :update)
                              (cache/reset-caches request-context)
                              (index-svc/reset request-context)
                              {:status 204}))
                          reset-routes)

        ;; Get the raw production routes (Before middleware is applied!)
        raw-prod-routes (production-routes/build-routes system)

        ;; Combine them together
        combined-raw-routes (routes raw-test-routes raw-prod-routes)]

    ;; Pass the combined routes through the EXACT production middleware pipeline
    (production-routes/apply-middleware combined-raw-routes system)))