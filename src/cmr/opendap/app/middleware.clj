(ns cmr.opendap.app.middleware
  "Custom ring middleware for CMR OPeNDAP."
  (:require
   [clojure.string :as string]
   [cmr.opendap.auth.core :as auth]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.site.pages :as pages]
   [cmr.opendap.app.routes.rest :as rest-routes]
   [reitit.ring :as ring]
   [ring.middleware.content-type :as ring-ct]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.file :as ring-file]
   [ring.middleware.not-modified :as ring-nm]
   [ring.util.response :as ring-response]
   [taoensso.timbre :as log]))

(defn wrap-cors
  "Ring-based middleware for supporting CORS requests."
  [handler]
  (fn [req]
    (response/cors req (handler req))))

(defn wrap-trailing-slash
  "Ring-based middleware forremoving a single trailing slash from the end of the
  URI, if present."
  [handler]
  (fn [req]
    (let [uri (:uri req)]
      (handler (assoc req :uri (if (and (not (= "/" uri))
                                        (.endsWith uri "/"))
                                 (subs uri 0 (dec (count uri)))
                                 uri))))))

(defn wrap-fallback-content-type
  [handler default-content-type]
  (fn [req]
    (condp = (:content-type req)
      nil (assoc-in (handler req)
                    [:headers "Content-Type"]
                    default-content-type)
      "application/octet-stream" (assoc-in (handler req)
                                           [:headers "Content-Type"]
                                           default-content-type)
      :else (handler req))))

(defn wrap-directory-resource
  ([handler system]
    (wrap-directory-resource handler system "text/html"))
  ([handler system content-type]
    (fn [req]
      (let [response (handler req)]
        (cond
          (contains? (config/http-index-dirs system)
                     (:uri req))
          (ring-response/content-type response content-type)

          :else
          response)))))

(defn wrap-base-url-subs
  [handler system]
  (fn [req]
    (let [response (handler req)]
      (if (contains? (config/http-replace-base-url system)
                     (:uri req))
        (assoc response
               :body
               (string/replace
                (slurp (:body response))
                (re-pattern (config/http-rest-docs-base-url-template system))
                (config/opendap-url system)))
        response))))

(defn wrap-resource
  [handler system]
  (let [docs-resource (config/http-docs system)
        assets-resource (config/http-assets system)
        compound-handler (-> handler
                             (ring-file/wrap-file
                              docs-resource {:allow-symlinks? true})
                             (ring-file/wrap-file
                              assets-resource {:allow-symlinks? true})
                             (wrap-directory-resource system)
                             (wrap-base-url-subs system)
                             (ring-ct/wrap-content-type)
                             (ring-nm/wrap-not-modified))]
    (fn [req]
      (if (contains? (config/http-skip-static system)
                     (:uri req))
        (handler req)
        (compound-handler req)))))

(defn wrap-not-found
  [handler system]
  (fn [req]
    (let [response (handler req)
          status (:status response)
          ct (get-in response [:headers "Content-Type"])]
      (cond (and ct (string/includes? ct "stream"))
            (do
              (log/debug "Got streaming response; skipping 404 checks ...")
              response)

            (or (= 404 status) (nil? status))
            (do
              (when (nil? status)
                (log/debug "Got nil status in not-found middleware ..."))
              (assoc (pages/not-found
                      req
                      {:base-url (config/opendap-url system)})
                     :status 404))

            :else
            response))))

(defn wrap-auth
  "Ring-based middleware for supporting the protection of routes using the CMR
  Access Control service and CMR Legacy ECHO support.

  In particular, this wrapper allows for the protection of routes by both roles
  as well as concept-specific permissions. This is done by annotating the routes
  per the means described in the reitit library's documentation."
  [handler system]
  (fn [req]
    (log/debug "Running perms middleware ...")
    (auth/check-route-access system handler req)))

(defn reitit-auth
  [system]
  "This auth middleware is specific to reitit, providing the data structure
  necessary that will allow for the extraction of roles and permissions
  settings from the request.

  For more details, see the docstring above for `wrap-auth`."
  {:data
    {:middleware [#(wrap-auth % system)]}})

(defn wrap-api-version-dispatch
  ""
  [site-routes system opts]
  (fn [req]
    (log/trace "Got site-routes:" (into [] site-routes))
    (let [spi-version (request/accept-api-version system req)
          routes (concat site-routes (rest-routes/all system))
          handler (ring/ring-handler (ring/router routes opts))
          header (format "%s; format=%s"
                         (request/accept-media-type system req)
                         (request/accept-format system req))]
      (log/debug "API version:" spi-version)
      (log/trace "Made routes:" (into [] routes))
      (response/version-media-type (handler req) header))))
