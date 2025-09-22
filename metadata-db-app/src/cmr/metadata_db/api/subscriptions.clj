(ns cmr.metadata-db.api.subscriptions
  "Defines the HTTP URL routes for the application as related to subscriptions."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.metadata-db.services.sub-notifications :as sub-note]
   [compojure.core :refer [PUT context]]))

(defn- update-subscription-notification-time
  "Update a subscription notification time"
  [context params body]
  (let [sub-id (:subscription-concept-id params)
        last-notified-time (-> (slurp body)
                               (string/trim)
                               (json/decode  true)
                               (get :last-notified-time))]
    (sub-note/update-subscription-notification context sub-id last-notified-time)
    {:status 204}))

(def subscription-api-routes
  (context "/subscription" []
    ;; receive notification to update subscription time
    (PUT "/:subscription-concept-id/notification-time"
      {params :params
       body :body
       request-context :request-context}
      (update-subscription-notification-time request-context params body))))
