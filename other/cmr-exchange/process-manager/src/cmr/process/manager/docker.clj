(ns cmr.process.manager.docker
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [cmr.process.manager.core :as process]
    [taoensso.timbre :as log]
    [trifl.fs :as fs]
    [trifl.ps :as ps]))

(defn- multi-flags
  [flag values]
  (mapcat #(conj [flag] %) values))

(defn- docker
  [args]
  (log/trace "Running the `docker` cli with the args:" (vec args))
  (:out (apply process/exec (concat ["docker"] args))))

(defn- container-id-file?
  [opts]
  (fs/exists? (io/file (:container-id-file opts))))

(defn read-container-id
  [opts]
  (when (container-id-file? opts)
    (let [container-id-file (:container-id-file opts)
          id (string/trim (slurp container-id-file))]
      (when-not (empty? id)
        id))))

(defn pull
  [opts]
  (docker ["pull" (:image-id opts)]))

(defn stop
  [opts]
  (let [container-id-file (:container-id-file opts)
        container-id (read-container-id opts)]
    (when container-id
      (docker ["stop" container-id]))
    (when (container-id-file? opts)
      (io/delete-file container-id-file))))

(defn run
  [opts]
  (when (fs/exists? (io/file (:container-id-file opts)))
    (stop opts))
  (docker (concat
           ["run" "-d"
            (str "--cidfile=" (:container-id-file opts))]
           (when-let [ports (:ports opts)]
            (multi-flags "-p" ports))
           (when-let [envs (:env opts)]
            (multi-flags "-e" envs))
           (when-let [vols (:volumes opts)]
            (multi-flags "-v" vols))
           [(:image-id opts)])))

(defn inspect
  [opts]
  (first
    (json/parse-string
      (docker ["inspect" (read-container-id opts)])
      true)))

(defn state
  [opts]
  (:State (inspect opts)))

(defn pid
  [opts]
  (get-in (inspect opts) [:State :Pid]))

(defn get-cpu-mem
  [opts]
  (ps/get-ps-info "%cpu,%mem" (pid opts)))

(defn get-cpu
  [opts]
  (:%cpu (get-cpu-mem opts)))

(defn get-mem
  [opts]
  (:%mem (get-cpu-mem opts)))
