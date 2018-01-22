(ns cmr.client.dev
  "The CMR client Clojure REPL development namespace."
  (:require
   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [cmr.client.ac :as ac]
   [cmr.client.common.const :as const]
   [cmr.client.common.util :as util]
   [cmr.client.http.core :as http]
   [cmr.client.ingest :as ingest]
   [cmr.client.search :as search]
   [cmr.client.testing.runner :as runner]
   [cmr.client.tests]
   [ltest.core :as ltest]))

(repl/set-refresh-dirs
   "src/clj"
   "src/cljc"
   "dev-resources/src")

;;; Aliases

(def reload
   "An alias for `repl/refresh`"
   #'repl/refresh)

(def refresh
   "An alias for `repl/refresh`"
   #'repl/refresh)

(def reset
   "An alias for `repl/refresh`"
   #'repl/refresh)
