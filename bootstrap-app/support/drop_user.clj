;;; run with lein exec -p drop_user.clj
(ns bootstrap-app.support.drop-user)
(require '[cmr.db :as db])

(try
  (db/drop-user)
  (finally
    (shutdown-agents)))
