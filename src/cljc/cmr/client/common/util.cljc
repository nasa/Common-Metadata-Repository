(ns cmr.client.common.util
  "Utility functions for general use by both the Clojure client and the
  ClojureScript client."
  (:require
   [cmr.client.common.const :as const]
   #?(:clj [clojure.core.async :as async :refer [go go-loop]]
      :cljs [cljs.core.async :as async]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(defn get-endpoint
  "Get the default endpoint for a given CMR service. If an environment type
  is provided, override the default and get the endpoint for that type."
  ([service-key]
    (service-key const/default-endpoints))
  ([environment-type service-key]
   (->> (environment-type const/deployment-type)
        (vector service-key)
        (get-in const/endpoints)
        (str (environment-type const/hosts)))))

(defn get-default-endpoint
  "Get the default endpoint; if the client options specify an endpoint, then
  use that one."
  [options service-key]
  (or (:endpoint options)
      (get-endpoint service-key)))

(defn get-default-token
  "Use the token that is defined in the options data structure; if one is not
  provided, a null value for token will be used."
  [options]
  (:token options))

(defn parse-endpoint
  "Given a string or a deployment environment and a service key, retur the
  service endpoint."
  ([endpoint]
   (parse-endpoint endpoint nil))
  ([endpoint service-key]
   (if (string? endpoint)
     endpoint
     (get-endpoint endpoint service-key))))

(defn ^:export with-callback
  "A utility function for running a callback function when a channel receives
  data."
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
  [service-type client-constructor-var client-data-constructor options-fn
   http-client-constructor]
  (fn
    ([]
     (client-constructor-var {}))
    ([options]
     (client-constructor-var options {}))
    ([options http-options]
     (let [endpoint (get-default-endpoint options service-type)
           token (get-default-token options)
           client-options (options-fn options)
           http-client (http-client-constructor client-options http-options)]
       (client-data-constructor
         (parse-endpoint endpoint service-type)
         token
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

(defmacro import-def
  "Import a single function or var:
  ```
  (import-def a b) => (def b a/b)
  ```"
  [from-ns def-name]
  (let [from-sym# (symbol (str from-ns) (str def-name))]
    `(def ~def-name ~from-sym#)))

(defmacro import-vars
  "Import multiple defs from multiple namespaces.

   This works for vars and functions, but not macros. Uses the same syntax as
   `potemkin.namespaces/import-vars`, namely:
   ```
   (import-vars
     [m.n.ns1 a b]
     [x.y.ns2 d e f])
  ```"
  [& imports]
  (let [expanded-imports (for [[from-ns & defs] imports
                               d defs]
                           `(import-def ~from-ns ~d))]
  `(do ~@expanded-imports)))
