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

(defmacro only-with-real-database
  "Executes the body of the call if the test environment is running with the real Oracle DB."
  [& body]
  `(when (runnable-env? :external-dbs)
     ~@body))

(defmacro only-with-in-memory-database
  "Executes the body of the call if the test environment is running with the in memory database"
  [& body]
  `(when (runnable-env? :in-memory)
     ~@body))