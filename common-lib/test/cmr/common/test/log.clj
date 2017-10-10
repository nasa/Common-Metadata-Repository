(ns cmr.common.test.log
  "Tests for log functions."
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [cmr.common.log :as log]))

(deftest create-logger-test
  (testing "creating logger"
    ;; defrecord defines type-and-value-based =, and will defined Java .hashCode and .equals
    ;; consistent with the contract for java.util.Map.  Therefore, .equals is used to compare
    ;; the values instead of also the types which are different.
    (is (.equals {:file nil, :stdout-enabled? true, :level :info}
           (log/create-logger)))
    (is (.equals '{:file nil, :stdout-enabled? true, :level :debug}
           (log/create-logger-with-log-level "debug")))))

(def ns-pattern-map
  "This namespace pattern map describes a logging configuration where the two listed namespaces
   can log debug levels and more severe. All other names spaces can log warning messages or more
   severe.  Remember though that all messages will still be filtered out if they are less severe
   than the main logging level - not set here."
  {:all :warn
   "cmr.common-app.services.logging-config" :debug
   "cmr.common-app.services.search" :debug})

(deftest get-namespace-from-pattern-map-test
  (testing "testing getting the namespace from the pattern map"
    (is (= "cmr.common-app.services.search"
           (log/get-namespace-from-pattern-map ns-pattern-map "cmr.common-app.services.search")))
    (is (= :all
           (log/get-namespace-from-pattern-map ns-pattern-map "cmr.common-app.services.ingest")))))

(deftest log-by-ns-pattern-test
  (let [old-level (:level (log/get-logging-configuration))
        timbre-config {:ns-pattern-map ns-pattern-map}
        timbre-config-higher {:ns-pattern-map {:all :warn
                                               "cmr.common-app.services.logging-config" :info
                                               "cmr.common-app.services.search" :info}}
        timbre-config-all-debug {:ns-pattern-map {:all :debug}}
        timbre-config-all-info {:ns-pattern-map {:all :info}}]
    (testing "testing the log-by-ns-pattern middleware"
      ;; This set of tests tests how a log message is either filtered out or passes through
      ;; the log-by-ns-pattern filter.
      ;; :?ns-str is the namespace of where the log message comes from
      ;; :level is the level of the log message
      ;; :config is the timbre configuration that includes the ns-pattern-map

      ;; Sets the timbre logging level at fatal for the following tests
      (log/merge-logging-configuration {:level :fatal})

      ;; has namespace test - this is assuming the main logging level is set to :fatal
      ;; 1) log message level lower than ns-logging-map
      ;;    and main logging level - this gets filtered out.
      (testing "log message level lower than namespace and main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :trace
                                       :config timbre-config}))))
      ;; 2) log message level at namespace logging level
      ;;    but lower than main logging level - this gets filtered out.
      (testing "log message level at namespace but lower than main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :debug
                                       :config timbre-config}))))
      ;; 3) log message level higher (more severe) than namespace logging level
      ;;    but still lower than main logging level - this gets filtered out
      (testing "log message level higher than namespace but lower than main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :error
                                       :config timbre-config}))))

      ;; Sets the timbre logging level at trace for the following tests
      (log/merge-logging-configuration {:level :trace})

      ;; 4) log message level lower than namespace logging level
      ;;    but at the main logging level - this gets filtered out
      (testing "log message level lower than namespace but at the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :trace
                                       :config timbre-config}))))

      ;; Sets the timbre logging level at debug for the following tests
      (log/merge-logging-configuration {:level :debug})

      ;; 5) log message level at than namespace logging level
      ;;    and at the main logging level - this message passes through
      (testing "log message level at the namespace and at the main logging level."
        (is (= {:config {:ns-pattern-map {:all :warn,
                                          "cmr.common-app.services.search" :debug,
                                          "cmr.common-app.services.logging-config" :debug}}
                :?ns-str "cmr.common-app.services.search",
                :level :debug}
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :debug
                                       :config timbre-config}))))

      ;; Sets the timbre logging level at debug for the following tests
      (log/merge-logging-configuration {:level :info})

      ;; 6) log message level higher than namespace logging level
      ;;    and at the main logging level - the main logging level needs to be at or lower
      ;;    than the namespace logging level so the message gets filtered out.
      (testing "log message level higher than namespace and at the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :info
                                       :config timbre-config}))))

      ;; Sets the timbre logging level at trace for the following tests
      (log/merge-logging-configuration {:level :trace})

      ;; 7) log message level lower than namespace logging level
      ;;    and higher (more severe) than the main logging level - this gets filtered out
      (testing "log message level lower than namespace and higher than the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :debug
                                       :config timbre-config-higher}))))

      ;; 8) log message level is at the namespace logging level
      ;;    and higher (more severe) than the main logging level - this message passes through
      (testing "log message level at the namespace and higher than the main logging level."
        (is (= {:config {:ns-pattern-map {:all :warn,
                                          "cmr.common-app.services.search" :info,
                                          "cmr.common-app.services.logging-config" :info}},
                :?ns-str "cmr.common-app.services.search",
                :level :info}
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :info
                                       :config timbre-config-higher}))))
      ;; 9) log message level is higher than the namespace logging level
      ;;    and higher (more severe) than the main logging level - this message passes through
      (testing "log message level higher than namespace and higher than the main logging level."
        (is (= {:config {:ns-pattern-map {:all :warn,
                                          "cmr.common-app.services.search" :info,
                                          "cmr.common-app.services.logging-config" :info}},
                :?ns-str "cmr.common-app.services.search",
                :level :error}
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.search"
                                       :level :error
                                       :config timbre-config-higher}))))

      ;; Sets the timbre logging level at fatal for the following tests
      (log/merge-logging-configuration {:level :fatal})

      ;; no namespace test - this is assuming the main logging level is set to :fatal
      ;; 10) log message level lower than :all
      ;;     and main logging level - this gets filtered out.
      (testing "log message level lower than :all and the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :trace
                                       :config timbre-config}))))
      ;; 11) log message level at :all logging level
      ;;     but lower than main logging level - this gets filtered out.
      (testing "log message level at :all and lower than the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :debug
                                       :config timbre-config-all-debug}))))
      ;; 12) log message level higher (more severe) than :all logging level
      ;;     but still lower than main logging level - this gets filtered out
      (testing "log message level heigher than :all and lower than the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :error
                                       :config timbre-config-all-debug}))))

      ;; Sets the timbre logging level at debug for the following tests
      (log/merge-logging-configuration {:level :debug})

      ;; 13) log message level lower than :all logging level
      ;;     but at the main logging level - this gets filtered out
      (testing "log message level lower than :all and at the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :debug
                                       :config timbre-config-higher}))))

      ;; 14) log message level at :all logging level
      ;;     and at the main logging level - this message passes through
      (testing "log message level at :all and at the main logging level."
        (is (= {:config {:ns-pattern-map {:all :debug}}
                :?ns-str "cmr.common-app.services.ingest",
                :level :debug}
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :debug
                                       :config timbre-config-all-debug}))))

      ;; Sets the timbre logging level at debug for the following tests
      (log/merge-logging-configuration {:level :error})

      ;; 15) log message level higher than :all logging level
      ;;     and at the main logging level - the main logging level needs to be at or lower
      ;;     than the namespace logging level so the message gets filtered out.
      (testing "log message level higher than :all and at the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :error
                                       :config timbre-config-all-debug}))))

      ;; Sets the timbre logging level at trace for the following tests
      (log/merge-logging-configuration {:level :trace})

      ;; 16) log message level lower than :all logging level
      ;;     and higher (more severe) than the main logging level - this gets filtered out
      (testing "log message level lower than :all and higher than the main logging level."
        (is (= nil
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :debug
                                       :config timbre-config-higher}))))

      ;; Sets the timbre logging level at debug for the following tests
      (log/merge-logging-configuration {:level :debug})

      ;; 17) log message level is at the :all logging level
      ;;    and higher (more severe) than the main logging level - this message passes through
      (testing "log message level at :all and higher than the main logging level."
        (is (= {:config {:ns-pattern-map {:all :info}},
                :?ns-str "cmr.common-app.services.ingest",
                :level :info}
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :info
                                       :config timbre-config-all-info}))))

      ;; 18) log message level is higher than the :all logging level
      ;;    and higher (more severe) than the main logging level - this message passes through
      (testing "log message level is higher than :all and higher than the main logging level."
        (is (= {:config {:ns-pattern-map {:all :info}}
                :?ns-str "cmr.common-app.services.ingest",
                :level :error}
               (log/log-by-ns-pattern {:?ns-str "cmr.common-app.services.ingest"
                                       :level :error
                                       :config timbre-config-all-info}))))

      (testing "reset the logging level to what it originally started."
        (is (= {:ns-whitelist [],
                :ns-blacklist [],
                :ns-pattern-map {:all :error},
                :level :error}
               (log/reset-logging-configuration)))))))

(deftest get-partial-logging-configuration-test
  (testing "testing getting the allowed logging configuration for a user to change."
    (is (= {:ns-whitelist [],
            :ns-blacklist [],
            :ns-pattern-map {:all :error},
            :level :error}
           (log/get-partial-logging-configuration)))))

(deftest filter-out-levels-not-allowed-test
  (testing "testing filtering out the main level when it is set too high."
    (is (= {:ns-pattern-map {:all :warn
                             "cmr.common-app.services.logging-config" :debug
                             "cmr.common-app.services.search" :debug}}
           (log/filter-out-levels-not-allowed
              {:level :error
               :ns-pattern-map {:all :warn
                                "cmr.common-app.services.logging-config" :debug
                                "cmr.common-app.services.search" :debug}}))))

  (testing "testing filtering out nothing because all are at or below the allowed logging level."
    (is (= {:level :warn
            :ns-pattern-map {:all :warn
                             "cmr.common-app.services.logging-config" :debug
                             "cmr.common-app.services.search" :debug}}
           (log/filter-out-levels-not-allowed
            {:level :warn
             :ns-pattern-map {:all :warn
                              "cmr.common-app.services.logging-config" :debug
                              "cmr.common-app.services.search" :debug}}))))

  (testing "testing filtering out :all and a namespace because they are higher than what is allowed."
    (is (= {:level :warn
            :ns-pattern-map {"cmr.common-app.services.search" :debug}}
           (log/filter-out-levels-not-allowed
            {:level :warn
             :ns-pattern-map {:all :fatal
                              "cmr.common-app.services.logging-config" :fatal
                              "cmr.common-app.services.search" :debug}}))))
  (testing "testing filtering out everything because all are higher than the allowed logging level."
    (is (= {:ns-pattern-map {}}
           (log/filter-out-levels-not-allowed
            {:level :fatal
             :ns-pattern-map {:all :error
                              "cmr.common-app.services.logging-config" :fatal
                              "cmr.common-app.services.search" :fatal}})))))

(deftest merge-partial-logging-configuration-test
  (let [old-config (log/get-partial-logging-configuration)]
    (testing "testing getting the allowed logging configuration for a user to change."
      (testing "merging an empty configuration does nothing to the configuration."
        (is (= old-config
               (log/merge-partial-logging-configuration {}))))

      (testing "merging an empty configuration double map does nothing to the configuration."
        ;; This is checking to make sure if the higher than allowed filter, filters out everything
        ;; that the logging configuration won't be harmed.
        (is (= old-config
               (log/merge-partial-logging-configuration {:ns-pattern-map {}}))))

      (testing "merging a ns-pattern-map that only changes the :all value."
        (is (= (assoc-in old-config [:ns-pattern-map :all] :info)
               (log/merge-partial-logging-configuration {:ns-pattern-map {:all :info}}))))

      (testing "merging a ns-pattern-map that adds a namespace value."
        (is (= (assoc old-config :ns-pattern-map {:all :info
                                                  "cmr.common-app.services.logging-config" :debug})
               (log/merge-partial-logging-configuration
                {:ns-pattern-map {"cmr.common-app.services.logging-config" :debug}}))))

      (testing "resetting the logging configuration."
        (is (= old-config
               (log/reset-logging-configuration)))))))
