(ns ^{:doc "provides index related utilities."}
  cmr.system-int-test.utils.index-util
  (:require [clj-http.client :as client]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn refresh-elastic-index
  []
  (client/post (url/elastic-refresh-url) {:connection-manager (url/conn-mgr)}))



