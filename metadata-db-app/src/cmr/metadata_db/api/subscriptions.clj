(ns cmr.metadata-db.api.subscriptions
  "Defines the HTTP URL routes for the application as related to subscriptions."
  (:require
   [cmr.metadata-db.services.sub-notifications :as sub-note]
   [cmr.metadata-db.services.subscriptions :as subscriptions]
   [compojure.core :refer [PUT POST context]]))

(defn- update-subscription-notification-time
  "Update a subscription notification time"
  [context params]
  (let [sub-id (:subscription-concept-id params)
        _ (sub-note/update-subscription-notification context sub-id)]
    {:status 204}))

(def subscription-api-routes
  (context "/subscription" []
    ;; receive notification to update subscription time
    (PUT "/:subscription-concept-id/notification-time"
      {params :params
       request-context :request-context}
      (update-subscription-notification-time request-context params))
    (POST "/refresh-subscription-cache"
      {request-context :request-context}
      (subscriptions/refresh-subscription-cache request-context))))
