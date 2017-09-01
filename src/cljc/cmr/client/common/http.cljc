(ns cmr.client.common.http)

(defn query+options
  [query-params http-options]
  (merge {:query-params #?(:clj query-params
                           :cljs (js->clj query-params))}
         #?(:clj http-options
            :cljs (js->clj http-options))))
