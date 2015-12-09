(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.spatial.kml :as kml]
            [clojure.java.shell :as shell])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        ;; Needed to make debug-repl available
        [alex-and-georges.debug-repl]
        [cmr.common.dev.capture-reveal]))


(defn start
  "Starts the current development system."
  [])


(defn stop
  "Shuts down and destroys the current development system."
  [])

(defn display-shapes
  "Saves the shapes as KML and opens the file in Google Earth."
  ([shapes]
   (display-shapes shapes "ge_scratch.kml"))
  ([shapes filename]
   (spit filename (kml/shapes->kml shapes))
   (shell/sh "open" filename)))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))
(println "Custom user.clj loaded.")
