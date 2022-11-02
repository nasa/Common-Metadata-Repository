(ns cmr.client.http.util
  "HTTP client utility functions.")

(defn query+options
  "Given separate HTTP query parameters and HTTP client options, combine them
  for use by the HTTP client.

  The ClojureScript version of this function converts JavaScript data to
  Clojure data."
  [query-params http-options]
  (merge {:query-params #?(:clj query-params
                           :cljs (js->clj query-params))}
         #?(:clj http-options
            :cljs (js->clj http-options))))

(defn merge-headers
  [options headers]
  (assoc options :headers (merge (:headers options)
                                  headers)))

(def merge-header #'merge-headers)
