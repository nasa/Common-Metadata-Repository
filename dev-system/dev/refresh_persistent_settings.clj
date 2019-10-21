(ns refresh-persistent-settings
  "A namespace that can contain development time settings that will not be lost during a refresh."
  (:require [clojure.tools.namespace.repl :as r]))

;; Disable reloading of this namespace.
(r/disable-reload!)
(r/disable-unload!)

(def logging-level
  "The logging level to use locally"
  (atom :error))

(def legacy?
  "Whether to run the dev-system with legacy services enabled and configured"
  (atom false))

(def aws?
  "Whether to use AWS for messaging when running the dev-system"
  (atom false))

(defonce default-run-mode
  ;; Note that `defonce` doesn't yet have the new(ish) support of docstrings
  ;; that `def` does. See https://dev.clojure.org/jira/browse/CLJ-1148.
  ^{:doc "Allow the default run mode to be set via the ENV or JVM options. In
         practice, this is done in the following manner:

         $ lein with-profile +run-external repl

         or

         $ lein with-profile +run-in-memory repl"}
  (or (keyword (System/getProperty "cmr.runmode"))
      :in-memory))

(def run-modes
  "The data structure that maintains the run modes for each individual
  component"
  (atom {:elastic default-run-mode
         :echo default-run-mode
         :db default-run-mode
         :messaging default-run-mode
         :redis default-run-mode}))
