(ns user
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
   [cmr.graph.system.core :as system-api]
   [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(logger/set-level! '[cmr.graph] :info)

(def ^:dynamic *mgr* nil)

(defn banner
  []
  (println (slurp (io/resource "text/banner.txt")))
  :ok)

(defn mgr-arg
  []
  (if *mgr*
    *mgr*
    (throw (new Exception
                (str "A state manager is not defined; "
                     "have you run (startup)?")))))

(defn system-arg
  []
  (if-let [state (:state *mgr*)]
    (system-api/get-system state)
    (throw (new Exception
                (str "System data structure is not defined; "
                     "have you run (startup)?")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn startup
  []
  (alter-var-root #'*mgr* (constantly (system-api/create-state-manager)))
  (system-api/set-system-ns (:state *mgr*) "cmr.graph.components.core")
  (system-api/startup *mgr*))

(defn shutdown
  []
  (when *mgr*
    (let [result (system-api/shutdown (mgr-arg))]
      (alter-var-root #'*mgr* (constantly nil))
      result)))

(defn system
  []
  (system-api/get-system (:state (mgr-arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Reloading Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset
  []
  (shutdown)
  (repl/refresh :after 'user/startup))

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
