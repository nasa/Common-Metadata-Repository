(ns cmr.http.kit.app.middleware
  "Custom site and API middleware for new CMR services."
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.util :as util]
   [cmr.http.kit.components.config :as config]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.http.kit.site.pages :as pages]
   [ring.middleware.content-type :as ring-ct]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.file :as ring-file]
   [ring.middleware.not-modified :as ring-nm]
   [ring.util.response :as ring-response]
   [taoensso.timbre :as log]))

(defn wrap-request-id
  [handler]
  (fn [req]
    (let [id (util/get-uuid)]
      (log/debugf "Adding request-id '%s' to request ..." id)
      (-> req
          (request/add-request-id id)
          handler
          (response/add-request-id id)))))

(defn wrap-log-request
  [handler]
  (fn [req]
    (log/debug "Got request:" req)
    (handler req)))

(defn wrap-log-response
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (log/debug "Sending response:" (assoc resp :body "..."))
      resp)))

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
      (handler (assoc req :uri (if (and (not= "/" uri)
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
    (let [response (handler req)
          base-url-fn (util/resolve-fully-qualified-fn
                       (config/base-url-fn system))]
      (if (contains? (config/http-replace-base-url system)
                     (:uri req))
        (assoc response
               :body
               (string/replace
                (slurp (:body response))
                (re-pattern (config/http-rest-docs-base-url-template system))
                (base-url-fn system)))
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
          status (:status response)]
      (cond (string/includes? (:uri req) "stream")
            (do
              (log/debug "Got streaming response; skipping 404 checks ...")
              response)

            (or (= 404 status) (nil? status))
            (do
              (when (nil? status)
                (log/debug "Got nil status in not-found middleware ..."))
              (assoc (pages/not-found system req) :status 404))

            :else
            response))))

(defn wrap-ring-middleware
  [handler system]
  (-> handler
      wrap-log-request
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)
      (wrap-resource system)
      wrap-trailing-slash
      wrap-cors
      (wrap-not-found system)
      wrap-log-response))
