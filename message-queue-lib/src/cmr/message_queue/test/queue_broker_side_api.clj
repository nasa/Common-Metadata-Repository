(ns cmr.message-queue.test.queue-broker-side-api
  "Defines routes for accessing a controlling the queue broker in testing through a side api and
  function for accessing those routes."
  (:require [compojure.core :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.string :as str]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.message-queue.test.queue-broker-wrapper :as wrapper]
            [cmr.message-queue.config :as config]
            [cmr.common-app.test.side-api :as side-api]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for defining the API

(defn build-routes
  "Creates the routes that can be used to access and control the queue broker wrapper"
  [broker-wrapper]
  (routes
    (context "/message-queue" []
      (POST "/wait-for-terminal-states" []
        (wrapper/wait-for-terminal-states broker-wrapper)
        {:status 200})

      (GET "/history" {:keys [params]}
        {:status 200
         :body (wrapper/get-message-queue-history broker-wrapper (:queue-name params))
         :headers {"Content-Type" "application/json"}})


      (POST "/set-retry-behavior" {:keys [params]}
        (let [num-retries (:num-retries params)]
          (debug (format "Setting message queue to retry messages %s times"
                         num-retries))
          (wrapper/set-message-queue-retry-behavior!
           broker-wrapper
           (Integer/parseInt num-retries))
          {:status 200}))

      ;; Used to change the timeout used for queueing messages on the message queue. For tests which
      ;; simulate a timeout error, set the timeout value to 0.
      (POST "/set-publish-timeout" {:keys [params]}
        (let [timeout (Integer/parseInt (:timeout params))
              expect-timeout? (zero? timeout)]
          (debug (format "Setting message queue publish timeout to %d ms" timeout))
          (config/set-publish-queue-timeout-ms! timeout)
          (wrapper/set-message-queue-timeout-expected!
           broker-wrapper
           expect-timeout?)
          {:status 200})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for accessing the API


(defn wait-for-terminal-states
  "Waits until all the message queues have reached a terminal state"
  []
  (client/post
   (format "http://localhost:%s/message-queue/wait-for-terminal-states" (side-api/side-api-port))))

(defn set-message-queue-retry-behavior
  "Set the message queue retry behavior"
  [num-retries]
  (client/post
    (format "http://localhost:%s/message-queue/set-retry-behavior" (side-api/side-api-port))
    {:query-params {:num-retries num-retries}}))

(defn set-message-queue-publish-timeout
  "Set the message queue publish timeout"
  [timeout]
  (client/post
    (format "http://localhost:%s/message-queue/set-publish-timeout" (side-api/side-api-port))
    {:query-params {:timeout timeout}}))

(defn get-message-queue-history
  "Returns the message queue history."
  [queue-name]
  (-> (client/get (format "http://localhost:%s/message-queue/history" (side-api/side-api-port))
                  {:query-params {:queue-name queue-name}})
      :body
      (json/decode true)))
