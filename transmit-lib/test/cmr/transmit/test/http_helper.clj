(ns cmr.transmit.test.http-helper
  (:require
   [clojure.test :refer [deftest is]]
   [cmr.common.config :as config]
   [cmr.common.services.health-helper :as hh]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.indexer :as indexer])
  (:import
   (com.sun.net.httpserver HttpExchange HttpHandler HttpServer)
   (java.net InetSocketAddress ServerSocket)
   (java.util.concurrent Executors)))

(defn- indexer-context
  [port]
  {:system {:indexer-connection (conn/create-app-connection
                                 {:protocol "http" :host "localhost" :port port :context ""})}})

(deftest health-timeout-keeps-connection-pool-test
  (let [calls (atom 0)
        slow-response-sent (promise)
        executor (Executors/newCachedThreadPool)
        server (doto (HttpServer/create (InetSocketAddress. "localhost" 0) 0)
                 (.createContext "/health"
                                 (reify HttpHandler
                                   (handle [_ exchange]
                                     (let [^HttpExchange exchange exchange
                                           first-call? (= 1 (swap! calls inc))]
                                       (when first-call?
                                         (Thread/sleep 3000))
                                       (.sendResponseHeaders exchange 200 -1)
                                       (.close exchange)
                                       (when first-call?
                                         (deliver slow-response-sent true))))))
                 (.setExecutor executor)
                 (.start))
        context (indexer-context (.getPort (.getAddress server)))]
    (try
      (binding [config/runtime-config-values (atom {})]
        (hh/set-health-check-timeout-seconds! 0)
        (is (= {:ok? false :problem "health check operation timed out."}
               (indexer/get-indexer-health context)))
        ;; A stopped thread blocked on a socket read only dies once the late response arrives.
        (deref slow-response-sent 5000 nil)
        (Thread/sleep 500)
        (is (:ok? (indexer/get-indexer-health context))))
      (finally
        (.stop server 0)
        (.shutdownNow executor)))))

(deftest health-request-failure-test
  (let [port (with-open [socket (ServerSocket. 0)] (.getLocalPort socket))]
    (is (false? (:ok? (indexer/get-indexer-health (indexer-context port)))))))
