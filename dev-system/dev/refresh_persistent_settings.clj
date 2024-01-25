(ns refresh-persistent-settings
  "A namespace that can contain development time settings that will not be lost during a refresh."
  (:require [clojure.tools.namespace.repl :as r]
            [clojure.java.io :as io]))

;; Disable reloading of this namespace.
(r/disable-reload!)
(r/disable-unload!)

(defn local-overrides
  "To prevent the need to ignore this file in git status, reading settings from
   an optional file which is not expected to be in git. Currently this file is
   defined as: dev-system/local.edn.
   and the content could contain two keys like this:

   {:logging-level :warn
    :run-modes {:db :external}}

   The first is the log level to use, the second is simply merged in with the
   default.
   This file is only read at initial launch of CMR, (user/reset) will not pickup
   changes.
   "
  [which default]
  (let [home (System/getProperty "user.dir")
        file-path (format "%s/local.edn" home)
        exists (.exists (io/file file-path))
        local-settings (if exists (read-string (slurp file-path)) {})
        settings (get local-settings which default)]
    ;; Aggresivly display that settings are being set from this process
    (println (format "🚀 - Using settings %s = %s." which settings))
    settings))

(def logging-level
  "The logging level to use locally"
  (atom (local-overrides :logging-level :error)))

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
  (atom (merge {:elastic default-run-mode
         :echo default-run-mode
         :db default-run-mode
         :messaging default-run-mode
         :redis default-run-mode}
         (local-overrides :run-modes {}))))
