(ns cmr.index-set.services.messages
  "Provides a map between crud ops and error message formats"
  (:require [clojure.string :as string]
            [cheshire.core :as cheshire]
            [cmr.common.services.errors :as errors]))

(def err-msg-fmts
  {:get {:index-set-not-found "index-set with id: %s not found"}
   :create {:invalid-id "id: %s not a positive integer, index set: %s"
            :missing-id-name "missing id or name in index-set: %s"
            :missing-idx-cfg "missing index names or settings or mapping in given index-set: %s"
            :index-set-exists "index-set id: %s already exists"
            :missing-idx "index-sets index does not exist in elastic"
            :invalid-index-set "invalid index-set: %s"
            :fail "failed to create index-set: %s; root cause: %s"}
   :delete {:index-fail "index delete operation failed - elastic response: %s"
            :doc-fail "index-set doc delete operation failed - elastic reponse; %s"}})

(defn index-set-not-found-msg
  "message generated in get context"
  [id]
  (format "index-set with id: %s not found." id))

(defn invalid-id-msg
  "message generated in 'create' context"
  [id index-set]
  (format "id: %s not a positive integer, index set: %s" id index-set))

(defn missing-id-name-msg
  "message generated in 'create' context"
  [index-set]
  (format "missing id or name in index-set: %s" index-set))

(defn missing-idx-cfg-msg
  "message generated in 'create' context"
  [index-set]
  (format "missing index names or settings or mapping in given index-set: %s" index-set))

(defn index-set-exists-msg
  "message generated in 'create' context"
  [id]
  (format "index-set with id: %s already exists" id))

(defn missing-index-for-index-sets-msg
  "message generated in 'create' context"
  [index-name]
  (format "%s index does not exist in elastic." index-name))

(defn invalid-index-set-msg
  "message generated in 'create' context"
  [index-set]
  (format "invalid index-set: %s" index-set))

(defn handle-elastic-exception
  "Expects context message and the cause. Context message to indicate create problems with index or doc."
  [context-msg e]
  (let [status (get-in (ex-data e) [:status])
        body (cheshire/decode (get-in (ex-data e) [:body]) true)
        error (format "context: %s, error: %s" context-msg (:error body))]
    (condp = status
      400 (errors/throw-service-error :bad-request error e)
      404 (errors/throw-service-error :not-found error e)
      409 (errors/throw-service-error :conflict error e)
      422 (errors/throw-service-error :invalid-data error e)
      (errors/internal-error! error e))))

(defn index-delete-failure-msg
  [es-response]
  (format "index delete operation failed - elastic response: %s" es-response))

(defn index-set-doc-delete-msg
  [es-response]
  (format "index-set doc delete operation failed - elastic reponse; %s" es-response))
