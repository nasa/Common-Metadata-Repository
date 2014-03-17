(ns cmr.system-trace.core
  (:require [cmr.system-trace.context :as c]
            [clj-zipkin.tracer :as t]
            [thrift-clj.core :as thrift]
            [clj-scribe :as scribe]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]))

(thrift/import
  (:types [com.twitter.zipkin.gen
           Span Annotation BinaryAnnotation AnnotationType Endpoint
           LogEntry StoreAggregatesException AdjustableRateException])
  (:clients com.twitter.zipkin.gen.ZipkinCollector))

(defn- record-span
  "Records the span info as a new span with annotations in Zipkin."
  [zipkin-config trace-info start-time stop-time]
  (let [{:keys [endpoint scribe-logger]} zipkin-config
        {:keys [span-name span-id parent-span-id trace-id]} trace-info
        a1 (Annotation. (* 1000 (time-coerce/to-long start-time))
                        (str "start:" span-name) endpoint 0)
        a2 (Annotation. (* 1000 (time-coerce/to-long stop-time))
                        (str "end:" span-name) endpoint 0)
        span (t/thrift->base64 (Span. trace-id span-name span-id parent-span-id [a1 a2] [] 0))]
    (clojure.pprint/pprint trace-info)
    (scribe/log scribe-logger [span])))

(defn- new-span-trace-info
  "Takes existing trace info from the context and the span name and creates a new trace info
  for this new span."
  [context span-name]
  (let [parent-trace-info (c/context->trace-info context)]
    {:trace-id (or (:trace-id parent-trace-info) (t/create-id))
     :parent-span-id (:span-id parent-trace-info)
     :span-name span-name
     :span-id (t/create-id)}))

(defn tracefn
  "Wraps a function with a new function that will trace the execution in Zipkin. Accepts the name of
  a span along with a function to trace. The first argument of the function should be the context. "
  [span-name f]
  (fn [context & args]
    (let [trace-info (new-span-trace-info context span-name)
          start-time (time/now)
          new-context (c/update-context-trace-info context trace-info)
          result (apply f new-context args)
          stop-time (time/now)]
      (record-span (c/context->zipkin-config) trace-info start-time stop-time)
      result)))

(defmacro deftracefn
  "Defines a traced function. A doc string must be defined. The first argument must be the context.
  The name of the function will be used as the span-name in Zipkin.
  Example:

  (deftracefn square-x
    \"Computes x^2 and returns the results.\"
    [context x]
    (* x x))"
  ;; Does not metadata missing doc string
  [var-name doc-string bindings & body]
  `(def ~var-name
     ~doc-string
     (tracefn ~(str var-name)
              (fn ~bindings ~@body))))

