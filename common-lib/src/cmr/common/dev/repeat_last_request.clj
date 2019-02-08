(ns cmr.common.dev.repeat-last-request
  "A special debug helper that allows the last request to be repeated.
  The debug-repl occasionally has trouble running in the thread Jetty starts. This captures requests
  that have been received and then can rerun them from the repl thread where debug-repl works better.")

(def last-request-atom (atom nil))

(defn save-last-request-handler
  "A ring handler that saves the last request received in an atom."
  [f]
  (fn [request]
    (reset! last-request-atom request)
    (f request)))

(defn wrap-api [api-fn]
  "Helper that will wrap the make-api function in routes. This should be called from the user
  namespace around the routes/make-api function to add the handler."
  (fn [& args]
    (save-last-request-handler (apply api-fn args))))

(defn repeat-last-request
  "Reruns the last ring request received from within the REPL. Added to make debug-repl work
  outside of Jetty thread."
  []
  (let [system (var-get (find-var 'user/system))
        routes-fn (get-in system [:web :routes-fn])]
   (if @last-request-atom
     (let [api-fn (routes-fn system)]
       (api-fn @last-request-atom))
     (println "No last request captured to repeat"))))
