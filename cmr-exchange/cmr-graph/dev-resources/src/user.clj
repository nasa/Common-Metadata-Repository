(ns user
  "A dev namespace that supports Proto-REPL.

  It seems that Proto-REPL doesn't support the flexible approach that lein
  uses: any configurable ns can be the starting ns for a REPL. As such, this
  minimal ns was created for Proto-REPL users, so they too can have an env
  that supports startup and shutdown."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as httpc]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.tools.namespace.repl :as repl]
   [clojurewerkz.neocons.rest.cypher :as ncy]
   [clojurewerkz.neocons.rest.nodes :as nn]
   [clojurewerkz.neocons.rest.paths :as np]
   [clojurewerkz.neocons.rest.relationships :as nrl]
   [clojusc.system-manager.core :as system-api]
   [cmr.graph.components.core :as components]
   [cmr.graph.components.neo4j :as neo4j]
   [cmr.graph.config :as config]
   [cmr.graph.demo.movie :as movie-demo]
   [cmr.graph.repl :as dev]
   [cmr.graph.health :as health]))

(def setup-options {
  :init 'cmr.graph.components.core/init
  :after-refresh 'cmr.graph.repl/init-and-startup
  :throw-errors false})

(defn init
  []
  "This is used to set the options and any other global data.

  This is defined in a function for re-use. For instance, when a REPL is
  reloaded, the options will be lost and need to be re-applied."
  (system-api/setup-manager setup-options))

(defn init-and-startup
  []
  "This is used as the 'after-refresh' function by the REPL tools library.
  Not only do the options (and other global operations) need to be re-applied,
  the system also needs to be started up, once these options have be set up."
  (init)
  (system-api/startup))

;; It is not always desired that a system be started up upon REPL loading.
;; Thus, we set the options and perform any global operations with init,
;; and let the user determine when then want to bring up (a potentially
;; computationally intensive) system.
(init)

(def reset #'system-api/reset)
