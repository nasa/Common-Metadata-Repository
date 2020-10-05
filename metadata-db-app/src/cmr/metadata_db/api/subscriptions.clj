(ns cmr.metadata-db.api.subscriptions
  "Defines the HTTP URL routes for the application as related to subscriptions."
  (:require
   [cheshire.core :as json]
   [clojure.walk :as walk]
   [cmr.acl.core :as acl]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.metadata-db.api.route-helpers :as rh]
   [cmr.metadata-db.services.sub-notifications :as sub-note]
   [compojure.core :refer :all]))

(defn- update-subscription-notification-time
  "Update a subscription notification time"
  [context params]
  ;(def context context)
  (def params params)
  (let [sub-id (:subscription-concept-id params)
        result (sub-note/update-subscription-notification context sub-id)]
    {:status 204}))

; TODO add a get interface to help with testing. CMR-????

(def subscription-api-routes
  (context "/subscription" [] ; maybe subnotification
    ;; receive notification to update subscription time
    (PUT "/:subscription-concept-id/notification/time"
      {{:keys [subscription-concept-id] :as params} :params
       request-context :request-context}
    ; the following worked
    ;(PUT "/notification/time" {:keys [request-context params headers body]}
      ; is the following needed in some form?
      ; (acl/verify-ingest-management-permission request-context :update) ; ???
      (update-subscription-notification-time request-context params))))
