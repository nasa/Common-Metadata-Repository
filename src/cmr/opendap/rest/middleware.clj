(ns cmr.opendap.rest.middleware
  "Custom ring middleware for CMR OPeNDAP."
  (:require
   [clojure.string :as string]
   [cmr.opendap.auth.core :as auth]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.site.pages :as pages]
   [ring.middleware.content-type :as ring-ct]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.file :as ring-file]
   [ring.middleware.not-modified :as ring-nm]
   [ring.util.request :as request]
   [ring.util.response :as rign-response]
   [taoensso.timbre :as log]))

(defn wrap-cors
  "Ring-based middleware for supporting CORS requests."
  [handler]
  (fn [request]
    (response/cors request (handler request))))

(defn wrap-trailing-slash
  "Ring-based middleware forremoving a single trailing slash from the end of the
  URI, if present."
  [handler]
  (fn [request]
    (let [uri (:uri request)]
      (handler (assoc request :uri (if (and (not (= "/" uri))
                                            (.endsWith uri "/"))
                                     (subs uri 0 (dec (count uri)))
                                     uri))))))

(defn wrap-fallback-content-type
  [handler default-content-type]
  (fn [request]
    (condp = (:content-type request)
      nil (assoc-in (handler request)
                    [:headers "Content-Type"]
                    default-content-type)
      "application/octet-stream" (assoc-in (handler request)
                                           [:headers "Content-Type"]
                                           default-content-type)
      :else (handler request))))

(defn wrap-directory-resource
  ([handler system]
    (wrap-directory-resource handler system "text/html"))
  ([handler system content-type]
    (fn [request]
      (let [response (handler request)]
        (cond
          (contains? (config/http-index-dirs system)
                     (:uri request))
          (rign-response/content-type response content-type)

          :else
          response)))))

(defn wrap-base-url-subs
  [handler system]
  (fn [request]
    (let [response (handler request)]
      (if (contains? (config/http-replace-base-url system)
                     (:uri request))
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
    (fn [request]
      (if (contains? (config/http-skip-static system)
                     (:uri request))
        (handler request)
        (compound-handler request)))))

(defn wrap-not-found
  [handler system]
  (fn [request]
    (let [response (handler request)
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
                      request
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
  (fn [request]
    (log/debug "Running perms middleware ...")
    (auth/check-route-access system handler request)))

(defn reitit-auth
  [system]
  "This auth middleware is specific to reitit, providing the data structure
  necessary that will allow for the extraction of roles and permissions
  settings from the request.

  For more details, see the docstring above for `wrap-auth`."
  {:data
    {:middleware [#(wrap-auth % system)]}})


(defn manager
  ""
  [handler system]
  (fn [request]
    ))
