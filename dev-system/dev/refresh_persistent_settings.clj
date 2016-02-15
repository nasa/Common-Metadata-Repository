(ns refresh-persistent-settings
  "A namespace that can contain development time settings that will not be lost during a refresh."
  (:require [clojure.tools.namespace.repl :as r]))

;; Disable reloading of this namespace.
(r/disable-reload!)
(r/disable-unload!)

;; TODO test this with refresh and super refresh

(def logging-level
  "The logging level to use locally"
  (atom :error))

