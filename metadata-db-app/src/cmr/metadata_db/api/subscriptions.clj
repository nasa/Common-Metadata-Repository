(ns cmr.metadata-db.api.subscriptions
  "Defines the HTTP URL routes for the application as related to subscriptions."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.services.sub-notifications :as sub-note]
   [compojure.core :refer :all]))

(defn- update-subscription-notification-time
  "Update a subscription notification time"
  [request subscription-concept-id]
  (let [{:keys [body request-context]} request]
    (sub-note/update-subscription-notification request-context subscription-concept-id (json/parse-string (slurp body) true))
    {:status 204}))

(def subscription-api-routes
  (context "/subscription" []
    ;; receive notification to update subscription time
    (context "/:subscription-concept-id" [subscription-concept-id]
      (PUT "/notification-time"
        request
        (update-subscription-notification-time request subscription-concept-id)))))
