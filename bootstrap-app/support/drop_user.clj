;;; run with lein exec -p drop_user.clj
(require '[cmr.db :as db])

(try
  (db/drop-user)
  (finally
    (shutdown-agents)))
