;;; run with lein exec -p create_user.clj
(require '[cmr.db :as db])

(try
  (db/create-user)
  (finally
    (shutdown-agents)))