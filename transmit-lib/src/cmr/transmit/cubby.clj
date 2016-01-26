(ns cmr.transmit.cubby
  "Provide functions for accessing the cubby app"
  (:require [cmr.transmit.connection :as conn]
            [ring.util.codec :as codec]
            [cmr.transmit.http-helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- reset-url
  [conn]
  (format "%s/reset" (conn/root-url conn)))

(defn- keys-url
  [conn]
  (format "%s/keys" (conn/root-url conn)))

(defn- key-url
  [key-name conn]
  (format "%s/keys/%s" (conn/root-url conn) (codec/url-encode key-name)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn get-keys
  "Gets the stored keys of cached values as a raw response."
  ([context]
   (get-keys context false))
  ([context raw]
   (h/request context :cubby {:url-fn keys-url :method :get :raw? raw})))

(defn get-value
  "Gets the value associated with the given key."
  ([context key-name]
   (get-value context key-name false))
  ([context key-name raw]
   (h/request context :cubby {:url-fn (partial key-url key-name), :method :get, :raw? raw})))

(defn set-value
  "Associates a value with the given key."
  ([context key-name value]
   (set-value context key-name value false))
  ([context key-name value raw]
   (h/request context :cubby {:url-fn (partial key-url key-name)
                              :method :put
                              :raw? raw
                              :http-options {:body value}})))

(defn delete-value
  "Dissociates the value with the given key."
  ([context key-name]
   (delete-value context key-name false))
  ([context key-name raw]
   (h/request context :cubby {:url-fn (partial key-url key-name), :method :delete, :raw? raw})))

(defn delete-all-values
  "Deletes all values"
  ([context]
   (delete-all-values context false))
  ([context raw]
   (h/request context :cubby {:url-fn keys-url, :method :delete, :raw? raw})))

(defn reset
  "Clears all values in the cache service"
  ([context]
   (reset context false))
  ([context raw]
   (h/request context :cubby {:url-fn reset-url, :method :post, :raw? raw :use-system-token? true})))

(h/defhealther get-cubby-health :cubby 2)
