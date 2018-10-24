(ns user
  "A dev namespace that supports Proto-REPL.

  It seems that Proto-REPL doesn't support the flexible approach that lein
  uses: any configurable ns can be the starting ns for a REPL. As such, this
  minimal ns was created for Proto-REPL users, so they too can have an env
  that supports startup and shutdown."
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.system-manager.core :refer :all]
   [cmr.versioning.repl :as dev]
   [org.httpkit.client :as httpc]))
