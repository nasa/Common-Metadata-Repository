(ns cmr.dev.env.manager.watcher.core
  (:require
    [cmr.dev.env.manager.watcher.impl.darwin :as darwin]
    [cmr.dev.env.manager.watcher.impl.hawk :as hawk]
    [cmr.dev.env.manager.watcher.impl.linux :as linux]
    [cmr.dev.env.manager.watcher.impl.nio :as nio]))

(defprotocol Watcher
  (add [this type coll])
  (add-dir [this dir])
  (add-dirs [this dirs])
  (add-file [this file])
  (add-files [this files]))
