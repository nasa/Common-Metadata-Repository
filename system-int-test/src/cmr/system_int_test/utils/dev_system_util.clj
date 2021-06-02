(ns cmr.system-int-test.utils.dev-system-util
  "Methods for accessing the dev system control api."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as str]
   [clojure.test :refer [is]]
   [cmr.common.util :as util]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.url-helper :as url]))

(defn admin-connect-options
  "This returns the options to send when executing admin commands"
  []
  {:connection-manager (s/conn-mgr)
   :query-params {:token "mock-echo-system-token"}})

(defn reset
  "Resets the database, queues, and the elastic indexes"
  []
  (qb-side-api/wait-for-terminal-states)
  (client/post (url/dev-system-reset-url) (admin-connect-options))
  (index/refresh-elastic-index))

(defn eval-in-dev-sys*
  "Evaluates the code given in dev system"
  [code]
  (let [response (client/post (url/dev-system-eval-url)
                              (assoc (admin-connect-options)
                                     :body (pr-str code)))]
    (assert (= 200 (:status response)) (:body response))
    (when (= 200 (:status response))
      (read-string (:body response)))))

(defmacro eval-in-dev-sys
  "Evaluates the code given in dev system. Call this with code like you'd use with a macro. Returns
  the result of evaluating the code in dev system."
  [body]
  `(eval-in-dev-sys* ~body))

(comment

  ;; Syntax unquote (The backtick reader macro in clojure --> ` ) should be used when calling
  ;; eval-in-dev-sys to fully evaluate symbols and to allow referral to variables defined outside
  ;; the call

  ;; Example of calling eval-in-dev-sys with code that refers to a variable outside the eval
  (let [x 5]
    (eval-in-dev-sys `(+ ~x ~x)))

  ;; This is sent to dev system as a string
  (clojure.core/+ 5 5)

  ;; Returned from dev system
  10

  ;; Example of referring to a fully qualified symbole
  (eval-in-dev-sys `(str/join (range 3)))

  ;; This is sent to dev system to evaluate
  (clojure.string/join (clojure.core/range 3))

  ;; Returned from dev system
  "012")



(defn clear-caches
  "Clears all the caches in the CMR."
  []
  (client/post (url/dev-system-clear-cache-url) (admin-connect-options)))

(defn freeze-time!
  "Sets the current time to the time represented by the given datetime string or
  whatever the real time is now if no datetime string is given."
  ([]
   (client/post (url/dev-system-freeze-time-url) (admin-connect-options)))
  ([date-time-str]
   (client/put (format "%s/%s" (url/dev-system-freeze-time-url) date-time-str)
               (admin-connect-options))))

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

(defn resume-time-fixture!
  "Guarantees time will resume for tests that manipulate time."
  [f]
  (try
    (f)
    (finally
      (clear-current-time!))))
