(ns cmr.authz.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def config-file "config/cmr-authz/config.edn")

(defn cfg-data
  ([]
    (cfg-data config-file))
  ([filename]
    (with-open [rdr (io/reader (io/resource filename))]
      (edn/read (new java.io.PushbackReader rdr)))))

(defn service-keys
  "We need to special-case two-word services, as split by the environment
  setup done in other cmr projects."
  [service]
  (cond (or (= service :access)
            (= service :access-control))
        [:access :control]

        (or (= service :echo)
            (= service :echo-rest))
        [:echo :rest]))

(defn service->base-url
  [service]
  (format "%s://%s:%s"
          (or (:protocol service) "https")
          (:host service)
          (or (:port service) "443")))

(defn service->url
  [service]
  (format "%s%s"
          (service->base-url service)
          (or (get-in service [:relative :root :url])
              (:context service)
              "/")))
