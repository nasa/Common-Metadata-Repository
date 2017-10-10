(ns cmr.common-app.services.logging-config
  "Defines the log routes for applications. There are three main functions 1) To get the current
   CMR logging configuration and 2) Change the logging configuration and 3) to reset it to the
   application start configuration. To get the logging
   configuration from a service such as search locally one would use the following command:
   curl -H \"Echo-Token: XXXX\" http://localhost:3003/log where the port goes to the correct
   service. From the internet the command is:
   curl -H \"Echo-Token: XXXX\" http://cmr.earthdata.nasa.gov/search/log
   The above commands return the current CMR logging configuration to the caller. It will format the
   message to either a basic html if one of the accepted headers is text/html, or as formatted raw
   text if text/html is not included in the accept HTML header.
   To change the configuration one needs to provide an EDN map of the configuration you want to
   replace. For example if a parital configuration looks like the following:
   {:level :warn,
    :ns-whitelist [],
    :ns-blacklist [],
    :ns-pattern-map {:all :warn}}
    use the following commmand:
    curl -i -XPUT -H \"Echo-Token: XXXX\" -H \"Content-Type: application/edn\" <URL> -d
        '{:level :debug :ns-pattern-map {\"cmr.common-app.api.log\" :debug :all :warn}}'
    Where <URL> is either for example http://localhost:3003/log or
     http://cmr.earthdata.nasa.gov/search/log
    This command changes the overall logging level to debug - then sets the cmr.common-app.api.log
    namespace to debug and all other names spaces are set to warn.
    ******* Important! - The main logging level :level - must be set at the lowest setting that you
    want logged at the namespace level. Otherwise these messages will be filtered out. The
    middleware keyword of :all will set the logging level for all namespaces
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
   [cmr.common.mime-types :as mt]))

(defn get-log-configuration-response
  "Creates a successful log configuration response which is an output of the logging configuration map.
   This function takes in three parameters, the first is a boolean - if true the output is in html format
   otherwise the output will be a string representation of the configuration map. The second parameter is
   the status code and the third is the actual configuration map."
  [html? status-code data]
  (let [body-string (if html?
                      (str "<html><pre><code>" (with-out-str (pp/pprint data)) "</code></pre></html>")
                      (with-out-str (pp/pprint data)))
        content-type-header (if html?
                              {cr/CONTENT_TYPE_HEADER mt/html}
                              {cr/CONTENT_TYPE_HEADER mt/edn})]
    {:status status-code
     :body body-string
     :headers content-type-header}))

(defn get-log-error-response
  "Creates an error response if the content type is not correctly set. If it is not correctly set
   or not set at all, then the compojure wrap-params will consume the body and it will be blank.
   Therefore I will send back an error to correctly set the content-type. Otherwise I will have to
   save the body in another key (like search is doing) in all services and use that key instead.
   https://github.com/weavejester/compojure/issues/108"
  [status-code content-type]
  {:status status-code
   :body (str "The content type of "
              content-type
              " in the request is either not provided or is not of the correct type. Please set the
                request Content-Type to application/edn.")
   :headers {cr/CONTENT_TYPE_HEADER "text/plain"}})

(defn get-logging-config
  "This function gets the logging configuration map and formats it into a proper response. The
   passed in variable determins if the response is for an html accepting client or not."
  [html?]
  (get-log-configuration-response
    html?
    200
    (log/get-partial-logging-configuration)))

(defn get-logging-configuration
  "This function gets the logging configuration map to the caller. If a token is not passed in
   with the request or the user doesn't have permission, the acl will throw an exception. The
   exception is caught in base code and an appropriate error message is passed to the end user."
  [request-context headers]
  (acl/verify-ingest-management-permission request-context :update)
  (get-logging-config (mt/mime-type-exists? mt/html "accept" headers)))

(defn merge-logging-config
  "This function asks the logger to merge the passed in new configuration with the current logging
   configuration. It will pass back to the caller the new logging configuration and formats it
   into a proper response. The passed in variable determins if the response is for an html
   accepting client or not."
  [new-partial-config html?]
  ;; Put the string into an edn format.
  (let [edn-map-config (edn/read-string new-partial-config)]
    (log/warn (format "Merging a new CMR logging configuration map %s. The old configuration is %s."
                      edn-map-config
                      (with-out-str (pp/pprint (log/get-logging-configuration)))))
    (get-log-configuration-response
      html?
      200
      (log/merge-partial-logging-configuration edn-map-config))))

(defn merge-logging-configuration
  "This function asks the logger to merge the passed in configuration with the current logging
   configuration. If a token is not passed in with the request or the user doesn't have permission,
   the acl will throw an exception. The exception is caught in base code and an appropriate error
   message is passed to the end user."
  [request-context headers body]
  (acl/verify-ingest-management-permission request-context :update)
  (let [body-string (slurp body)]
    ;; This tests to see if the content type is correct. If the user didn't set the correct
    ;; content type or didn't set it at all then the compojure wrap-params will consume the
    ;; body and it will be blank when the body gets here.
    (if (string/blank? body-string)
      (get-log-error-response 400 (mt/get-header headers "Content-Type"))
      (merge-logging-config body-string (mt/mime-type-exists? mt/html "accept" headers)))))

(defn reset-logging-config
  "This function resets the logging configuration back as to when the application started. The
   passed in variable determins if the response is for an html accepting client or not."
  [html?]
  (get-log-configuration-response
    html?
    200
    (log/reset-logging-configuration)))

(defn reset-logging-configuration
  "This function resets the logging configuration back to what it started out as.
   If a token is not passed in with the request or the user doesn't have permission,
   the acl will throw an exception. The exception is caught in base code and an appropriate error
   message is passed to the end user. This function will return the reset logging configuration."
  [request-context headers]
  (acl/verify-ingest-management-permission request-context :update)
  (reset-logging-config (mt/mime-type-exists? mt/html "accept" headers)))
