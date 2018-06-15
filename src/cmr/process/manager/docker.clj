(ns cmr.process.manager.docker
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [cmr.process.manager.core :as process]
    [taoensso.timbre :as log]
    [trifl.fs :as fs]))

(defn- multi-flags
  [flag values]
  (mapcat #(conj [flag] %) values))

(defn- docker
  [args]
  (log/trace "Running the `docker` cli with the args:" (vec args))
  (:out (apply process/exec (concat ["docker"] args))))

(defn read-container-id
  [opts]
  (string/trim (slurp (:container-id-file opts))))

(defn pull
  [opts]
  (docker ["pull" (:image-id opts)]))

(defn stop
  [opts]
  (docker ["stop" (read-container-id opts)])
  (io/delete-file (:container-id-file opts)))

(defn run
  [opts]
  (when (fs/exists? (io/file (:container-id-file opts)))
    (stop opts))
  (docker (concat
           ["run" "-d"
            (str "--cidfile=" (:container-id-file opts))]
           (multi-flags "-p" (:ports opts))
           (multi-flags "-e" (:env opts))
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
