(ns cmr.common.api.web-server
  "Defines a web server component."
  (:require [cmr.common.lifecycle :as lifecycle]
            [ring.adapter.jetty :as jetty]
            [cmr.common.log :refer (debug info warn error)])
  (:import [org.eclipse.jetty.server
            Server
            NCSARequestLog]
           [org.eclipse.jetty.server.handler
            GzipHandler
            RequestLogHandler]))

(def MIN_THREADS
  "The minimum number of threads for Jetty to use to process requests. The was originally set to the
  ring jetty adapter default of 8."
  8)

(def MAX_THREADS
  "The maximum number of threads for Jetty to use to process requests. This was originally set to
  the ring jetty adapter default of 50.

  VERY IMPORTANT NOTE: The value set here must correspond to the number of persistent HTTP
  connections we use in the transmit library. Do not change this or refactor this code without
  making sure that the transmit library uses the same amount."
  50)

(def MIN_GZIP_SIZE
  "The size that will be used to determine if responses should be GZIP'd. See the following Stack
  Overflow question:
  http://webmasters.stackexchange.com/questions/31750/what-is-recommended-minimum-object-size-for-gzip-performance-benefits
  Akamai recommend 860 bytes. We're transmitting UTF-8 which should be about a byte a character."
  860)

(defn create-access-log-handler
  "Setup access logging for each application. Access log entries will go to stdout similar to
  application logging. As a result the access log entries will be in the same log as the
  application log."
  [existing-handler]
  (doto (RequestLogHandler.)
    (.setHandler existing-handler)
    (.setRequestLog
      (doto (NCSARequestLog.)
        (.setLogLatency true)))))

(defn create-gzip-handler
  "Setup gzip compression for responses.  Compression will be used for any response larger than
  the configured minimum size."
  [existing-handler min_gzip_size]
  (doto (GzipHandler.)
    (.setHandler existing-handler)
    (.setMinGzipSize min_gzip_size)))


(defrecord WebServer
  [
   ;; The port Jetty will be running on
   port

   ;; Whether gzip compressed responses are enabled or not
   use-compression?

   ;; Whether access log is enabled or not.
   use-access-log?

   ;; A function that will return the routes. Should accept a single argument of the system.
   routes-fn

   ;;The actual Jetty instance
   server
   ]

  lifecycle/Lifecycle

  (start
    [this system]
    (try
      (let [{:keys [port routes-fn use-compression?]} this
            routes (routes-fn system)
            ^Server server (jetty/run-jetty routes {:port port
                                                    :join? false
                                                    :min-threads MIN_THREADS
                                                    :max-threads MAX_THREADS})]

        (let [request-handler (if use-compression?
                                (create-gzip-handler (.getHandler server) MIN_GZIP_SIZE)
                                (.getHandler server))
              request-handler (if use-access-log?
                                (create-access-log-handler request-handler)
                                request-handler)]
          (doto server
            (.stop)
            (.setHandler request-handler)
            (.start)))


        (info "Jetty started on port" port)
        (assoc this :server server))
      (catch Exception e
        (info "Failed to start jetty on port" port)
        (throw e))))

  (stop
    [this system]
    (when-let [^Server server (:server this)]
      (.stop server))
    (assoc this :server nil)))


(defn create-web-server
  "Creates a new web server. Accepts argument of port and a routes function that should accept
  system argument and return compojure routes to use."
  ([port routes-fn]
   (create-web-server port routes-fn true true))
  ([port routes-fn use-compression use-access-log]
   (map->WebServer {:port port
                    :use-compression? use-compression
                    :use-access-log? use-access-log
                    :routes-fn routes-fn})))
