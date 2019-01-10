(ns cmr-edsc-stubs.dev
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [cmr-edsc-stubs.core :as stubs]
   [cmr-edsc-stubs.data.cmrapis :as cmrapis]
   [cmr-edsc-stubs.data.fake-response :as fake]
   [cmr-edsc-stubs.data.jdbc :as jdbc]
   [cmr-edsc-stubs.data.metadatadb :as metadatadb]
   [cmr-edsc-stubs.data.service :as service]
   [cmr-edsc-stubs.util :as util]
   [cmr.client.ingest :as ingest]
   [cmr.client.search :as search]
   [cmr.sample-data.const :as const]
   [cmr.sample-data.core :as data-sources]
   [cmr.sample-data.util :as sutil]
   [trifl.java :refer [show-methods]]))

;;; Utility functions

(def p (comp pprint json/parse-string))

;;; Aliases

(def reload #'repl/refresh)
(def reset #'repl/refresh)
