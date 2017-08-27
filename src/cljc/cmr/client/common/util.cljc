(ns cmr.client.common.util
  (:require
   [cmr.client.common.const :as const]
   #?(:clj [clojure.core.async :as async :refer [go go-loop]]
      :cljs [cljs.core.async :as async]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(def default-environment-type :prod)

(defn get-endpoint
  [environment-type service-key]
  (->> (environment-type const/deployment-type)
       (vector service-key)
       (get-in const/endpoints)
       (str (environment-type const/hosts))))

(defn get-default-endpoint
  [options service-key]
  (or (:endpoint options)
      (get-endpoint default-environment-type service-key)))

(defn parse-endpoint
  ([endpoint]
   (parse-endpoint endpoint nil))
  ([endpoint service-key]
   (if (string? endpoint)
     endpoint
     (get-endpoint endpoint service-key))))

(defn ^:export with-callback
  [chan callback]
  (go-loop []
    (if-let [response (async/<! chan)]
      (callback response)
      (recur))))

(defn create-service-client-constructor
  "This is a utility function that returns a function for creating clients of
  a particular type, e.g., ingest, search, or access-control clients.

  The arguments details are as follows:

  * `service-type` - must be one of the supported service types, notably
    `:ingest`, `:search`, or `:access-control`
  * `client-constructor-var` - this is the var of that is assigned the value of
    the call to `create-service-client-constructor`; it is passed so that the
    anonymous function below has something to refer to in support of multiple
    arities
  * `client-data-constructor` - this is the constrcutor for the record that is
    used for the implementation of the protocol (that which is extended)
  * `options-fn` - a functin which creates the client client options, including
    basic defaults, for the implementation; it should be a function that in
    turn calls a `CMR*ClientOpions` constructor
  * `http-client-constructor` - a function that instrantiates the CMR HTTP
    client used by all CMR service type clients (different for Clojure and
    ClojureScript)

  This docstring is a bit dense; for more clarity, be sure to view the calls
  made to this function in both the Clojure and ClojureScript clients."
  [servie-type client-constructor-var client-data-constructor options-fn
   http-client-constructor]
  (fn
    ([]
     (client-constructor-var {}))
    ([options]
     (client-constructor-var options {}))
    ([options http-options]
     (let [endpoint (get-default-endpoint options servie-type)
           client-options (options-fn options)
           http-client (http-client-constructor client-options http-options)]
       (client-data-constructor (parse-endpoint endpoint servie-type)
                       client-options
                       http-client)))))

(defn create-http-client-constructor
  "This is a utility function that returns a function for creating clients of
  a particular type, e.g., ingest, search, or access-control clients.

  The arguments details are as follows:

  * `client-constructor-var` - this is the var of that is assigned the value of
    the call to `create-service-client-constructor`; it is passed so that the
    anonymous function below has something to refer to in support of multiple
    arities
  * `client-data-constructor` - this is the constrcutor for the record that is
    used for the implementation of the protocol (that which is extended)"
  [client-constructor-var client-data-constructor]
  (fn
    ([]
     (client-constructor-var {}))
    ([http-options]
     (client-constructor-var {} http-options))
    ([parent-client-options http-options]
     (client-data-constructor parent-client-options
                              http-options))))
