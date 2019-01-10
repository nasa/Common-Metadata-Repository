(ns cmr.dev.env.manager.repl.transitions
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.twig :as logger]
   [cmr.dev.env.manager.components.system :as components]
   [cmr.dev.env.manager.config :as config]
   [cmr.dev.env.manager.process.core :as process]
   [com.stuartsierra.component :as component]
   [me.raynes.conch.low-level :as shell]
   [taoensso.timbre :as log]
   [trifl.java :refer [show-methods]]))

(def valid-stop #{:started :running})

(def invalid-init #{:initialized :started :running})
(def invalid-deinit #{:started :running})
(def invalid-start #{:started :running})
(def invalid-stop #{:stopped :shutdown})
(def invalid-run #{:running})
(def invalid-shutdown #{:uninitialized :shutdown})
