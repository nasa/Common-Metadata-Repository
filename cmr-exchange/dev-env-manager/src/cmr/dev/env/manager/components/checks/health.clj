(ns cmr.dev.env.manager.components.checks.health
  (:require
    [cmr.process.manager.components.docker :as docker]
    [cmr.process.manager.components.process :as process])
  (:import
    (cmr.process.manager.components.docker DockerRunner)
    (cmr.process.manager.components.process ProcessRunner)))

(defprotocol Healthful
  (get-summary [this]
    "Provides high-level view on health of a component.")
  (get-status [this]
    "Performs a health check on a given component."))

(extend DockerRunner
        Healthful
        docker/healthful-behaviour)

(extend ProcessRunner
        Healthful
        process/healthful-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Health-check Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; For processes ...

; (defn get-status
;   [this]
;   ;; XXX process, http, and ping TBD
;   (let [process-response nil
;         http-response nil
;         ping-response nil
;         cpu (process/get-cpu (:process-data this))
;         mem (process/get-mem (:process-data this))]
;     {:process {:status :ok}
;      :http {:status :ok}
;      :ping {:status :ok}
;      :cpu {
;        :status (if (>= cpu 50) :high :ok)
;        :details {:value cpu :type :percent}}
;      :mem {
;        :status (if (>= mem 20) :high :ok)
;        :details {:value mem :type :percent}}}))

;; XXX move this into ...
;;     * Option 1: cmr.dev.env.manager.components.common
;;     * Option 2: cmr.dev.env.manager.components.checks.common
;;     see ticket https://github.com/cmr-exchange/dev-env-manager/issues/36

; (defn get-summary
;   [this]
;   (->> this
;        (get-status)
;        (map (fn [[k v]] [k (:status v)]))
;        (into {})))

; (def healthful-behaviour
;   {:get-status get-status
;    :get-summary get-summary})

;; For dockers ...

; (defn get-status
;   [this]
;   ;; XXX http and ping TBD
;   (let [state-response (docker/state (:opts this))
;         http-response nil
;         ping-response nil
;         cpu (docker/get-cpu (:opts this))
;         mem (docker/get-mem (:opts this))]
;     {:docker {
;        :status (keyword (:Status state-response))
;        :details state-response}
;      :http {:status :ok}
;      :ping {:status :ok}
;      :cpu {
;        :status (if (>= cpu 50) :high :ok)
;        :details {:value cpu :type :percent}}
;      :mem {
;        :status (if (>= mem 20) :high :ok)
;        :details {:value mem :type :percent}}}))

;; XXX move this into ...
;;     * Option 1: cmr.dev.env.manager.components.common
;;     * Option 2: cmr.dev.env.manager.components.checks.common
;;     see ticket https://github.com/cmr-exchange/dev-env-manager/issues/36

; (defn get-summary
;   [this]
;   (->> this
;        (get-status)
;        (map (fn [[k v]] [k (:status v)]))
;        (into {})))

; (def healthful-behaviour
;   {:get-status get-status
;    :get-summary get-summary})
