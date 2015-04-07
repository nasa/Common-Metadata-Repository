(ns cmr.transmit.cubby
  "Provide functions for accessing the cubby app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.health-helper :as hh]
            [cmr.common.mime-types :as mt]
            [cheshire.core :as json]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [ring.util.codec :as codec]))

(comment
  (def context {:system (config/system-with-connections {} [:cubby])})

  (get-keys-raw context)

  )

(defn- reset-url
  [conn]
  (format "%s/reset" (conn/root-url conn)))

(defn- keys-url
  [conn]
  (format "%s/keys" (conn/root-url conn)))

(defn- key-url
  [key-name conn]
  (format "%s/keys/%s" (conn/root-url conn) (codec/url-encode key-name)))

(defn safe-parse-json
  "Parses the string as json if it isn't. Swallows any exceptions."
  [s]
  (when s
    (try
      (json/decode s true)
      (catch Exception _
        s))))

(defn- http-response->raw-response
  "Parses a clj-http response and returns only the keys that we would normally be interested in.
  Parses the json in the body if the content type is JSON. The \"raw\" respond body is considered
  useful in cases where the exact status code is required such as in testing."
  [{:keys [status body headers]}]
  (let [content-type (mt/mime-type->format
                       (mt/mime-type-from-headers headers)
                       ;; don't return a default
                       nil)]
    {:status status
     :body (if (= content-type :json)
             (safe-parse-json body)
             body)
     :content-type content-type}))

(defn- handle-raw-fetch-response
  "Used to convert a raw response when fetching something into the actual result."
  [{:keys [status body] :as resp}]
  (cond
    (and (>= status 200) (<= status 299)) body
    (= status 404) nil
    :else (errors/internal-error!
            (format "Received unexpected status code %s with response %s"
                    status (pr-str resp)))))

(defn- handle-raw-update-response
  "Handles a raw response to an update request. Any non successful request is considered an error."
  [{:keys [status body] :as resp}]
  (when-not (and (>= status 200) (<= status 299))
    (errors/internal-error!
      (format "Received unexpected status code %s with response %s"
              status (pr-str resp)))))

(defn- cubby-request
  "Makes an HTTP request with the given options and parses the response. The arguments in the
  options map have the following effects.

  * :url-fn - A function taking a transmit connection and returning the URL to use.
  * :method - the HTTP method. :get, :post, etc.
  * :raw? - indicates whether the raw HTTP response (as returned by http-response->raw-response ) is
  desired. Defaults to false.
  * :http-options - a map of additional HTTP options to send to the clj-http.client/request function."
  [context {:keys [url-fn method raw? http-options]}]
  (let [conn (config/context->app-connection context :cubby)
        response (http-response->raw-response
                   (client/request
                     (merge {:url (url-fn conn)
                             :method method
                             :throw-exceptions false
                             :connection-manager (conn/conn-mgr conn)}
                            http-options)))]
    (cond
      raw? response
      (= method :get) (handle-raw-fetch-response response)
      :else (handle-raw-update-response response))))

(defn get-keys
  "Gets the stored keys of cached values as a raw response."
  ([context]
   (get-keys context false))
  ([context is-raw]
   (cubby-request context {:url-fn keys-url :method :get :raw? is-raw})))

(defn get-value
  "Gets the value associated with the given key."
  ([context key-name]
   (get-value context key-name false))
  ([context key-name is-raw]
   (cubby-request context {:url-fn (partial key-url key-name), :method :get, :raw? is-raw})))

(defn set-value
  "Associates a value with the given key."
  ([context key-name value]
   (set-value context key-name value false))
  ([context key-name value is-raw]
   (cubby-request context {:url-fn (partial key-url key-name),
                           :method :put,
                           :raw? is-raw
                           :http-options {:body value}})))

(defn delete-value
  "Dissociates the value with the given key."
  ([context key-name]
   (delete-value context key-name false))
  ([context key-name is-raw]
   (cubby-request context {:url-fn (partial key-url key-name), :method :delete, :raw? is-raw})))

(defn reset
  "Clears all values in the cache service"
  ([context]
   (reset context false))
  ([context is-raw]
   (cubby-request context {:url-fn reset-url, :method :post, :raw? is-raw})))

;; TODO this will be done in a subsequent pull request when elasticsearch is added.
(defn get-health
  "TODO"
  [context]
  )