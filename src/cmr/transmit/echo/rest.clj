(ns cmr.transmit.echo.rest
  "A helper for making echo-rest requests"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cheshire.core :as json]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [cmr.common.log :as log :refer (debug info warn error)]))

(defn request-options
  [conn]
  {:accept :json
   :throw-exceptions false
   :headers {"Echo-Token" (config/echo-system-token)}
   :connection-manager (conn/conn-mgr conn)})

(defn post-options
  [conn body-obj]
  (merge (request-options conn)
         {:content-type :json
          :body (json/encode body-obj)}))

(defn rest-get
  "Makes a get request to echo-rest. Returns a tuple of status, the parsed body, and the body."
  ([context url-path]
   (rest-get context url-path {}))
  ([context url-path options]
   (let [conn (config/context->app-connection context :echo-rest)
         url (format "%s%s" (conn/root-url conn) url-path)
         params (merge (request-options conn) options)
         ;; Uncoment to log requests
         ; _ (debug "Making ECHO GET Request" url (pr-str params))
         response (client/get url params)
         {:keys [status body headers]} response
         parsed (if (.startsWith ^String (get headers "Content-Type") "application/json")
                  (json/decode body true)
                  nil)]
     [status parsed body])))

(defn rest-post
  "Makes a post request to echo-rest. Returns a tuple of status, the parsed body, and the body."
  ([context url-path body-obj]
   (rest-post context url-path body-obj {}))
  ([context url-path body-obj options]
   (let [conn (config/context->app-connection context :echo-rest)
         url (format "%s%s" (conn/root-url conn) url-path)
         params (merge (post-options conn body-obj) options)
         ;; Uncoment to log requests
         ; _ (debug "Making ECHO POST Request" url (pr-str params))
         response (client/post url params)
         {:keys [status body headers]} response
         parsed (if (.startsWith ^String (get headers "Content-Type" "") "application/json")
                  (json/decode body true)
                  nil)]
     [status parsed body])))


(defn unexpected-status-error!
  [status body]
  (errors/internal-error!
    (format "Unexpected status %d from response. body: %s"
            status (pr-str body))))