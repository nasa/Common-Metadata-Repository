(ns cmr.transmit.ordering
  "Handle all communications to the CMR-Ordering application, a graphql app which
   processes orders for Earthdata Search and CMR. This application needs to know
   when CMR has changed a provider so that it can download the latest provider
   list"

  (:require
   [clj-http.client :as client]
   [cmr.common.api.context :as ctxt]
   [cmr.common.log :as log :refer (debug info)]
   [cmr.common.mime-types :as mime]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [clojure.core.async :as async]
   [clojure.string :as string]))

;; GraphQl query to call mutation of data
;; query='mutation SyncProvider {syncProviders {added,deleted,message}}'
;; server="https://cmr.sit.earthdata.nasa.gov/ordering/api"
;; curl -s \
;;   --header 'Content-Type: application/json' \
;;   --data "{\"query\": \"$query\"}" \
;;   "$server"

(def order-provider-sync-message
  "mutation SyncProvider {syncProviders {added,deleted,message}}")

(defn- context->just-token
  "graph-ql does not require bearer in the token"
  [context]
  (-> context
      :token
      (string/replace-first "Bearer:" "")
      (string/replace-first "bearer:" "")
      string/trim))

(defn- send-to-ordering
  "A generic message send action"
  [context message]
  (let [conn (config/context->app-connection context :ordering)
        url (conn/root-url conn)
        token (context->just-token context)
        params (merge
                (config/conn-params conn)
                {:body (format "{\"query\": \"%s\"}" message)
                 :headers (merge
                           (ctxt/context->http-headers context)
                           {:content-type mime/json
                            :client-id config/cmr-client-id
                            config/token-header token})
                 :throw-exceptions false})
        response (client/post url params)]
      (info (format "Provider change event, notifying [%s] - response is [%s]\n" url response))))

(defn notify-ordering
  "Sent a message to the sync mutation at cmr-ordering"
  [context]
  (debug "sending sync message to cmr-ordering")
  (async/go (send-to-ordering context order-provider-sync-message)))
