(ns cmr.graph.dev
  "CMR Graph development namespace.

  This namespace is particularly useful when doing active development on the
  CMR Graph application."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as httpc]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.tools.namespace.repl :as repl]
   [clojurewerkz.elastisch.rest :as esr]
   [clojurewerkz.elastisch.rest.index :as esi]
   [clojurewerkz.elastisch.query :as esq]
   ;[clojurewerkz.elastisch.response :as esrsp]
   [clojurewerkz.elastisch.rest.document :as esd]
   [clojurewerkz.neocons.rest :as nr]
   [clojurewerkz.neocons.rest.cypher :as ncy]
   [clojurewerkz.neocons.rest.nodes :as nn]
   [clojurewerkz.neocons.rest.paths :as np]
   [clojurewerkz.neocons.rest.relationships :as nrl]
   [clojusc.twig :as logger]
   [cmr.graph.components.core :as components]
   [cmr.graph.components.neo4j :as neo4j]
   [cmr.graph.config :as config]
   [cmr.graph.demo.movie :as movie-demo]
   [cmr.graph.health :as health]
   [cmr.graph.system :as system-api]
   [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(logger/set-level! '[cmr.graph] :info)
(system-api/set-system-ns "cmr.graph.components.core")

(defn banner
  []
  (println (slurp (io/resource "text/banner.txt")))
  :ok)

(defn system-arg
  []
  (if-let [system (system-api/get-system)]
    system
    (throw (new Exception
                (str "System data structure is not defined; "
                     "have you run (startup)?")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def startup #'system-api/startup)
(def shutdown #'system-api/shutdown)

(defn system
  []
  (system-api/get-system))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Reloading Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset
  []
  (system-api/stop)
  (system-api/deinit)
  (repl/refresh :after 'cmr.graph.system/startup))

(def refresh #'repl/refresh)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Data   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; Demo functions
(def get-movie #(movie-demo/get-movie (neo4j/get-conn (system-arg)) %))
(def search-movie #(movie-demo/search (neo4j/get-conn (system-arg)) %))
(def get-movie-graph #(movie-demo/get-graph (neo4j/get-conn (system-arg)) %))

;;; Health functions
(def current-health #(health/components-ok? (system-arg)))
