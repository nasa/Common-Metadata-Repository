(ns cmr.sample-data.dev
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [cmr.sample-data.const :as const]
   [cmr.sample-data.core :as data]
   [cmr.sample-data.util :as util]
   [trifl.java :refer [show-methods]]))

;;; Aliases

(def reload #'repl/refresh)
(def reset #'repl/refresh)
