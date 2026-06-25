(ns cmr.ingest.api.realtime-events
  "Provider-facing realtime event publication routes."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.mime-types :as mt]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.data.realtime-events :as realtime-events]
   [compojure.core :refer [POST context]]))

(defn- json-response [status body]
  {:status status
   :headers {common-routes/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)
             common-routes/CORS_ORIGIN_HEADER "*"}
   :body (json/generate-string body)})

(defn publish-event
  "Accepts a provider event and publishes it for realtime indexing.

  The provider id comes from the route, not the request body, so authorization and event
  ownership stay tied to the same CMR ingest provider boundary used by normal concepts."
  [provider-id {:keys [request-context body-copy]}]
  (api-core/verify-provider-exists request-context provider-id)
  (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
  (let [event (-> (json/parse-string body-copy true)
                  (assoc :provider-id provider-id))]
    (realtime-events/publish-realtime-event request-context event)
    (json-response 202 {:status "accepted"
                        :event-id (:event-id event)
                        :event-type (:event-type event)})))

(def routes
  (context "/realtime" []
    (POST "/events"
      request
      (publish-event (:provider-id (:params request)) request))))
