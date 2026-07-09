(ns cmr.audit-simulator.runner
  "Aggregates all simulated audit findings into one report."
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [cmr.audit-simulator.api.routes :as routes]
   [cmr.audit-simulator.architecture :as architecture]
   [cmr.audit-simulator.metadata :as metadata]
   [cmr.audit-simulator.reliability :as reliability]
   [cmr.audit-simulator.security-build :as security-build]))

(def severity-rank
  {:critical 4
   :high 3
   :medium 2
   :low 1})

(defn all-findings []
  (sort-by (comp - severity-rank :severity)
           (concat (security-build/findings)
                   (architecture/findings)
                   (metadata/findings)
                   (reliability/findings))))

(defn report []
  {:app "cmr-audit-simulator"
   :root-directory "cmr-audit-simulator"
   :purpose "Safe, intentionally flawed audit target for CMR auditor development."
   :paths {:source "cmr-audit-simulator/src"
           :resources "cmr-audit-simulator/resources"
           :tests "cmr-audit-simulator/test"}
   :summary {:finding-count (count (all-findings))
             :categories [:security-build :architecture :metadata :reliability]}
   :routes (:routes (routes/route-report))
   :findings (vec (all-findings))})

(defn -main
  [& _args]
  (println (json/generate-string (report) {:pretty true})))
