(ns cmr.transmit.test.echo.tokens-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3 is-jwt-token?]]
   [cmr.transmit.echo.tokens :as tokens])
  (:import
   clojure.lang.ExceptionInfo
   java.lang.Exception))

(def bad-gateway-body "<html>\r\n<head><title>504 Gateway Time-out</title></head>\r\n<body>\r\n<center><h1>504 Gateway Time-out</h1></center>\r\n</body>\r\n</html>\r\n")

(def upstream-server-error-body "<!DOCTYPE html>\n<html>\n<head>\n  <title>We're sorry, but something went wrong (500)</title>\n  <style type=\"text/css\">\n    body { background-color: #fff; color: #666; text-align: center; font-family: arial, sans-serif; }\n    div.dialog {\n      width: 25em;\n      padding: 0 4em;\n      margin: 4em auto 0 auto;\n      border: 1px solid #ccc;\n      border-right-color: #999;\n      border-bottom-color: #999;\n    }\n    h1 { font-size: 100%; color: #f00; line-height: 1.5em; }\n  </style>\n</head>\n\n<body>\n  <!-- This file lives in public/500.html -->\n  <div class=\"dialog\">\n    <h1>We're sorry, but something went wrong.</h1>\n    <p>We've been notified about this issue and we'll take a look at it shortly.</p>\n  </div>\n</body>\n</html>")

(deftest handle-get-user-id-test
  (are3 [status response ex-type msg-fragment]
    (is (thrown-with-msg?
         ex-type
         (re-pattern msg-fragment)
         (tokens/handle-get-user-id "foo-token" status nil response)))

    "504 Gateway Timeout"
    504 bad-gateway-body ExceptionInfo "gateway timeout"

    "General server error"
    500 upstream-server-error-body Exception "We're sorry"))

(def expired-token "eyJ0eXAiOiJKV1QiLCJvcmlnaW4iOiJFYXJ0aGRhdGEgTG9naW4iLCJzaWciOiJlZGxqd3RwdWJrZXlfZGV2ZWxvcG1lbnQiLCJhbGciOiJSUzI1NiJ9.eyJ0eXBlIjoiVXNlciIsImNsaWVudF9pZCI6InRlc3RjbGllbnQiLCJleHAiOjE2NjMwMTY0NzcsImlhdCI6MTY2MzAxNjQ2NywiaXNzIjoiRWFydGhkYXRhIExvZ2luIiwidWlkIjoibWl0Y2h0ZXN0In0.Z5RihLpC5WrqowZEhk0pifBWmW9vmGJcfK571MiK4_VSQb0jXsEqh1-ULsGvOLTbFWrQ97KOqjkPuyQSbXY20OIVwXUMGRcdEqoMGluuM_D-qBFrUbNuuJoAtcBWi71ubebiSF5ilLedcit79ZK0KSlGo78rjj0Y8IJtV08BBe86ZjLvMdIN8VAj-V1F69Op_nYlGJyxXHuCk0MOYC1K3dM8YNe61QX75RZvMMGxSAginMlxpeFHakKDozIlxf2xoBrxoF9NlK9-rg6eoC4R9vxt-qvFHrHX2F03iRaSNi2U-RKifC2uf07eST7TOJbEncuul400yJDze6aQTVA50F8jZfZzgzN1uUjIEkx_VE1urPKG6zKcV7LaIRD4CQgGmBXgO_-jkbVQxJPQpMcF1o_iQrjyd7RFOzn9kU_JPCZT878L60HI94As-6MrQqef_CL0UnLIsdIEDtcRigUdha0D39mzuUkATYGEI6pPWWN23wjZbJyb8ZpGOAN5RWXYyXIx8_azEZ8ADg-KqGbq9Y2s6BoU_NWLL6Yrw6vKoRT6fZkqI-VWGuPjDaNRdv_sEhgpseRhm1mC3mBXivKXVIYgRNeT15FOCX9x7J6gFVcYBN8z44awQUikwKxyl4m80b4k2q7iwIRTlFqJV0Kl6rT4WTgQnyzxUoCPE2iespk")

(def not-expired-token "eyJ0eXAiOiJKV1QiLCJvcmlnaW4iOiJFYXJ0aGRhdGEgTG9naW4iLCJzaWciOiJlZGxqd3RwdWJrZXlfZGV2ZWxvcG1lbnQiLCJhbGciOiJSUzI1NiJ9.eyJ0eXBlIjoiVXNlciIsImNsaWVudF9pZCI6ImxvY2FsZGV2IiwiZXhwIjoxMTY2MTU0NzA5NCwiaWF0IjoxNjYxNTQ3MDk0LCJpc3MiOiJFYXJ0aGRhdGEgTG9naW4iLCJ1aWQiOiJtc3RhcnR6ZWx0ZXN0ZXIifQ.tAA4zu2K3NFJVX96_M1qb1lrDQ1l7uMjMWNE5Jkxw16RnHTgIt6dcEdX-YcXUbJqKDU39RLcB_O8hgzd9e7pHBdxrwhjl4LnuNcSj1XZGTLh-RD5VW1jRkQe_pcH1noniUqkXaYuVGUybnbhDZa_37Oxs-vr0lT06Qk82elV5Y1dq_YLQeABv4O0BiktpPoCSjSIfTW6jEUS-ONk07r5N5O_Me7H-QbPhEtiax1N3zcTRWyNn7lM6vQxl7d-ywRKqQaeA9Iy-ufmGXoczLvvN4HsaKbVhQ_llw6Xnj7cKpd4WJ6VABDETlMlcjwtyvSt-q3aToy9N4_EkMGQbxkbsQrvf9LI2VM7J799uhW9E4VvOEl-CafkFnOgjo1nvMpq3fZq1zIfG4eA6UrYpQQz_gcdFfoL-p5ZI_BbMO0PK_8XAfE8O0w7b7i7QJ_EmKKUA2QibJLK8qdlOhbLNu6ORTyqvxbawMAjW_ZzJIZnDwjyuIoJNBFJQxiz2SMBdQwAuJDGcyzIGEAheF0ffB-mJG28HyVvhbjQhP2ByE0mZoDFhqmgk47FnQNFL7mTdtSbI-KvOXb3rBEaELUdWDuqjgnOxQehJzFlbqETRZfZDuEUq7q1Zl227k2178lvVVPQuco8Auo180qVaJcAs9Fd2k-i6oNkalC6MNjgmEpBUSE")

(def invalid-jwt-token "eyJ0eXAiOiJKV1QiLCJvcmlnaW4iOiJFYXJ0aGRhdGEgTG9naW4iLCJzaWciOiJlZGxqd3RwdWJrZXlfZGV2ZWxvcG1lbnQiLCJhbGciOiJSUzI1NiJ9.eyJ0eXBlIjoiVXNlciIsImNsaWVudF9pZCI6ImxvY2FsZGV2IiwiZXhwIjoxMTY2MTU0NzA5NCwiaWF0IjoxNjYxNTQ3MDk0LCJpc3MiOiJFYXJ0aGRhdGEgTG9naW4iLCJ1aWQiOiJtc3RhcnR6ZWx0ZXN0ZXIifQ.tAA4zu2K3NFJVX96_M1qb1lrDQ1l7uMjMWNE5Jkxw16RnHTgIt6dcEdX-YcXUbJqKDU39RLcB_O8hgzd9e7pHBdxrwhjl4LnuNcSj1XZGTLh-RD5VW1jRkQe_pcH1noniUqkXaYuVGUybnbhDZa_37Oxs-vr0lT06Qk82elV5Y1dq_YLQeABv4O0BiktpPoCSjSIfTW6jEUS-ONk07r5N5O_Me7H-QbPhEtiax1N3zcTRWyNn7lM6vQxl7d-ywRKqQaeA9Iy-ufmGXoczLvvN4HsaKbVhQ_llw6Xnj7cKpd4WJ6VABDETlMlcjwtyvSt-q3aToy9N4_EkMGQbxkbsQrvf9LI2VM7J799uhW9E4VvOEl-CafkFnOgjo1nvMpq3fZq1zIfG4eA6UrYpQQz_gcdFfoL-p5ZI_BbMO0PK_8XAfE8O0w7b7i7QJ_EmKKUA2QibJLK8qdlOhbLNu6ORTyqvxbawMAjW_ZzJIZnDwjyuIoJNBFJQxiz2SMBdQwAuJDGcyzIGEAheF0ffB-mJG28HyVvhbjQhP2ByE0mZoDFhqmgk47FnQNFL7mTdtSbI-KvOXb3rBEaELUdWDuqjgnOxQehJzFlbqETRZfZDuEUq7q1Zl227k2178lvVVPQuco8Auo180qVaJcAs9Fd2k-i6oNkalC6MNjgmEpBooo")

(def invalid-token "invalid-token")

(deftest validating-jwt-token-locally-test
  (testing "Validating jwt tokens locally with"
    (testing "expired token"
      (is (thrown-with-msg?
           ExceptionInfo 
           (re-pattern "has expired.")
           (tokens/verify-edl-token-locally expired-token))))
    (testing "invalid jwt token"
      (is (thrown-with-msg?
           ExceptionInfo 
           (re-pattern "does not exist")
           (tokens/verify-edl-token-locally invalid-jwt-token))))
    (testing "invalid token/unknown error"
      (is (thrown-with-msg?
           Exception 
           (re-pattern "Unexpected error")
           (tokens/verify-edl-token-locally invalid-token))))
    (testing "valid token"
      (is (= "mstartzeltester" (tokens/verify-edl-token-locally not-expired-token)))))

  (testing "is-jwt-token? tests with"
    (testing "valid jwt token"
      (is (= true (is-jwt-token? expired-token))))
    (testing "invalid jwt token"
      (is (= true (is-jwt-token? invalid-jwt-token))))
    (testing "non jwt invalid token"
      (is (= false (is-jwt-token? invalid-token))))
    (testing "valid token"
      (is (= true (is-jwt-token? not-expired-token))))))
