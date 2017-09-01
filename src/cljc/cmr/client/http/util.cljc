(ns cmr.client.http.util)

(defn query+options
  [query-params http-options]
  (merge {:query-params #?(:clj query-params
                           :cljs (js->clj query-params))}
         #?(:clj http-options
            :cljs (js->clj http-options))))
