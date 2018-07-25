(ns cmr.opendap.http.request
  (:require
   [cmr.http.kit.request :as request]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.const :as const]
   [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Backwards-compatible Aliases   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-header request/get-header)
(def add-header request/add-header)
(def add-accept request/add-accept)
(def add-token-header request/add-token-header)
(def add-content-type request/add-content-type)
(def add-form-ct request/add-form-ct)
(def add-payload request/add-payload)
(def options request/options)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Header Support   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def version-format "application/vnd.%s%s+%s")

(defn add-user-agent
  ([]
    (add-user-agent {}))
  ([req]
    (request/add-header req "User-Agent" const/user-agent)))

(defn add-client-id
  ([]
    (add-client-id {}))
  ([req]
    (request/add-header req "Client-Id" const/client-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   HTTP Client Support   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-options
  (-> {:user-agent const/user-agent
       :insecure? true}
      (add-user-agent)
      (add-client-id)))

(defn request
  [method url req & [callback]]
  (request/request method url req default-options callback))

(defn async-get
  ([url]
    (async-get url {}))
  ([url req]
    (async-get url req nil))
  ([url req callback]
    (request :get url req callback)))

(defn async-post
  ([url]
    (async-post url {}))
  ([url req]
    (async-post url req nil))
  ([url req callback]
    (request :post url req callback)))

(defn get
  [& args]
  @(apply async-get args))

(defn post
  [& args]
  @(apply async-post args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Accept Header/Version Support   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn default-accept
  [system]
  (format version-format
          (config/vendor system)
          (config/api-version-dotted system)
          (config/default-content-type system)))

(defn parse-accept
  [system req]
  (->> (or (get-in req [:headers :accept])
           (get-in req [:headers "accept"])
           (get-in req [:headers "Accept"])
           (default-accept system))
       (re-find request/accept-pattern)
       (zipmap request/accept-pattern-keys)))

(defn accept-api-version
  [system req]
  (let [parsed (parse-accept system req)
        version (or (:version parsed) (config/api-version system))]
    version))

(defn accept-media-type
  [system req]
  (let [parsed (parse-accept system req)
        vendor (or (:vendor parsed) (config/vendor system))
        version (or (:.version parsed) (config/api-version-dotted system))]
    (str vendor version)))

(defn accept-format
  [system req]
  (let [parsed (parse-accept system req)]
    (or (:content-type parsed)
        (:no-vendor-content-type parsed)
        (config/default-content-type system))))
