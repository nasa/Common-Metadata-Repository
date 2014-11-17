(ns cmr.system-int-test.utils.test-environment
  "Contains helper functions related to the test environment")


(defn runnable-env?
  [expected-env]
  (try
    (some-> 'user/system-type
            find-var
            var-get
            (= expected-env))
    (catch Exception e
      (.printStackTrace e)
      (println "Exception indicates this is not is most likely an in memory database")
      (= :in-memory expected-env))))

(defn real-database?
  "Returns true if running with a real database"
  []
  (runnable-env? :external-dbs))

(defn in-memory-database?
  "Returns true if running with a in-memory database"
  []
  (runnable-env? :in-memory))

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