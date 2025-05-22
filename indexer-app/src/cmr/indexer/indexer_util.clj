(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.config :as common-config]
   [cmr.common.log :as log :refer [info]]
   [cmr.common.rebalancing-collections :as rebalancing-collections]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.indexer.config :as config]
   [cmr.indexer.data.index-set-elasticsearch :as es]
   [cmr.indexer.services.messages :as m]))

(defn context->es-store
  [context]
  (get-in context [:system :db]))

(defn context->conn
  "Returns the elastisch connection in the context"
  [context]
  (get-in context [:system :db :conn]))