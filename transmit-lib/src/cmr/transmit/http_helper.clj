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
  (let [content-type (mt/mime-type->format
                       (mt/content-type-mime-type headers)
                       ;; don't return a default
                       nil)]
    {:status status
     :body (if (= content-type :json)
             (safe-parse-json body)
             body)
     :content-type content-type}))

(defn- handle-raw-fetch-or-delete-response
  "Used to convert a raw response when fetching or deleting something into the actual result."
  [{:keys [status body] :as resp}]
  (cond
    (<= 200 status 299) body
    (= status 404) nil
    :else (errors/internal-error!
            (format "Received unexpected status code %s with response %s"
                    status (pr-str resp)))))

(defn handle-raw-write-response
  "Handles a raw response to an update request. Any non successful request is considered an error."
  [{:keys [status body] :as resp}]
  (if (<= 200 status 299)
    body
    (errors/internal-error!
      (format "Received unexpected status code %s with response %s"
              status (pr-str resp)))))

(defn update-response-handler
  "Response handler for update requests."
  [concept-id request {:keys [status body] :as resp}]
  (cond
    (<= 200 status 299)
    body

    (= 404 status)
    (errors/throw-service-error
     :not-found (format "Item could not be found with id [%s]" concept-id))

    :else
    (errors/internal-error!
      (format "Received unexpected status code %s with response %s"
              status (pr-str resp)))))

(defn default-response-handler
  "The default response handler."
  [request response]
  (let [{:keys [method raw?]} request]
    (cond
      raw? response
      (or (= method :get) (= method :delete)) (handle-raw-fetch-or-delete-response response)
      :else (handle-raw-write-response response))))

(defn request
  "Makes an HTTP request with the given options and parses the response. The arguments in the
  options map have the following effects.

  * :url-fn - A function taking a transmit connection and returning the URL to use.
  * :method - the HTTP method. :get, :post, etc.
  * :raw? - indicates whether the raw HTTP response (as returned by http-response->raw-response ) is
  desired. Defaults to false.
  * :use-system-token? - indicates if the ECHO system token should be put in the header
  * :http-options - a map of additional HTTP options to send to the clj-http.client/request function.
  * :response-handler - a function to handle the response. Defaults to default-response-handler"
  [context app-name {:keys [url-fn method http-options response-handler use-system-token?] :as request}]
  (let [conn (config/context->app-connection context app-name)
        response-handler (or response-handler default-response-handler)
        response (http-response->raw-response
                   (client/request
                     (merge (config/conn-params conn)
                            {:url (url-fn conn)
                             :method method
                             :throw-exceptions false}
                            (when use-system-token?
                              {:headers {config/token-header (config/echo-system-token)}})
                            http-options)))]
    (default-response-handler request response)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CRUD Macros
;; These are macros for defining standard CRUD operations for objects.
;; * Objects are sent and retrieved in JSON.
;; * Create and Update functions expect that they'll be able to succeed. It's considered an internal
;; error if an object sent is invalid. This can be bypassed by sending :raw? option to get the
;; raw HTTP response back.

(defmacro defcreator
  "Creates a function that can be used to send standard requests to create an item using JSON."
  ([fn-name app-name url-fn]
   `(defcreator ~fn-name ~app-name ~url-fn nil))
  ([fn-name app-name url-fn default-options]
   `(defn ~fn-name
      "Sends a request to create the item. Valid options are
      * :raw? - set to true to indicate the raw response should be returned. See
      cmr.transmit.http-helper for more info. Default false.
      * token - the user token to use when creating the group. If not set the token in the context will
      be used.
      * http-options - Other http-options to be sent to clj-http."
      ([context# item#]
       (~fn-name context# item# nil))
      ([context# item# options#]
       (let [options# (merge options# ~default-options)
             {raw?# :raw? token# :token http-options# :http-options} options#
             token# (or token# (:token context#))
             headers# (when token# {config/token-header token#})]
         (request context# ~app-name
                  {:url-fn ~url-fn
                   :method :post
                   :raw? raw?#
                   :http-options (merge {:body (json/generate-string item#)
                                         :content-type :json
                                         :headers headers#
                                         :accept :json}
                                        http-options#)}))))))

(defmacro defupdater
  "Creates a function that can be used to send standard requests to updater an item using JSON. The
  url-fn will be passed the connection and the concept id"
  ([fn-name app-name url-fn]
   `(defupdater ~fn-name ~app-name ~url-fn nil))
  ([fn-name app-name url-fn default-options]
   `(defn ~fn-name
      "Sends a request to update the item. Valid options are
      * :raw? - set to true to indicate the raw response should be returned. See
      cmr.transmit.http-helper for more info. Default false.
      * token - the user token to use when creating the group. If not set the token in the context will
      be used.
      * http-options - Other http-options to be sent to clj-http."
      ([context# concept-id# item#]
       (~fn-name context# concept-id# item# nil))
      ([context# concept-id# item# options#]
       (let [options# (merge options# ~default-options)
             {raw?# :raw? token# :token http-options# :http-options} options#
             token# (or token# (:token context#))
             headers# (when token# {config/token-header token#})]
         (request context# ~app-name
                  {:url-fn #(~url-fn % concept-id#)
                   :response-handler (partial update-response-handler concept-id#)
                   :method :put
                   :raw? raw?#
                   :http-options (merge {:body (json/generate-string item#)
                                         :content-type :json
                                         :headers headers#
                                         :accept :json}
                                        http-options#)}))))))

(defmacro defdestroyer
  "Creates a function that can be used to send standard requests to delete an item using JSON. The
  url-fn will be passed the connection and the concept id"
  ([fn-name app-name url-fn]
   `(defdestroyer ~fn-name ~app-name ~url-fn nil))
  ([fn-name app-name url-fn default-options]
   `(defn ~fn-name
      "Sends a request to delete an item. Valid options are
      * :raw? - set to true to indicate the raw response should be returned. See
      cmr.transmit.http-helper for more info. Default false.
      * token - the user token to use when creating the token. If not set the token in the context will
      be used.
      * http-options - Other http-options to be sent to clj-http."
      ([context# concept-id#]
       (~fn-name context# concept-id# nil))
      ([context# concept-id# options#]
       (let [options# (merge options# ~default-options)
             {raw?# :raw? token# :token http-options# :http-options} options#
             token# (or token# (:token context#))
             headers# (when token# {config/token-header token#})]
         (request context# ~app-name
                  {:url-fn #(~url-fn % concept-id#)
                   :method :delete
                   :raw? raw?#
                   :http-options (merge {:headers headers#
                                         :accept :json}
                                        http-options#)}))))))


(defmacro defgetter
  "Creates a function that can be used to send standard requests to request an item using JSON. The
  url-fn will be passed the connection and the concept id"
  ([fn-name app-name url-fn]
   `(defgetter ~fn-name ~app-name ~url-fn nil))
  ([fn-name app-name url-fn default-options]
   `(defn ~fn-name
      "Sends a request to get an item by concept id. Valid options are
      * :raw? - set to true to indicate the raw response should be returned. See
      cmr.transmit.http-helper for more info. Default false.
      * token - the user token to use when creating the group. If not set the token in the context will
      be used.
      * http-options - Other http-options to be sent to clj-http."
      ([context# concept-id#]
       (~fn-name context# concept-id# nil))
      ([context# concept-id# options#]
       (let [options# (merge options# ~default-options)
             {raw?# :raw? token# :token http-options# :http-options} options#
             token# (or token# (:token context#))
             headers# (when token# {config/token-header token#})]
         (request context# ~app-name
                  {:url-fn #(~url-fn % concept-id#)
                   :method :get
                   :raw? raw?#
                   :http-options (merge {:headers headers# :accept :json}
                                        http-options#)}))))))

(defmacro defsearcher
  "Creates a function that can be used to send find items matching parameters."
  ([fn-name app-name url-fn]
   `(defsearcher ~fn-name ~app-name ~url-fn nil))
  ([fn-name app-name url-fn default-options]
   `(defn ~fn-name
      "Sends a request to find items by parameters. Valid options are
      * :raw? - set to true to indicate the raw response should be returned. See
      cmr.transmit.http-helper for more info. Default false.
      * token - the user token to use when creating the group. If not set the token in the context will
      be used.
      * http-options - Other http-options to be sent to clj-http."
      ([context# params#]
       (~fn-name context# params# nil))
      ([context# params# options#]
       (let [options# (merge options# ~default-options)
             {raw?# :raw? token# :token http-options# :http-options} options#
             token# (or token# (:token context#))
             headers# (when token# {config/token-header token#})]
         (request context# ~app-name
                  {:url-fn ~url-fn
                   :method :get
                   :raw? raw?#
                   :http-options (merge {:headers headers#
                                         :query-params params#
                                         :accept :json}
                                        http-options#)}))))))



