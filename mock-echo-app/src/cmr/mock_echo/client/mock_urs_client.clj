(ns cmr.mock-echo.client.mock-urs-client
  "Contains functions for communicating with the mock urs api that aren't normal URS
  operations"
  (:require [cmr.transmit.http-helper :as h]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [cheshire.core :as json]))

(defn- create-users-url
  [conn]
  (format "%s/users" (conn/root-url conn)))

(defn create-users
  "Creates the users in mock urs given an array of maps with :username and :password"
  [context users]
  (let [{:keys [status body]} (h/request context :urs
                                         {:url-fn create-users-url
                                          :method :post
                                          :raw? true
                                          :http-options {:body (json/generate-string users)
                                                         :throw-exceptions false
                                                         :content-type mt/json}})]
    (when-not (= status 201)
      (errors/internal-error!
       (format "Unexpected status %d from response. body: %s"
               status (pr-str body))))))




