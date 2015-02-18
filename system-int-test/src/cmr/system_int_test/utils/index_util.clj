(ns ^{:doc "provides index related utilities."}
  cmr.system-int-test.utils.index-util
  (:require [clj-http.client :as client]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.indexer.config :as config]
            [cmr.system-int-test.utils.test-environment :as te]
            [cmr.system-int-test.utils.queue :as queue]
            [cmr.common.log :as log :refer (debug info warn error)]))

(defn refresh-elastic-index
  []
  (client/post (url/elastic-refresh-url) {:connection-manager (url/conn-mgr)}))

(defn wait-until-indexed
  "Wait until ingested concepts have been indexed"
  []
  (when (config/use-index-queue?)
    (client/post (url/wait-for-indexing-url) {:connection-manager (url/conn-mgr)}))
  (refresh-elastic-index))


