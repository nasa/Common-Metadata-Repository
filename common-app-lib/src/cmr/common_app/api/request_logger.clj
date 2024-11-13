(ns cmr.common-app.api.request-logger
  "Defines a Ring Handlers for writing a CMR style request log. The handlers here
   can be controlled by settings in cmr.common-app.config. These logs are to be
   in addition to the standard NCSA Request logs issued by Ring but are different
   in content and format. This log will be in JSON and hold a large set of values."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common-app.config :as config]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.log :as log :refer [error report]]
   [cmr.common.util :as util]
   [cmr.common.time-keeper :as tk]
   [digest :as digest]
   [ring.middleware.params :as params]
   [ring.util.codec :as codec]))

(defn- request->uri
  "Reconstruct a url that a request client may have asked for (assuming port
   number was given). Special care is taken to scrub token from the URL parameter
   list if provided at 'token'."
  [request]
  ;; Another filter may have already parsed the parameters found at :query-params,
  ;; use that to save time, otherwise do it ourself from :query-string
  (let [raw-query (or (:query-params request) (codec/form-encode (:query-string request)))
        token (get raw-query "token")
        request-scheme (:scheme request)
        clean-query (if (nil? token)
                      raw-query
                      (assoc raw-query "token" (util/scrub-token token)))
        encoded-query (codec/form-encode clean-query)]
    ;; build: <protocol>://<server>:<port><path><question-mark><params>
    (format "%s://%s:%s%s%s%s"
            (when request-scheme (name request-scheme))
            (or (:server-name request) nil)
            (or (:server-port request) nil)
            (or (:uri request) nil)
            (if (empty? encoded-query) "" "?") ;; don't show if no query
            encoded-query)))

(defn- scrub-token-from-map
  "Scrub out a token keyed at field-name from a map"
  ([data] (scrub-token-from-map data :token))
  ([data field-name]
   (let [token (get data field-name)]
     (if token
       (assoc data field-name (util/scrub-token token))
       data))))

(def max-dump-params
  "The maximum number of parameters to dump to logs due to the issue of storing large logs."
  128)

(defn- dump-param
  "Dump out a form post parameter or url parameter map from inside a given
   request object taking care to mask tokens and to limit the number of actual
   values that will be logged. Defaults (2 parameter call) to 128 parameters."
  ([data field] (dump-param data field max-dump-params))
  ([data field limit]
   (-> data
       (get field {})
       ;; check for and scrub out token values to make safe for logging
       (scrub-token-from-map)
       (scrub-token-from-map "token")
       ;; Parameters in forms could be really large, don't log everything
       (as-> item (apply dissoc item (drop limit (keys item)))))))

(defn- normalize-local
  "IP 6 addresses are ugly and wildly different then IP 4, normalize these so that all localhost
   searches can be found in logs regardless of technology used to deliver the request."
  [raw-address]
  (if (contains? (set ["[0:0:0:0:0:0:0:1]" "127.0.0.1"]) raw-address)
    "localhost"
    raw-address))

(defn- add-hash-to-data
  "Conditionally adds md5 and sha1 hashes if the log level is either debug or
   trace. These values are pulled from the response map."
  [data response]
  (let [log-level (log/apparent-log-level)]
    (if (contains? (set [:debug :trace]) log-level)
      (-> data
          (assoc "body-md5" (get-in response [:headers "Content-MD5"]))
          (assoc "body-sha1" (get-in response [:headers "Content-SHA1"]))
          (util/remove-nil-keys))
      data)))

;; *****************************************************************************
;; Ring Middleware Handlers

(defn add-body-hashes
  "Calculate the MD5 and SHA1 hash for the body of a response and include those
   results in the response as HTTP headers. Headers are called Content-MD5 and
   Content-SHA1. Content-MD5 is mentioned on the Wikipedia page:
   https://en.wikipedia.org/wiki/List_of_HTTP_header_fields#Standard_request_fields
   so it is presumed to be in common use."
  [handler]
  (fn [request]
    (let [response (handler request)
          ;; This is run after all responses
          text (str (:body response))
          body-md5 (digest/md5 text)
          body-sha1 (digest/sha-1 text)
          updated-response (-> response
                               (assoc-in [:headers "Content-MD5"] body-md5)
                               (assoc-in [:headers "Content-SHA1"] body-sha1))]
      updated-response)))

(defn add-time-stamp
  "Add the current time to the request"
  [handler]
  (fn [request]
    (if (some? (:ring-start-time request))
      (handler request)
      (handler (assoc request
                      :ring-start-now (tk/now)
                      :ring-start-time (tk/now-ms))))))

;; log-ring-request should provide the same info as a standard NCSA Log
;; ; 127.0.0.1 - - [2023-12-27 19:04:01.676] "GET /collections?keyword=any HTTP/1.1" 200 112 "-" "curl/8.1.2" 296

;; Fields are as follows:
;; ;
;; IP Address
;; login (ignored)
;; -
;; login (ignored)
;; -
;; [date-time]
;; "
;; HTTP VERB
;; Relative URL
;; "
;; Status code
;; response size
;; "-"
;; agent
;; time

(defn log-ring-request
  "Log a request from Ring returning JSON data to be parsed by a log tool like Splunk or ELK. This
   handler can be called multiple times at different positions in the handlers call but it does
   depend on common-routes/add-request-id-response-handler and add-time-stamp. When calling this
   function multiple times in the routes list, then pass in an ID (the two arity call) and the
   output will include the ID value in the log so that log entries can be told apart. One may want
   to do this to see when a value logged becomes valid in the routes chain and thus availible
   downstream.

   NOTE: If you pass in an ID of :ignore then no ID will be written out, the same behavior if you
   call the single arity call."
  ([handler]
   (log-ring-request handler :ignore-id))
  ([handler id]
   (fn [request]
     (if-not (config/enable-enhanced-http-logging)
       (handler request)
       (let [response (handler request) ;; Do all the response handlers first
             ;; Do all the time based captures upfront
             log-start (tk/now-ms) ;; After processing the other handlers, start tracking log time
             ring-start (get request :ring-start-time (tk/now-ms))
             ring-now (get request :ring-start-now (tk/now))
             ;; processing of logs can now start
             now-text (dtp/clj-time->date-time-str ring-now)
             _ (when-not (:ring-start-time request) ;; Did someone forget to setup a routes.clj
                 (error "There is no ring start time for this service" (request->uri request)))
             query-params (params/assoc-query-params request "UTF-8")
             form-params (params/assoc-form-params request "UTF-8")
             note (-> {"message-type" "request-log"}
                      (add-hash-to-data response)
                      ;; As this handler can be called multiple times, if so,
                      ;; include an id showing which instance is reporting this
                      ;; information.
                      (as-> data (if (= id :ignore-id) data (assoc data "log-id" id)))
                      ;; assume that (add-body-hashes) has been run and reuse data
                      (into (:headers request)) ;; Bring in all the existing headers
                      (assoc "cmr-hit" (get-in response [:headers "CMR-Hit"] nil)
                             "cmr-took" (get-in response [:headers "CMR-Took"] nil)
                             "form-params" (dump-param form-params :form-params)
                             "method" (string/upper-case
                                       (name (get-in request [:request-method] "unknown")))
                             "now" now-text
                             "now-ms" ring-start
                             "query-params" (dump-param query-params :query-params)
                             "remote-address" (normalize-local (:remote-addr request))
                             "request-id" (get-in response [:headers "CMR-Request-Id"] "n/a")
                             "status" (:status response)
                             "uri" (get-in request [:uri] "/")
                             "url" (request->uri request)
                             ;; do these last
                             "log-cost" (- (tk/now-ms) log-start)
                             "duration" (- (tk/now-ms) ring-start))
                      (util/remove-nil-keys))
             note (if (contains? note "authorization")
                    (assoc note "authorization" "****")
                    note)]
         (report (json/generate-string note))
         response)))))

;; *************************************

(comment
  ;; Notes on using ring code:

  ;; We can reuse the ring code for parsing body parameters like so (instead of
  ;; writing our own) but all the values must be exactly correct in case and setup
  ;; Use the following as an example to understand what is expected of a request object
  ;; and what you will get back.
  (let [body-stream (clojure.java.io/reader (char-array "p1=a&p2=b"))
        request {:headers {"content-type" "application/x-www-form-urlencoded;anything-allowed-here"}
                 :body body-stream}
        foo (log-ring-request {})]
    ;;(foo {}) ;;uncomment this to run the log-ring-request and test it out
    (ring.middleware.params/assoc-form-params request "UTF-8"))

  ;; and query paramas are simple and can be done as the following:
  (ring.middleware.params/assoc-query-params {:query-string "&keyword=value"} "UTF-8"))
