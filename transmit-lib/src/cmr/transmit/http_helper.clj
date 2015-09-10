(ns cmr.transmit.http-helper
  "Contains helpers for handling making http requests and processing responses."
  (:require [clj-http.client :as client]
            [cmr.common.mime-types :as mt]
            [cheshire.core :as json]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [cmr.common.services.errors :as errors]))

(defn- safe-parse-json
  "Tries to parse the string as json. Swallows any exceptions."
  [s]
  (when s
    (try
      (json/decode s true)
      (catch Exception _
        s))))

(defn- http-response->raw-response
  "Parses a clj-http response and returns only the keys that we would normally be interested in.
  Parses the json in the body if the content type is JSON. The \"raw\" response body is considered
  useful in cases where the exact status code is required such as in testing."
  [{:keys [status body headers]}]
  (cmr.common.dev.capture-reveal/capture-all)
  (let [content-type (mt/mime-type->format
                       (mt/content-type-mime-type headers)
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
    (<= 200 status 299) body
    (= status 404) nil
    :else (errors/internal-error!
            (format "Received unexpected status code %s with response %s"
                    status (pr-str resp)))))

(defn- handle-raw-update-response
  "Handles a raw response to an update request. Any non successful request is considered an error."
  [{:keys [status body] :as resp}]
  (if (<= 200 status 299)
    body
    (errors/internal-error!
      (format "Received unexpected status code %s with response %s"
              status (pr-str resp)))))

(defn request
  "Makes an HTTP request with the given options and parses the response. The arguments in the
  options map have the following effects.

  * :url-fn - A function taking a transmit connection and returning the URL to use.
  * :method - the HTTP method. :get, :post, etc.
  * :raw? - indicates whether the raw HTTP response (as returned by http-response->raw-response ) is
  desired. Defaults to false.
  * :http-options - a map of additional HTTP options to send to the clj-http.client/request function."
  [context app-name {:keys [url-fn method raw? http-options]}]
  (let [conn (config/context->app-connection context app-name)
        response (http-response->raw-response
                   (client/request
                     (merge (config/conn-params conn)
                            {:url (url-fn conn)
                             :method method
                             :throw-exceptions false
                             :headers {config/token-header (config/echo-system-token)}}
                            http-options)))]
    (cond
      raw? response
      (= method :get) (handle-raw-fetch-response response)
      :else (handle-raw-update-response response))))