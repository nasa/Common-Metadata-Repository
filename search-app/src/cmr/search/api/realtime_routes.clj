(ns cmr.search.api.realtime.routes
  "Public realtime discovery routes for search-app."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.mime-types :as mt]
   [cmr.search.api.realtime.stream :as stream]
   [compojure.core :refer [GET context]]))

(defn- json-response [status body]
  {:status status
   :headers {common-routes/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)
             common-routes/CORS_ORIGIN_HEADER "*"}
   :body (json/generate-string body)})

(defn capability-response []
  (json-response
   200
   {:realtime true
    :resources [{:rel "events"
                 :href "/search/realtime/events"
                 :type "text/event-stream"}
                {:rel "granules"
                 :href "/search/granules.json?realtime=true"
                 :type "application/json"}]
    :filters [:collection-concept-id
              :provider
              :bounding-box
              :temporal
              :validation-state
              :stream-state]}))

(def routes
  (context "/realtime" []
    (GET "/" [] (capability-response))
    (GET "/events"
      {ctx :request-context params :params}
      (stream/event-stream-response ctx params))))

