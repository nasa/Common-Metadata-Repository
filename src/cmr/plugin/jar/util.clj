(ns cmr.plugin.jar.util)

(defmacro when-let*
  [bindings & body]
  (if (seq bindings)
    `(when-let [~(first bindings) ~(second bindings)]
       (when-let* ~(drop 2 bindings) ~@body))
    `(do ~@body)))
