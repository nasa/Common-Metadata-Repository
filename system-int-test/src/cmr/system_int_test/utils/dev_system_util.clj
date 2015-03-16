(ns cmr.system-int-test.utils.dev-system-util
  "Methods for accessing the dev system control api."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.common.util :as util]
            [cmr.system-int-test.system :as s]))

(defn admin-connect-options
  "This returns the options to send when executing admin commands"
  []
  {:connection-manager (s/conn-mgr)
   :query-params {:token "mock-echo-system-token"}})

(defn reset
  "Resets the database, queues, and the elastic indexes"
  []
  (client/post (url/dev-system-reset-url) (admin-connect-options))
  (index/wait-until-indexed))

(defn clear-caches
  "Clears all the caches in the CMR."
  []
  (client/post (url/dev-system-clear-cache-url) (admin-connect-options)))

(defn freeze-time!
  "Sets the current time to whatever the real time is now."
  []
  (client/post (url/dev-system-freeze-time-url) (admin-connect-options)))

(defn advance-time!
  "Increases the time override by a number of seconds"
  [num-secs]
  (client/post (url/dev-system-advance-time-url num-secs) (admin-connect-options)))

(defn clear-current-time!
  "Clears the current time if one was set."
  []
  (client/post (url/dev-system-clear-current-time-url) (admin-connect-options)))

(defn freeze-resume-time-fixture
  "This is a clojure.test fixture that will freeze time then clear any time override at the end
  of the test."
  []
  (fn [f]
    (try
      (freeze-time!)
      (f)
      (finally
        (clear-current-time!)))))
