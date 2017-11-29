(ns cmr.dev.env.manager.process.info
  (:require
    [clojure.string :as string]
    [cmr.dev.env.manager.process.util :as util]
    [me.raynes.conch.low-level :as shell]
    [taoensso.timbre :as log]))

(defn output-format->keys
  [output-fields]
  (->> #","
       (string/split output-fields)
       (mapv (comp keyword string/trim))))

(defn parse-output-line
  [output-format output-line]
  (case output-format
    "pid,ppid,pgid,comm"
    (let [[pid ppid pgid & cmd] (string/split (string/trim output-line) #"\s+")]
      (conj
        (mapv util/str->int [pid ppid pgid])
        (string/join " " cmd)))))

(defn output-line->map
  [output-format output-line]
  (zipmap (output-format->keys output-format)
          (parse-output-line output-format output-line)))

(defn output-lines->ps-info
  [output-format output-lines]
  (map (partial output-line->map output-format) output-lines))

(defn get-children
  [ps-info acc ps-parents]
  (if (seq ps-parents)
    (for [pid ps-parents]
      (let [child-pids (mapv :pid (filter #(= (:ppid %) pid) ps-info))]
        (get-children ps-info (concat acc child-pids) child-pids)))
    acc))
