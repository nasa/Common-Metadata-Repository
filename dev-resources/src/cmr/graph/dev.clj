(ns cmr.graph.dev
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
   [cmr.graph.dev.system :as dev-system]
   [cmr.graph.health :as health]
   [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(logger/set-level! ['cmr.graph] :info)
(dev-system/set-system-ns "cmr.graph.components.core")

(defn banner
  []
  (println (slurp (io/resource "text/banner.txt")))
  :ok)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def startup #'dev-system/startup)
(def shutdown #'dev-system/shutdown)
(def system (fn [] dev-system/system))

(defn system-arg
  []
  (if-let [system (system)]
    system
    (throw (new Exception
                "System data structure is nil; have you run (startup)?"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Reloading Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset
  []
  (dev-system/shutdown)
  (repl/refresh :after 'cmr.graph.dev.system/startup))

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
