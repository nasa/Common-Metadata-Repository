(ns cmr.client.common.http)

(defn query+options
  [query-params http-options]
  (merge {:query-params query-params}
         http-options))
