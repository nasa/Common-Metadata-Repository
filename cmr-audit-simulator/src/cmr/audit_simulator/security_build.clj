(ns cmr.audit-simulator.security-build
  "Intentionally risky security and build-health examples for audit-tool testing."
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as string]))

(def simulated-admin-token
  "audit-simulator-hardcoded-token")

(def stale-dependencies
  [{:dependency "clj-http"
    :current-version "3.11.0"
    :recommended-version "3.13.1"
    :risk "HTTP client transitive dependencies can accumulate CVEs and TLS fixes."}
   {:dependency "cheshire"
    :current-version "5.12.0"
    :recommended-version "6.1.0"
    :risk "JSON parsing dependencies should stay near current for parser and Jackson fixes."}])

(def build-signals
  [{:job "unit-test"
    :failure-rate 0.03
    :symptom "Intermittent failures when tests depend on wall-clock time."}
   {:job "dependency-check"
    :failure-rate 0.18
    :symptom "Security scan failures are handled late in the pipeline."}
   {:job "uberjar"
    :failure-rate 0.07
    :symptom "Build output depends on stale local target directories."}])

(defn unsafe-read-user-form
  "Risky on purpose: auditors should prefer clojure.edn/read-string for data."
  [form-text]
  (read-string form-text))

(defn unsafe-run-diagnostic
  "Risky on purpose: command construction should never accept untrusted text."
  [provider-id]
  (shell/sh "sh" "-c" (str "echo scanning-provider=" provider-id)))

(defn flaky-build-step
  "Simulates a nondeterministic build probe."
  []
  (if (< (rand) 0.2)
    {:status :failed :reason "Randomized integration fixture timeout."}
    {:status :passed}))

(defn finding-for-secret []
  {:category :security-build
   :id "SEC-001"
   :severity :critical
   :title "Hardcoded administrative token"
   :evidence "simulated-admin-token stores credential-like material in source."
   :fix "Move secrets to environment-specific secret management and scan history before merge."})

(defn finding-for-reader []
  {:category :security-build
   :id "SEC-002"
   :severity :high
   :title "Unsafe Clojure reader on user input"
   :evidence "unsafe-read-user-form calls clojure.core/read-string."
   :fix "Use clojure.edn/read-string with schema validation and explicit size limits."})

(defn finding-for-shell []
  {:category :security-build
   :id "SEC-003"
   :severity :high
   :title "Shell command injection risk"
   :evidence "unsafe-run-diagnostic interpolates provider-id into a shell command."
   :fix "Call shell commands with fixed argv elements or replace the shell call with pure Clojure."})

(defn finding-for-dependencies []
  {:category :security-build
   :id "BUILD-001"
   :severity :medium
   :title "Stale dependency risk"
   :evidence stale-dependencies
   :fix "Open isolated dependency bump PRs and run unit, integration, and security scans."})

(defn finding-for-build-instability []
  {:category :security-build
   :id "BUILD-002"
   :severity :medium
   :title "Flaky build signals"
   :evidence (filter #(> (:failure-rate %) 0.05) build-signals)
   :fix "Quarantine flaky fixtures, remove wall-clock dependencies, and fail earlier on scan drift."})

(defn findings []
  [(finding-for-secret)
   (finding-for-reader)
   (finding-for-shell)
   (finding-for-dependencies)
   (finding-for-build-instability)])

(defn summarize-build-signals []
  (string/join
   ", "
   (map (fn [{:keys [job failure-rate]}]
          (str job "=" (format "%.0f%%" (* 100 failure-rate))))
        build-signals)))
