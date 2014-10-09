(ns common-viz.util
  (:require [vdd-core.core :as vdd]
            [vdd-core.internal.project-viz :as project-viz]
            [coffee-script.core :as coffee]
            [clojure.java.io :as io]
            [clojure.string :as s]))


(defn- coffeescript-files
  "Finds all the coffeescript files in subdirectories"
  [path]
  (->> path
       io/file
       file-seq
       (map str)
       (filter #(.endsWith ^String % ".coffee"))))

(defn- compile-coffeescript-file
  "Compiles a single coffeescript file from blah.coffee to blah.js."
  [path output-path]
  (io/make-parents output-path)
  (let [js (coffee/compile-coffee (slurp path))]
    (spit output-path js)
    output-path))

(defn compile-coffeescript
  "Compiles all the coffeescript files in the visualization."
  [config]
  (let [visualization-paths (map :path (project-viz/project-visualizations config))
        visualization-paths (conj visualization-paths "viz/common")]
    (doall (for [visualization-path visualization-paths
                 coffeescript-file (coffeescript-files visualization-path)]
             (let [compiled-path (-> coffeescript-file
                                     (s/replace visualization-path (str visualization-path "/compiled"))
                                     (s/replace ".coffee" ".js"))]
               (compile-coffeescript-file coffeescript-file compiled-path))))))
