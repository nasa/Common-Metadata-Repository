(ns user
  (:require
   [clojure.pprint :refer (pprint pp)]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [cmr.common.lifecycle :as l]
   [cmr.orbits.orbits-runtime :as orbits-runtime]
   [proto-repl.saved-values]))

(def system nil)

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system
                  (constantly
                   {orbits-runtime/system-key
                    (l/start (orbits-runtime/create-orbits-runtime) nil)})))


(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s]
                    (when s
                      (update s orbits-runtime/system-key #(l/stop % nil))))))

(defn reset []
  (stop)
  (refresh :after 'user/start))

(println "Custom user.clj loaded.")
