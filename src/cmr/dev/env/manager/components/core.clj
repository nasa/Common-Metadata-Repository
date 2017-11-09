(ns cmr.dev.env.manager.components.core
  "System component access functions.")

(defn get-config
  ""
  [system config-key & args]
  (let [base-keys [:config config-key]]
    (if-not (seq args)
      (get-in system base-keys)
      (get-in system (concat base-keys args)))))
