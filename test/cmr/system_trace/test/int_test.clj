(ns cmr.system-trace.test.int-test
  (:require [clojure.test :refer :all]
            [cmr.system-trace.core :as c :refer [deftracefn]]
            [cmr.system-trace.context :as cxt]
            [cmr.system-trace.http :as h]
            [clj-zipkin.tracer :as t]
            [cmr.common.util :as u]
            [clj-time.coerce :as coerce]))


;; Simulate service functions
(deftracefn service-a
  "Mock service A"
  [context]
  (Thread/sleep 5) ;sleep to simulate small amount of work
  "a")

(deftracefn service-b
  "Mock service B that calls A."
  [context]
  (Thread/sleep 5) ;sleep to simulate small amount of work
  (service-a context))

;; Simulate api

(defn api-call-a
  [request]
  (service-a (:request-context request)))

(defn api-call-b
  [request]
  (service-b (:request-context request)))

(def system
  {:db :mock-db
   :zipkin :mock-zipkin})

(defn mock-http-request
  [headers]
  {:headers headers})

(defn create-arg-recorder
  "Creates a function that will record all arguments its called with into an atom.
  It always returns nil."
  [arg-atom]
  (fn [& args]
    (swap! arg-atom conj args)
    nil))

(defn id-generator
  "Returns a new function that will return a new number each time it's called starting with 0"
  []
  (u/sequence->fn (range)))

(defn verify-record-span-calls
  [expected-traces actual-calls]
  (is (= expected-traces (map #(get (vec %) 1) actual-calls)))

  (is (= [:mock-zipkin] (distinct (map first actual-calls))))
  (doseq [[_ _ start stop] actual-calls]
    (is (< (coerce/to-long start) (coerce/to-long stop)))))


(deftest everything-integrated-test
  (testing "no trace ids in header"
    (let [record-span-args (atom [])]
      (with-redefs [cmr.system-trace.core/record-span (create-arg-recorder record-span-args)
                    t/create-id (id-generator)]
        (let [api-b (h/build-request-context-handler api-call-b system)]
          (is (= "a" (api-b (mock-http-request {}))))
          (verify-record-span-calls
            [{:trace-id 0 :parent-span-id 1 :span-name "service-a" :span-id 2}
             {:trace-id 0 :parent-span-id nil :span-name "service-b" :span-id 1}]
            @record-span-args))))))