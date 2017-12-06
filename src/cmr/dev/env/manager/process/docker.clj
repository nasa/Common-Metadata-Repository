(ns cmr.dev.env.manager.process.docker
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [cmr.dev.env.manager.process.core :as process]
    [taoensso.timbre :as log]
    [trifl.fs :as fs]))

(defn- multi-flags
  [flag values]
  (mapcat #(conj [flag] %) values))

(defn- docker
  [args]
  (log/debug "Running the `docker` cli with the args:" (vec args))
  (apply process/exec (concat ["docker"] args)))

(defn pull
  [opts]
  (docker ["pull" (:image-id opts)]))

(defn stop
  [opts]
  (docker ["stop" (string/trim (slurp (:container-id-file opts)))])
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
