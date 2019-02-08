(ns cmr.mock-echo.client.mock-urs-client
  "Contains functions for communicating with the mock urs api that aren't normal URS
  operations"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

(defn- create-users-url
  "Call to create users in mock URS. Depending on how tests are being run the context for URS URLs
  might or might not have /urs as the relative root URL. We'll make sure we use the correct URL here
  in all circumstances."
  [conn]
  (let [base-url (conn/root-url conn)]
    (if (string/includes? base-url "urs")
      (format "%s/users" base-url)
      (format "%s/urs/users" base-url))))

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
