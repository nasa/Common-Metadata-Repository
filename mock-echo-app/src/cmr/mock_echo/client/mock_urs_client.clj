(ns cmr.mock-echo.client.mock-urs-client
  "Contains functions for communicating with the mock urs api that aren't normal URS
  operations"
  (:require
   [cheshire.core :as json]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

(defn- create-users-url
  "Call to create users in mock URS. Makes an assumption that the URS root context in the
  connection is an empty string."
  [conn]
  (format "%s/urs/users" (conn/root-url conn)))

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
