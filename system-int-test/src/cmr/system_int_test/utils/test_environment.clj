(ns cmr.system-int-test.utils.test-environment
  "Contains helper functions related to the test environment")

(defn runnable-env?
  [key expected-value]
  (try
    (some-> 'user/system-type
            find-var
            var-get
            (get key)
            (= expected-value))
    (catch Exception e
      (.printStackTrace e)
      (println "Exception thrown - Default to in-memory implementation for " key)
      (= :in-memory expected-value))))

(defn real-database?
  "Returns true if running with a real database"
  []
  (runnable-env? :db :external))

(defn in-memory-database?
  "Returns true if running with a in-memory database"
  []
  (runnable-env? :db :in-memory))

(defmacro only-with-real-database
  "Executes the body of the call if the test environment is running with the real Oracle DB."
  [& body]
  `(when (real-database?)
     ~@body))

(defmacro only-with-in-memory-database
  "Executes the body of the call if the test environment is running with the in memory database"
  [& body]
  `(when (in-memory-database?)
     ~@body))