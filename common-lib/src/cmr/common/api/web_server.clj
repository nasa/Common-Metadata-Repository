(ns cmr.common.api.web-server
  "Defines a web server component."
  (:require
   [clojure.java.io :as io]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [ring.adapter.jetty :as jetty]
   [clojure.core.reducers :as reducers])
  (:import
   (java.io ByteArrayInputStream InputStream)
   (org.eclipse.jetty.server Server NCSARequestLog Connector HttpConnectionFactory)
   (org.eclipse.jetty.server.handler RequestLogHandler)
   (org.eclipse.jetty.servlets.gzip GzipHandler)))

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

(def ONE_MB 1048576)

(def MAX_REQUEST_HEADER_SIZE
  "The maximum request header size. This is set to 1MB to handle requests with long urls."
  ONE_MB)

(def MAX_REQUEST_BODY_SIZE
 "The maximum request body size which the application will accept. This is set to prevent large,
  invalid requests coming in that cause out of memory exceptions. Requests to save the humanizer
  report in Cubby can be in the 5 to 10 MB range."
 (* 50 ONE_MB))

(def buffer-size
  "Number of bytes to allocate for the buffer used for verifying request body size is not too
  large. We want to use a smaller buffer size than the max request body size so for small requests
  we use a small amount of memory to perform the verification."
  512)

(defn- routes-fn-verify-size
  "Takes the passed in routes function and wraps it with another function that will verify request
   sizes do not exceed the maximum size.
   Before calling routes-fn function, check to make sure the request body size is not too large (greater
   than MAX_REQUEST_BODY_SIZE). If the body size is too large, throw an error, otherwise call the
   routes-fn function"
  [routes-fn]
  (fn [request]
    (let [^InputStream body-input-stream (:body request)]
      (loop [total-bytes-read 0
             bytes-read-for-this-buffer 0
             input-byte-arrays (transient [])
             current-buffer (byte-array buffer-size)
             counter 1]
        ;; Read bytes into the byte array up to length MAX_REQUEST_BODY_SIZE + 1. If more is read,
        ;; we know the request is too large and can return an error
        ; (println "Loop counter:" counter)
        (let [
              bytes-read (.read body-input-stream
                                current-buffer
                                bytes-read-for-this-buffer
                                (- buffer-size bytes-read-for-this-buffer))
              at-end? (= -1 bytes-read)
              buffer-full? (= buffer-size (+ bytes-read bytes-read-for-this-buffer))
              ; If bytes-read is -1, still want 0, so when adding it to total bytes it doesn't
              ;; subtract
              bytes-read (max 0 bytes-read)
              buffer-full? (= buffer-size (+ bytes-read bytes-read-for-this-buffer))
              ; _ (println "Bytes read: " bytes-read)
              ; _ (println "Buffer full: " buffer-full?)
              input-byte-arrays (if (or at-end? buffer-full?)
                                  (conj! input-byte-arrays current-buffer)
                                  input-byte-arrays)
              current-buffer (if (and buffer-full? (not at-end?))
                               (byte-array buffer-size)
                               current-buffer)
              bytes-read-for-this-buffer (if buffer-full?
                                           0
                                           (+ bytes-read-for-this-buffer bytes-read))]

          ;; If the entire request body has been read or if the amount of bytes read is
          ;; greater than MAX_REQUEST_BODY_SIZE, process based on num bytes read
          ;; otherwise loop to continue reading the request body
          (if (or at-end?
                  (> (+ total-bytes-read bytes-read) MAX_REQUEST_BODY_SIZE))
            (if (> (+ total-bytes-read bytes-read) MAX_REQUEST_BODY_SIZE)
              {:status 413
               :content-type :text
               :body "Request body exceeds maximum size"}

              (do
                ;; Put the request body into a new input stream since the current has been read
                (let [
                      ; single-byte-array (byte-array (for [single-array (persistent! input-byte-arrays)
                      ;                                     single-byte single-array]
                      ;                                 single-byte))
                      single-byte-array (->> input-byte-arrays
                                             persistent!
                                             (reducers/mapcat vec)
                                             reducers/foldcat
                                             (into [])
                                             byte-array)]
                                        ;  (byte-array (reducers/mapcat vec (persistent! input-byte-arrays))))]
                      ; single-byte-array (byte-array (my-flatten (persistent! input-byte-arrays)))]
                  ; (println "The byte array is:" (slurp (ByteArrayInputStream. single-byte-array 0 (+ total-bytes-read bytes-read))))
                  ; (println "The byte array no max length:" (slurp (ByteArrayInputStream. single-byte-array)))
                  ; (println "The byte array add buffer to length:" (slurp (ByteArrayInputStream. single-byte-array 0 (+ total-bytes-read bytes-read buffer-size))))
                  (routes-fn (assoc request :body (ByteArrayInputStream.
                                                   single-byte-array
                                                   0
                                                   (+ total-bytes-read bytes-read)))))))
                                                  ;  (byte-array (mapcat seq input-byte-arrays))
                                                  ;  0 ;; offset
                                                  ;  (+ total-bytes-read bytes-read buffer-size)))))))
            (recur (+ total-bytes-read bytes-read)
                   bytes-read-for-this-buffer
                   input-byte-arrays
                   current-buffer
                   (inc counter))))))))



(comment
 (def the-arrays [[1 2 3] [4 5 6]])
 (->> the-arrays
      (reducers/mapcat vec)
      reducers/foldcat
      (into [])))
      ; byte-array)

(defn create-access-log-handler
  "Setup access logging for each application. Access log entries will go to stdout similar to
  application logging. As a result the access log entries will be in the same log as the
  application log."
  [existing-handler]
  (doto (RequestLogHandler.)
    (.setHandler existing-handler)
    (.setRequestLog
      (doto (NCSARequestLog.)
        (.setLogLatency true)
        (.setLogDateFormat "yyyy-MM-dd HH:mm:ss.SSS")))))

(defn- create-gzip-handler
  "Setup gzip compression for responses.  Compression will be used for any response larger than
  the configured minimum size."
  [existing-handler min-gzip-size]
  (doto (GzipHandler.)
    (.setHandler existing-handler)
    ;; All the mime types that we want to support compression with must be specified here.
    (.setMimeTypes ^java.util.Set (set mt/all-supported-mime-types))
    (.setMinGzipSize min-gzip-size)))

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
   server]

  lifecycle/Lifecycle

  (start
    [this system]
    (try
      (let [{:keys [port routes-fn use-compression?]} this
            routes (routes-fn-verify-size (routes-fn system))
            ^Server server (jetty/run-jetty
                             routes
                             {:port port
                              :join? false
                              :min-threads MIN_THREADS
                              :max-threads MAX_THREADS
                              :configurator (fn [^Server jetty]
                                              (doseq [^Connector connector (.getConnectors jetty)]
                                                (let [^HttpConnectionFactory http-conn-factory
                                                      (first (.getConnectionFactories connector))]
                                                  (.setRequestHeaderSize
                                                    (.getHttpConfiguration http-conn-factory)
                                                    MAX_REQUEST_HEADER_SIZE))))})]


        (.stop server)

        (let [request-handler (if use-compression?
                                (create-gzip-handler (.getHandler server) MIN_GZIP_SIZE)
                                (.getHandler server))
              request-handler (if use-access-log?
                                (create-access-log-handler request-handler)
                                request-handler)]
          (doto server
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
