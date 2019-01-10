(ns cmr.http.kit.app.handler
  "This namespace defines the handlers for general resources.

  Simple handlers will only need to make a call to a library and then have that
  data prepared for the client by standard response function. More complex
  handlers will need to perform additional tasks. For example, in order of
  increasing complexity:
  * utilize non-default, non-trivial response functions
  * operate on the obtained data with various transformations, including
    extracting form data, query strings, etc.
  * take advantage of middleware functions that encapsulate complicated
    business logic"
  (:require
   [cmr.http.kit.response :as response]
   [ring.util.http-response]
   [ring.util.response :as ring-response]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn status
  [status-keyword]
  (fn [request]
    ((ns-resolve 'ring.util.http-response (symbol (name status-keyword))) {})))

(def ok
  (fn [request]
    (response/ok request)))

(defn dynamic-page
  [system page-fn data]
  #(page-fn system % data))

(defn permanent-redirect
  [location]
  (fn [request]
    (ring-response/redirect location :moved-permanently)))
