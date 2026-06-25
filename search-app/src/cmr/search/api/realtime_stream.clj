(ns cmr.search.api.realtime-stream
  "Server-sent-event response skeleton for realtime CMR metadata events."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.api.routes :as common-routes]))

(defn- sse [event-name payload]
  (str "event: " event-name "\n"
       "data: " (json/generate-string payload) "\n\n"))

(defn event-stream-response
  "Returns a placeholder SSE response.

  The production implementation should attach to the realtime event source, apply the same
  ACL/query constraints used by granule search, and stream matching metadata events."
  [_context params]
  {:status 200
   :headers {common-routes/CONTENT_TYPE_HEADER "text/event-stream; charset=utf-8"
             "Cache-Control" "no-cache"
             common-routes/CORS_ORIGIN_HEADER "*"}
   :body (sse "cmr.realtime.ready"
              {:status "ready"
               :params params
               :message "Realtime event stream placeholder. Attach queue/log consumer here."})})
