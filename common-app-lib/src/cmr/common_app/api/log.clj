(ns cmr.common-app.api.log
  "Defines the log routes for applications. There are two main functions 1) To get the current
   CMR logging configuration and 2) Change the logging configuration. To get the logging
   configuration from a service such as search locally one would use the following command:
   curl -H \"Echo-Token: XXXX\" http://localhost:3003/log where the port goes to the correct service.
   From the internet the command is:
   curl -H \"Echo-Token: XXXX\" http://cmr.earthdata.nasa.gov/search/log
   The above commands return the current CMR logging configuration to the caller. It will format the
   message to either a basic html if one of the accepted headers is text/html, or as formatted raw text
   if text/html is not included in the accept HTML header.
   To change the configuration one needs to provide an EDN map of the configuration you want to replace.
   For example if the configuraiton looks like the following:
   {:level :warn,
    :ns-whitelist [],
    :ns-blacklist [],
    :middleware [#function[cmr.common.log/log-by-ns-pattern]],
    :timestamp-opts
    {:pattern \"yyyy-MM-dd HH:mm:ss.SSS\",
     :locale :jvm-default,
     :timezone :utc},
    :output-fn #function[cmr.common.log/log-formatter],
    :appenders
    {:println
     {:enabled? true,
      :async? false,
      :min-level nil,
      :rate-limit nil,
      :output-fn :inherit,
      :fn
      #function[taoensso.timbre.appenders.core/println-appender$fn--10435]}},
    :ns-pattern-map
    {:all :warn}}
    use the following commmand:
    curl -i -XPUT -H \"Echo-Token: XXXX\" -H \"Content-Type: application/edn\" <URL> -d
        '{:level :debug :ns-pattern-map {\"cmr.common-app.api.log\" :debug :all :warn}}'
    Where <URL> is either for example http://localhost:3003/log or
     http://cmr.earthdata.nasa.gov/search/log
    This command changes the overall logging level to debug - then sets the cmr.common-app.api.log
    namespace to debug and all other names spaces are set to warn.
    ******* Important! - The main logging level :level - must be set at the lowest setting that you
    want logged at the namespace level. Otherwise Timbre will filter these logs out before the logs
    make it to the middleware.  The middleware :all will set the logging level for all namespaces
    not listed - set this to what :level used to be. *****
    The change command will return the new logging configuration."
  (:require
   [cmr.acl.core :as acl]
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.pprint :as pp]
   [clojure.string :as string]
   [cmr.common.log :as log]
   [cmr.common-app.api.routes :as cr]
   [cmr.common.mime-types :as mt]
   [compojure.core :refer :all]))

(defn get-log-configuration-response
  "Creates a successful log configuration response which is an output of the logging configuration map.
   This function takes in three parameters, the first is a boolean - if true the output is in html format
   otherwise the output will be a string representation of the configuration map. The second parameter is
   the status code and the third is the actual configuration map."
  [html? status-code data]
  (let [body-string (if html?
                      (str "<html><pre><code>" (with-out-str (pp/pprint data)) "</code></pre></html>")
                      (with-out-str (pp/pprint data)))
        content (if html?
                  {cr/CONTENT_TYPE_HEADER mt/html}
                  {cr/CONTENT_TYPE_HEADER mt/edn})]
    {:status status-code
     :body body-string
     :headers content}))

(defn get-log-error-response
  "Creates an error response if the content type is not correctly set. If it is not correctly set
   or not set at all, then the compojure wrap-params will consume the body and it will be blank.
   Therefore I will send back an error to correctly set the content-type. Otherwise I will have to
   save the body in another key (like search is doing) in all services and use that key instead."
  [status-code content-type]
  {:status status-code
   :body (str "The content type of "
              content-type
              " in the request is either not provided or is not of the correct type. Please set the request Content-Type to application/edn.")
   :headers {cr/CONTENT_TYPE_HEADER "text/plain"}})

(defn get-logging-configuration
  "This function gets the logging configuration map to the caller. If a token is not passed in
   with the request or the user doesn't have permission, the acl will through an exception. The
   exception is caught in base code and an appropriate error message is passed to the end user."
  [request-context headers]
  (acl/verify-ingest-management-permission request-context :update)
  (get-log-configuration-response
    (.contains (mt/get-header headers "accept") "text/html")
    200
    (log/get-logging-configuration)))

(defn merge-log-configuration
  "This function asks the logger to merge the passed in configuration with the current logging
   configuration. If a token is not passed in with the request or the user doesn't have permission,
   the acl will through an exception. The exception is caught in base code and an appropriate error
   message is passed to the end user."
  [request-context headers body]
  (acl/verify-ingest-management-permission request-context :update)
  (let [body-string (slurp body)]
    ;; This tests to see if the content type is correct. If the user didn't set the correct
    ;; content type or didn't set it at all then the compojure wrap-params will consume the
    ;; body and it will be blank when the body gets here.
    (if (string/blank? body-string)
      (get-log-error-response 400 (mt/get-header headers "Content-Type"))
      ;; Put the string into an edn format.
      (let [edn-map-config (edn/read-string body-string)]
        (log/warn (format "Merging a new CMR logging configuration map %s. The old configuration is %s."
                          edn-map-config
                          (with-out-str (pp/pprint (log/get-logging-configuration)))))
        (get-log-configuration-response
          (.contains (mt/get-header headers "accept") "text/html")
          200
          (log/merge-logging-configuration edn-map-config))))))

(def log-api-routes
  "Routes for changing the logging configuration endpoints"
  (context "/log" []

    ;; update the logging configuration
    (PUT "/"
         {:keys [request-context headers body]}
         (println "request-context:" request-context)
         (println "headers:" headers)
         (println "body:" body)
         (merge-log-configuration request-context headers body))

    ;; retrieve the logging configuration
    (GET "/"
         {:keys [request-context headers]}
         (get-logging-configuration request-context headers))))
