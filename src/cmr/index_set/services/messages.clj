(ns cmr.index-set.services.messages
  "Provides a map between crud ops and error message formats"
  (:require [clojure.string :as string]
            [cmr.common.services.errors :as errors]))

(def err-msg-fmts
  {:get {:index-set-not-found "index-set with id: %s not found"}
   :create {:invalid-id "id: %s not a positive integer, index set: %s"
            :missing-id-name "missing id or name in index-set: %s"
            :missing-idx-cfg "missing index names or settings or mapping in given index-set: %s"
            :index-set-exists "index-set id: %s already exists"
            :missing-idx "index-sets index does not exist in elastic"
            :invalid-index-set "invalid index-set: %s"
            :fail "failed to create index-set: %s; root cause: %s"}
   :delete {:index-fail "index delete operation failed - elastic response: %s"
            :doc-fail "index-set doc delete operation failed - elastic reponse; %s"}})


