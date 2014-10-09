(ns cmr.system-trace.test-tracing
  "Defines helper macros to enable system tracing that starts from the system tests."
  (:require [clojure.test]
            [cmr.system-trace.core :as t]
            [cmr.system-trace.context :as c]))

(def testing-context
  "A request context to use for tracing related calls."
  (c/request-context
    {:zipkin (c/zipkin-config "Sys Int Tests" true)}
    (c/trace-info)))

(defmacro testing
  "Replaces the existing clojure.test/testing macro to enable tracing at that level.

  It takes an additional set of bindings after the name of the test. The bindings should
  have one argument which will be the context.

  Example:
  (require '[cmr.system-trace.test-tracing :as t])
  (deftest a-good-feature-test
    (t/testing \"using the good parameters\" [context]
      (invoke-service context)))"
  [name bindings & body]
  `(clojure.test/testing
     ~name
     (let [traced-fn# (t/tracefn ~name (fn ~bindings ~@body))]
       (traced-fn# testing-context))))

