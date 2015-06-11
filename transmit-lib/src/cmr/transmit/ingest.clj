(ns cmr.transmit.ingest
  "Provide functions to invoke ingest app"
  (:require [clj-http.client :as client]
            [cmr.common.services.health-helper :as hh]
            [cmr.transmit.connection :as conn]
            [ring.util.codec :as codec]
            [cmr.transmit.http-helper :as h]
            [camel-snake-kebab.core :as csk]
            [cmr.transmit.config :as config]
            [cheshire.core :as cheshire]
            [cmr.common.util :as util :refer [defn-timed]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(def concept-type->url-part
  {:collection "collections"
   :granule "granules"})

(defn- concept-ingest-url
  [provider-id concept-type native-id conn]
  (format "%s/providers/%s/%s/%s"
          (conn/root-url conn)
          (codec/url-encode provider-id)
          (concept-type->url-part concept-type)
          (codec/url-encode native-id)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn-timed ingest-concept
  ([context concept]
   (ingest-concept context concept false))
  ([context concept is-raw]
   (let [{:keys [provider-id concept-type metadata native-id revision-id]} concept]
     (h/request context :ingest
                {:url-fn #(concept-ingest-url provider-id concept-type native-id %)
                 :method :put
                 :raw? is-raw
                 :http-options {:body metadata
                                :content-type (:format concept)
                                :headers {"Revision-Id" revision-id}
                                :accept :json}}))))

(defn get-ingest-health-fn
  "Returns the health status of the ingest"
  [context]
  (let [conn (config/context->app-connection context :ingest)
        request-url (str (conn/root-url conn) "/health")
        response (client/get request-url {:accept :json
                                          :throw-exceptions false
                                          :connection-manager (conn/conn-mgr conn)})
        {:keys [status body]} response
        result (cheshire/decode body true)]
    (if (= 200 status)
      {:ok? true :dependencies result}
      {:ok? false :problem result})))

(defn get-ingest-health
  "Returns the ingest health with timeout handling."
  [context]
  (let [timeout-ms (* 1000 (+ 2 (hh/health-check-timeout-seconds)))]
    (hh/get-health #(get-ingest-health-fn context) timeout-ms)))