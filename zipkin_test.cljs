(do
  (require '[clj-zipkin.tracer :as t])

  (require '[thrift-clj.core :as thrift])
  (require '[clojure.data.codec.base64 :as b64])
  (require '[clj-scribe :as scribe])
  (require '[thrift-clj.gen.core :as c])
  (require '[byte-streams])
  (require '[clj-time.core :as time])
  (require '[clj-time.coerce :as time-coerce])

  (thrift/import
    (:types [com.twitter.zipkin.gen
             Span Annotation BinaryAnnotation AnnotationType Endpoint
             LogEntry StoreAggregatesException AdjustableRateException])
    (:clients com.twitter.zipkin.gen.ZipkinCollector))
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Defining an api

(defn create-context
  "Creates a context object. The service name is the name of the service to use in the trace.
  The ip-address should be for the current host."
  [service-name ip-address]
  (let [scribe-logger (scribe/async-logger :host "127.0.0.1"
                                           :port 9410
                                           :category "zipkin")]
    {:system {:zipkin {:endpoint (Endpoint. (t/ip-str-to-int ip-address) 0 service-name)
                       :scribe-logger scribe-logger}}
     :request {:tracing-enabled? true
               :trace-info {
                            :span-name nil
                            :span-id nil
                            :parent-span-id nil
                            :trace-id nil}}}))

(defn record-span
  "Records the span info as a new span with annotations in Zipkin."
  [zipkin-info trace-info start-time stop-time]
  (let [{:keys [endpoint scribe-logger]} zipkin-info
        {:keys [span-name span-id parent-span-id trace-id]} trace-info
        a1 (Annotation. (* 1000 (time-coerce/to-long start-time))
                        (str "start:" span-name) endpoint 0)
        a2 (Annotation. (* 1000 (time-coerce/to-long stop-time))
                        (str "end:" span-name) endpoint 0)
        span (t/thrift->base64 (Span. trace-id span-name span-id parent-span-id [a1 a2] [] 0))]
    (clojure.pprint/pprint trace-info)
    (scribe/log scribe-logger [span])))

(defn new-span-trace-info
  "Takes existing trace info from the context and the span name and creates a new trace info
  for this new span."
  [context span-name]
  (let [parent-trace-info (get-in context [:request :trace-info])]
    {:trace-id (or (:trace-id parent-trace-info) (t/create-id))
                    :parent-span-id (:span-id parent-trace-info)
                    :span-name span-name
                    :span-id (t/create-id)}))

(defn proto-trace
  "A prototype function to write code before attempting a macro."
  [span-name context block-fn]
  (let [trace-info (new-span-trace-info context span-name)
        start-time (time/now)
        result (block-fn (assoc-in context [:request :trace-info] trace-info))
        stop-time (time/now)]
    (record-span (-> context :system :zipkin) trace-info start-time stop-time)
    result))

;; TODO consider reversing span name and context
(defmacro trace
  [span-name context & body]
  `(let [trace-info# (new-span-trace-info ~context ~span-name)
         start-time# (time/now)
         ~'context (assoc-in ~context [:request :trace-info] trace-info)
         result (block-fn (assoc-in context [:request :trace-info] trace-info))
         stop-time (time/now)]
     (record-span (-> context :system :zipkin) trace-info start-time stop-time)
     result))


(defn tracefn
  "TODO"
  [span-name f]
  (fn [context & args]
    (let [trace-info (new-span-trace-info context span-name)
          start-time (time/now)
          new-context (assoc-in context [:request :trace-info] trace-info)
          result (apply f new-context args)
          stop-time (time/now)]
      (record-span (-> context :system :zipkin) trace-info start-time stop-time)
      result)))

(tracefn "building something"
         (fn [context y]
           (Thread/sleep y)))

(defmacro deftracefn
  "TODO"
  ;; TODO make it more robust to take defn style with metadata and without a docstring
  [var-name doc-string bindings & body]
  `(def ~var-name
     ~doc-string
     (tracefn ~(str var-name)
              (fn ~bindings ~@body))))


(macroexpand '(deftracefn build-something
  "Blah blah blah"
  [context arg1]
  (println "hi")))

(def build-something
  "Blah blah blah"
  (tracefn "build-something" (fn [context arg1] (println "hi"))))



(comment
  (let [context (create-context "Search" "127.0.0.1")]
    (trace "search" context
           (trace "apply-acls" context (Thread/sleep 100))
           (trace "simplify" context (Thread/sleep 100))
           (trace "execute-query" context (Thread/sleep 100))
           (trace "get-results" context
                  (trace "transform" context
                         (trace "get-metadata" context (Thread/sleep 100))
                         (trace "convert-xml" context (Thread/sleep 100)))))))))

  (let [context (create-context "Search" "127.0.0.1")
        sleeper (fn [_] (Thread/sleep 100))]
    (proto-trace
      "search" context
      (fn [context]

        (proto-trace "apply-acls" context sleeper)
        (proto-trace "simplify" context sleeper)
        (proto-trace "execute-query" context sleeper)
        (proto-trace
          "get-results" context
          (fn [context]
            (proto-trace
              "transform" context
              (fn [context]
                (proto-trace "get-metadata" context sleeper)
                (proto-trace "convert-xml" context sleeper))))))))





)





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn anno-time [t name]
  (Annotation. (+ (* t 1000) (* 1000 (time-coerce/to-long (time/now))))
               name
               (t/host->endpoint "127.0.0.1")
               0))

(defn anno-duration [t length name]
  (let [start-time (+ (* t 1000) (* 1000 (time-coerce/to-long (time/now))))]
    (Annotation. start-time
                 name
                 (t/host->endpoint "127.0.0.1")
                 length)))

(let [logger (scribe/async-logger :host "127.0.0.1"
                                  :port 9410
                                  :category "zipkin")
      trace-id (t/create-id)
      root-id (t/create-id)
      span-root (t/thrift->base64
                  (Span. trace-id
                         "root3"
                         root-id
                         nil
                         [(anno-time 1 "root") (anno-time 10 "root")]
                         []
                         0))
      span-child1 (t/thrift->base64
                    (Span. trace-id
                           "child1"
                           (t/create-id)
                           root-id
                           [(anno-time 2 "child1") (anno-time 5 "child1")]
                           []
                           0))
      span-child2 (t/thrift->base64
                    (Span. trace-id
                           "child2"
                           (t/create-id)
                           root-id
                           [(anno-time 6 "child2") (anno-time 8 "child2")]
                           []
                           0))]
  (scribe/log logger [span-root])
  (Thread/sleep 100)
  (scribe/log logger [span-child1])
  (Thread/sleep 100)
  (scribe/log logger [span-child2])
  )

(let [root-id (t/create-id)
      main-options {:host "127.0.0.1"
                    :trace-id (t/create-id)
                    :scribe {:host "127.0.0.1" :port 9410}}
      trace-id (t/create-id)]
  (t/trace {:span "root2" :span-id root-id :host "127.0.0.1"
            :trace-id (t/create-id)
            :scribe {:host "127.0.0.1" :port 9410}}
           (do
             (Thread/sleep 100)
             (t/trace {:span "child1" :span-id (t/create-id) :parent-span-id root-id
                       :host "127.0.0.1"
                       :trace-id (t/create-id)
                       :scribe {:host "127.0.0.1" :port 9410}}
                      (Thread/sleep 100))
             (Thread/sleep 100)
             (t/trace {:span "child2" :span-id (t/create-id) :parent-span-id root-id
                       :host "127.0.0.1"
                       :trace-id (t/create-id)
                       :scribe {:host "127.0.0.1" :port 9410}}
                      (Thread/sleep 100)))))


(defmacro trace
  [name span-id & body]
  `(t/trace {:trace-id 1 :span-id ~span-id :span ~name :host "127.0.0.1"  :scribe {:host "127.0.0.1" :port 9410}}
            (do
              ~@body)))

(defmacro trace-child
  [name parent-span-id & body]
  `(t/trace {:span-id (t/create-id) :trace-id 1 :parent-span-id ~parent-span-id :span ~name :host "127.0.0.1" :scribe {:host "127.0.0.1" :port 9410}}
            (do
              ~@body)))

(defn do-stuff []
  (t/trace {:span "println" :host "127.0.0.1"  :scribe {:host "127.0.0.1" :port 9410}}
           (println "hello")))


(let [psi (t/create-id)]
  (trace "TOP12" psi
         (trace-child "println" psi
                      (println "hi")
                      (Thread/sleep 10))
         (trace-child "println" psi
                      (println "hi")
                      (Thread/sleep 10))
         (trace-child "sleeping" psi
                      (Thread/sleep 100))
         (trace-child "sleeping" psi
                      (Thread/sleep 100))))


(clojure.walk/macroexpand-all '(trace-span "this" (println "jason")))

(let* [logger (clj-scribe/async-logger
                :host (-> {:host "127.0.0.1", :port 9410} :host)
                :port (-> {:host "127.0.0.1", :port 9410} :port)
                :category "zipkin")
       trace-id 1
       span-list (atom [])
       span-id nil
       result (clj-zipkin.tracer/trace*
                {:host "127.0.0.1",
                 :scribe {:host "127.0.0.1", :port 9410},
                 :span "this",
                 :trace-id 1,
                 :span-id (clj-zipkin.tracer/create-id)}
                (do (println "jason")))
       _ (clj-scribe/log logger @span-list)]
      result)


(let* [logger (clj-scribe/async-logger
                :host
                (:host {:host "127.0.0.1", :port 9410})
                :port
                (:port {:host "127.0.0.1", :port 9410})
                :category
                "zipkin")
       trace-id (let* [or_d 1]
                      (if or_d
                        or_d
                        (clj-zipkin.tracer/create-id)))
       span-list (clojure.core/atom [])
       span-id nil
       result (let* [parent-id span-id
                     span-id (let* [or_d (clj-zipkin.tracer/create-id)]
                                   (if or_d
                                     or_d
                                     (clj-zipkin.tracer/create-id)))
                     start-time (clj-time.core/now)
                     result2 (do (println "jason"))
                     end-time (clj-time.core/now)
                     span (clj-zipkin.tracer/create-timestamp-span
                            "this"
                            "127.0.0.1"
                            trace-id
                            span-id
                            parent-id
                            start-time
                            end-time)
                     _ (clojure.core/swap!
                         span-list
                         (fn* ([l]
                               (clojure.core/cons span l))))]
                    result2)
       _ (clj-scribe/log logger @span-list)]
      result)


(t/trace {:trace-id (t/create-id)
          :span-id (t/create-id)
          :host "127.0.0.1" :span "GET" :scribe {:host "127.0.0.1" :port 9410}}
         (do
           (do-stuff)
           (Thread/sleep 300)

           #_(t/trace {:span "sleep1"}
                      (Thread/sleep 300))
           #_(t/trace {:span "sleep2"}
                      (Thread/sleep 200))
           #_(t/trace {:span "sleep3"}
                      (Thread/sleep 100))

           #_(t/trace {:span "println"}
                      (println "hello2"))


           ))