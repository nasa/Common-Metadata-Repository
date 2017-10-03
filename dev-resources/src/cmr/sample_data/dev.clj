(ns cmr.sample-data.dev
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [cmr.sample-data.core :as data]
   [cmr.sample-data.util :as util]))

;;; Aliases

(def reload #'repl/refresh)
(def reset #'repl/refresh)
