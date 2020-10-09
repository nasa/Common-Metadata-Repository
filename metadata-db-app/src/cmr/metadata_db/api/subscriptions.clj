(ns cmr.metadata-db.api.subscriptions
  "Defines the HTTP URL routes for the application as related to subscriptions."
  (:require
   [cmr.metadata-db.services.sub-notifications :as sub-note]
   [compojure.core :refer :all]))

(defn- update-subscription-notification-time
  "Update a subscription notification time"
  [context params]
  (let [sub-id (:subscription-concept-id params)
        result (sub-note/update-subscription-notification context sub-id)]
    {:status 204}))

; TODO add a get interface to help with testing. CMR-6621

(def subscription-api-routes
  (context "/subscription" []
    ;; receive notification to update subscription time
    (PUT "/:subscription-concept-id/notification-time"
      {{:keys [subscription-concept-id] :as params} :params
       request-context :request-context}
      (update-subscription-notification-time request-context params))))
