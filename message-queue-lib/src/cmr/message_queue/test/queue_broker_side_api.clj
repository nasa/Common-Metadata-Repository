(ns cmr.message-queue.test.queue-broker-side-api
  "Defines routes for accessing a controlling the queue broker in testing through a side api."
  (:require [compojure.core :refer :all]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.message-queue.test.queue-broker-wrapper :as wrapper]
            [cmr.message-queue.config :as config]))


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
              expect-timeout? (= timeout 0)]
          (debug (format "Setting message queue publish timeout to %d ms" timeout))
          (config/set-publish-queue-timeout-ms! timeout)
          (wrapper/set-message-queue-timeout-expected!
           broker-wrapper
           expect-timeout?)
          {:status 200})))))

