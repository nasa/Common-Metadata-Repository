(ns cmr.system-int-test.utils.test-environment
  "Contains helper functions related to the test environment"
  #_(require [cmr.system-int-test.utils.url-helper :as url]))

; (defn- get-component-type-map
;   "Returns the message queue history."
;   []
;   (let [component-map (-> (client/get (url/dev-system-get-component-types-url)
;                                       {:connection-manager (s/conn-mgr)})
;                           :body
;                           json/decode)]
;     (into {}
;           (for [[k v] component-map]
;             [(keyword k) (keyword v)]))))

; (defn real-database?
;   "Returns true if running with a real database"
;   []
;   false)
; ; (= (:db (get-component-type-map)) :external))

; (defn in-memory-database?
;   "Returns true if running with a in-memory database"
;   []
;   true)
; ; (= (:db (get-component-type-map)) :in-memory))


; (defn real-message-queue?
;   "Returns true if running with a real message-queue"
;   []
;   true)
; ; (= (:message-queue (get-component-type-map)) :external))


; (defmacro only-with-real-database
;   "Executes the body of the call if the test environment is running with the real Oracle DB."
;   [& body]
;   `(when (real-database?)
;      ~@body))

; (defmacro only-with-in-memory-database
;   "Executes the body of the call if the test environment is running with the in memory database"
;   [& body]
;   `(when (in-memory-database?)
;      ~@body))

; (defmacro only-with-real-message-queue
;   "Executes the body of the call if the test environment is running with the real RabbitMQ."
;   [& body]
;   `(when (real-message-queue?)
;      ~@body))

; (comment
;   (real-database?)
;   (real-message-queue?))