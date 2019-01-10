(ns cmr.exchange.common.dev
  "CMR OPeNDAP development namespace."
  (:require
   [clojure.java.classpath :as classpath]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.system-manager.core :as system-api :refer :all]
   [clojusc.twig :as logger]
   [cmr.exchange.common.components.config :as config]
   [cmr.exchange.common.components.core]
   [cmr.exchange.common.file :as file]
   [cmr.exchange.common.util :as util]
   [com.stuartsierra.component :as component]
   [trifl.java :refer [show-methods]])
  (:import
   (java.net URI)
   (java.nio.file Paths)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def setup-options {
  :init 'cmr.exchange.common.components.core/init
  :after-refresh 'cmr.exchange.common.dev/init-and-startup
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

