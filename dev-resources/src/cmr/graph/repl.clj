(ns cmr.graph.repl
  "CMR Graph development namespace.

  This namespace is particularly useful when doing active development on the
  CMR Graph application."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as httpc]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.tools.namespace.repl :as repl]
   [clojurewerkz.elastisch.query :as esq]
   ;[clojurewerkz.elastisch.response :as esrsp]
   [clojurewerkz.elastisch.rest :as esr]
   [clojurewerkz.elastisch.rest.document :as esd]
   [clojurewerkz.elastisch.rest.index :as esi]
   [clojurewerkz.neocons.rest :as nr]
   [clojurewerkz.neocons.rest.cypher :as ncy]
   [clojurewerkz.neocons.rest.nodes :as nn]
   [clojurewerkz.neocons.rest.paths :as np]
   [clojurewerkz.neocons.rest.relationships :as nrl]
   [clojusc.system-manager.core :as system-api :refer :all]
   [clojusc.twig :as logger]
   [cmr.graph.components.core :as components]
   [cmr.graph.components.neo4j :as neo4j]
   [cmr.graph.config :as config]
   [cmr.graph.demo.movie :as movie-demo]
   [cmr.graph.health :as health]
   [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def setup-options {
  :init 'cmr.graph.components.core/init
  :after-refresh 'cmr.graph.repl/init-and-startup
  :throw-errors false})

(defn init
  []
  "This is used to set the options and any other global data.

  This is defined in a function for re-use. For instance, when a REPL is
  reloaded, the options will be lost and need to be re-applied."
  (logger/set-level! '[cmr] :debug)
  (setup-manager setup-options))

(defn init-and-startup
  []
  "This is used as the 'after-refresh' function by the REPL tools library.
  Not only do the options (and other global operations) need to be re-applied,
  the system also needs to be started up, once these options have be set up."
  (init)
  (startup))

;; It is not always desired that a system be started up upon REPL loading.
;; Thus, we set the options and perform any global operations with init,
;; and let the user determine when then want to bring up (a potentially
;; computationally intensive) system.
(init)

(defn banner
  []
  (println (slurp (io/resource "text/banner.txt")))
  :ok)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Data   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; Demo functions
(def get-movie #(movie-demo/get-movie (neo4j/get-conn (system)) %))
(def search-movie #(movie-demo/search (neo4j/get-conn (system)) %))
(def get-movie-graph #(movie-demo/get-graph (neo4j/get-conn (system)) %))

;;; Health functions
(def current-health #(health/components-ok? (system)))
