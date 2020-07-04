(ns org.sysfoundry.http.util)



(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})


(def ok       (partial response 200))
(def created  (partial response 201))
(def accepted (partial response 202))
(def not-found (partial response 404))