(ns cmr.common.api.web-server
  "Defines a web server component."
  (:require [cmr.common.lifecycle :as lifecycle]
            [ring.adapter.jetty :as jetty]
            [cmr.common.log :refer (debug info warn error)])
  (:import org.eclipse.jetty.server.Server
           org.eclipse.jetty.server.NCSARequestLog
           org.eclipse.jetty.server.handler.GzipHandler
           org.eclipse.jetty.server.handler.RequestLogHandler))

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

(defrecord WebServer
  [
   ;; The port Jetty will be running on
   port

   ;; Whether gzip compressed responses are enabled or not
   use-compression?

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



            ; ;; Replace the existing handler with the gzip handler
            ; (doto server
            ;   (.stop)
            ;   (.setHandler new-handler)
            ;   (.start))))

        (let [access-log-handler (doto (RequestLogHandler.)
                                   (.setHandler (.getHandler server))
                                   (.setRequestLog
                                     (doto (NCSARequestLog.)
                                       (.setLogLatency true))))]



          (when use-compression?
          ;; Create a GZIP handler to handle compression of responses
            (let [gzip-handler (doto (GzipHandler.)
                              (.setHandler access-log-handler)
                              (.setMinGzipSize MIN_GZIP_SIZE))]
              (doto server
                (.stop)
                (.setHandler gzip-handler)
                (.start)))
            (doto server
              (.stop)
              (.setHandler access-log-handler)
              (.start)))))

        (info "Jetty started on port" port)
        (assoc this :server server)
      (catch Exception e
        (info "Failed to start jetty on port" port)
        (throw e))))

  (stop
    [this system]
    (if-let [^Server server (:server this)]
      (.stop server))
    (assoc this :server nil)))


(defn create-web-server
  "Creates a new web server. Accepts argument of port and a routes function that should accept
  system argument and return compojure routes to use."
  ([port routes-fn]
   (create-web-server port routes-fn true))
  ([port routes-fn use-compression]
   (map->WebServer {:port port
                    :use-compression? use-compression
                    :routes-fn routes-fn})))
