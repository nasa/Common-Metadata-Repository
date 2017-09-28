(ns cmr-edsc-stubs.dev
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [cmr-edsc-stubs.core :as stubs]
   [cmr-edsc-stubs.data.core :as data]
   [cmr-edsc-stubs.data.sources :as data-sources]
   [cmr-edsc-stubs.util :as util]))

;;; Aliases

(def reload #'repl/refresh)
(def reset #'repl/refresh)
