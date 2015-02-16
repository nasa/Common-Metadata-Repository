(ns cmr.system-trace.context
  "Contains functions for storing and retrieving trace information in a request context.

  Main functions in the CMR system are expected to accept a context object. The shape of a context
  map is the following

  {:system {:zipkin { ;; items for zipkin config. See cmr.system-trace.context/zipkin-config
                    }
            ;; Other system level items. (db, cache, etc.)
           }
   :request {:trace-info { ;; trace level information, See cmr.system-trace.context/trace-info
                         }
             ;; Other fields here related to current request like token or current user.
            }
  }"
  (:require [clj-zipkin.tracer :as t]
            [thrift-clj.core :as thrift]
            [clj-scribe :as scribe]))

(thrift/import
  (:types [com.twitter.zipkin.gen Endpoint])
  (:clients com.twitter.zipkin.gen.ZipkinCollector))

(defn request-context
  "Creates a new request context with the given system and trace-info"
  [system request-id trace-info]
  {:system system
   :request {:request-id (or request-id (str (java.util.UUID/randomUUID)))
             :trace-info trace-info}})

(defn trace-info
  "Creates a new trace-info map which contains information about the operation being traced."
  ([]
   (trace-info nil nil))
  ([trace-id span-id]
   {;; Numeric identifer of a trace
    :trace-id trace-id
    ;; Numeric identifer of parent span
    :parent-span-id nil
    ;; The name of the current operation being traced.
    :span-name nil
    ;; Numeric identifer of current span
    :span-id span-id}))

(defn zipkin-config
  "Creates a map of the information needed when communicating with zipkin.
  * service-name - Name of the CMR service. This will be used in Zipkin spans to show current
  executing service.
  * ip - Ip address of the current host.
  * collector-host - Host where zipkin collector is running.
  * collector-port - Port zipkin collector is listening on."
  ([service-name enabled?]
   ;; Defaults good for local development
   (zipkin-config service-name "127.0.0.1" "127.0.0.1" 9410 enabled?))
  ([service-name ip collector-host collector-port enabled?]
   (let [scribe-logger (scribe/async-logger :host collector-host
                                            :port collector-port
                                            :category "zipkin")]
     {:endpoint (Endpoint. (t/ip-str-to-int ip) 0 service-name)
      :scribe-logger scribe-logger
      :enabled? enabled?})))

(defn tracing-enabled?
  "Returns true if tracing is enabled in the given context"
  [context]
  (get-in context [:system :zipkin :enabled?]))

(defn context->request-id
  "Extracts request id from a request context."
  [context]
  (get-in context [:request :request-id]))

(defn context->trace-info
  "Extracts trace-info from a request context."
  [context]
  (get-in context [:request :trace-info]))

(defn update-context-trace-info
  "Updates the trace-info in a request context."
  [context trace-info]
  (assoc-in context [:request :trace-info] trace-info))

(defn context->zipkin-config
  "Extracts zipkin config from a request context."
  [context]
  (get-in context [:system :zipkin]))

