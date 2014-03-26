(ns cmr.elastic-utils.test.embedded-elastic-server
  (:require [clojure.test :refer :all]
            [cmr.elastic-utils.embedded-elastic-server :as s]
            [cmr.common.lifecycle :as l]

            [clojurewerkz.elastisch.native  :as es]
            [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.native.response :as esrsp]

            [clj-http.client :as client]))

(def http-port
  "Port chosen to avoid conflicts"
  9234)

(def transport-port
  "Port chosen to avoid conflicts"
  9334)

(defn connect-to-server!
  "Uses elastisch to connect to a local elasticsearch server."
  [server]
  (es/connect-to-local-node! (:node server)))

(defn start-and-connect
  []
  (let [server (l/start (s/create-server http-port transport-port) nil)]
    (connect-to-server! server)
    server))


(def simple-index-name
  "simples")

(def simple-type-name
  "simple")

(def simple-mapping-properties
  {simple-type-name ;; a type name
   {:properties {:value  ;; a field within the type
                 {:type "string"
                  :index "not_analyzed"
                  :store "true"}}}})

(defmacro with-connected-server
  "A macro for starting a server, connecting to it, executing a body, and then stopping the server."
  [& body]
  `(let [server# (start-and-connect)]
     (try
       ~@body
       (finally
         (l/stop server# nil)))))


(deftest test-embedded-server
  (testing "connect to server"
    (let [s (s/create-server http-port transport-port)
          s (l/start s nil)]
      (try
        (connect-to-server! s)
        (finally
          (l/stop s nil)))))
  (testing "Data available on port"
    (with-connected-server
      (is (= 200 (:status (client/get (format "http://localhost:%d" http-port)))))))
  (testing "create index"
    (with-connected-server
      (esi/create simple-index-name :settings {"index" {"number_of_shards" 1}}
                  :mappings simple-mapping-properties)
      (esi/delete simple-index-name))))
