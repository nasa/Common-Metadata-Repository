(ns cmr.common-app.api.request-logger
  "Defines a Ring Handler for writing a CMR style request log. The handlers here
   can be controlled by settings in cmr.common-app.config."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common-app.config :as common-config]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as util]
   [cmr.common.time-keeper :as tk]
   [digest :as digest]
   [ring.util.codec :as codec]))

(defn- request->uri
  [request]
  "Reconstruct a url that a request client may have asked for (assuming port
   number was given). Special care is taken to scrub token from the URL parameter
   list if provided at 'token'."
  (let [raw-query (:query-params request)
        token (get raw-query "token")
        request-scheme (:scheme request)
        clean-query (if (nil? token)
                      raw-query
                      (assoc raw-query "token" (util/scrub-token token)))]
    ;; <protocol>://<server>:<port><path><?><params>
    (format "%s://%s:%s%s%s%s"
            (when request-scheme (name request-scheme))
            (or (:server-name request) nil)
            (or (:server-port request) nil)
            (or (:uri request) nil)
            (if (empty? clean-query) "" "?") ;; don't show if no query
            (codec/form-encode clean-query))))

(defn- scrub-token-from-map
  "Scrub out a token keyed at field-name from a map"
  ([data] (scrub-token-from-map data :token))
  ([data field-name]
   (let [token (get data field-name)]
     (if token
       (assoc data field-name (util/scrub-token token))
       data))))

(defn- dump-param
  "Dump out a form post parameter or url parameter map from inside a given
   request object taking care to mask tokens and to limit the number of actual
   values that will be logged. Defaults (2 parameter call) to 128 parameters."
  ([data field] (dump-param data field 128))
  ([data field limit]
   (-> data
       (get field {})
       ;; check for and scrub out token values to make safe for logging
       (scrub-token-from-map)
       (scrub-token-from-map "token")
       ;; Parameters in forms could be really large, don't log everything
       (as-> item (apply dissoc item (drop limit (keys item)))))))

(comment

  (request->uri my-request)

  (let [data {:a {:left :0 :right :1 :token "bad-value"}
              :b {:left :2 :right :3 "token" "value-bad"}
              :c {:left :4 :right :5}}]
    (for [x (keys data)]
      (dump-param data x))))

;; *****************************************************************************
;; Ring Middleware Handlers

(defn add-body-hashes
  [handler]
  (fn [request]
    (if-not (common-config/add-hash-headers)
      (handler request)
      (let [response (handler request)
          ; after all responses
            text (str (:body response))
            body-md5 (digest/md5 text)
            body-sha1 (digest/sha-1 text)
            updated-response (-> response
                                 (assoc-in [:headers "Content-MD5"] body-md5)
                                 (assoc-in [:headers "Content-SHA1"] body-sha1))]
        updated-response))))

;; provider the same info as a standard NCSA Log
;; ; 127.0.0.1 - - [2023-12-27 19:04:01.676] "GET /collections?keyword=any HTTP/1.1" 200 112 "-" "curl/8.1.2" 296
;; Fields are as follows:
;; ip address
;; date-time
;; HTTP VERB
;; Relative URL
;; Status code
;; response size
;; agent

(defn action-logger
  "Log a request from Ring returning JSON data to be parsed by a log tool like
   Splunk or ELK. This handler can be called multiple times at different positions
   in the handlers call. When calling multiple times then pass in an ID (two
   arity call) and the output will include this value in the log so that log
   entries can be told appart. One may want to do this to see when a value logged
   becomes valid and thus availible downstream."
  ([handler]
   (action-logger handler :ignore))
  ([handler id]
   (def my-handler handler)
   (fn [request]
     (def my-request request)
     (if-not (cmr.common-app.config/custom-request-log)
       (handler request)
       (let [start (tk/now-ms)
             now (dtp/clj-time->date-time-str (tk/now))
             response (handler request)
             note (-> {"log-version" "1.0.0"}
                    ;; As this handler can be called multiple times, if so,
                    ;; include an id showing which instance is reporting this
                    ;; information.
                      (as-> data (when-not (= id :ignore) (assoc data "log-id" id)))
                    ;; assume that (add-body-hashes) has been run and reuse data
                      (assoc "body-md5" (get-in response [:headers "Content-MD5"]))
                      (assoc "body-sha1" (get-in response [:headers "Content-SHA1"]))
                      (assoc "client-id" (get-in request [:headers "client-id"] "unknown"))
                      (assoc "form-params" (dump-param request :form-params))
                      (assoc "method" (clojure.string/upper-case (name (:request-method request))))
                      (assoc "now" now)
                      (assoc "protocol" (:protocol request))
                      (assoc "query-params" (dump-param request :query-params))
                      (assoc "remote-address" (:remote-addr request))
                      (assoc "request-id" (get-in response [:headers "CMR-Request-Id"] "-to early-"))
                      (assoc "status" (:status response))
                      (assoc "took" (get-in response [:headers "CMR-Took"] "n/a"))
                      (assoc "uri" (request->uri request))
                      (assoc "user-agent" (get-in request [:headers "user-agent"] "unknown"))
                    ;; do this last
                      (assoc "log-cost" (- (tk/now-ms) start)))]
       ;; send the log to standard error in the same way that the ring access log does
         (.println *err* (json/generate-string note))
         (def my-response response)
         response)))))

(comment

  (- (tk/now-ms) (tk/now-ms))

  (let [data {:a :b :c :d :e :f :g :h}]
    (apply dissoc data (drop 128 (keys data))))

  (let [id :ignores
        test (when-not (= id :ignore) :something)]
    test)

  (dtp/clj-time->date-time-str (tk/now))

  (:headers my-request)
  (:headers (assoc-in my-request [:headers :action-logger] "here inaction"))

  (dissoc my-request :request-context :body)
  (keys my-handler)
  (keys my-request)
  (keys my-response)


  (:content-type my-request)
  (:headers my-request)
  (:form-params my-request)
  (:query-params my-request)

  (my-handler my-request)
  (my-handler (assoc-in my-request [:headers "Action-Logger"] "here in action"))

  (get my-handler "CMR-Took", "zero")

  (-> my-request
      (dissoc :body-copy :ssl-client-cert :params :headers :content-length :form-params :content-type :character-encoding :body :multipart-params :request-context))
  (format "%s://%s%s?%s"
          (name (:scheme my-request))
          (:server-name my-request)
          (:uri my-request)
          (:query-string my-request))

  (digest/md5 (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?><results><hits>0</hits><took>38</took><references></references></results>")))


