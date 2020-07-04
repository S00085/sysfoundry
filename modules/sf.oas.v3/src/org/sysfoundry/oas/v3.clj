(ns org.sysfoundry.oas.v3
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.data :as jdata]
            [taoensso.timbre :as log]
            [reitit.ring :as ring]
            [integrant.core :as ig]
            )
  (:import (com.reprezen.kaizen.oasparser OpenApi3Parser))
  )

(def swagger-ui-resource-root "META-INF/resources/webjars/swagger-ui/3.27.0")

(defn oas-key-fn [k]
  (if (string? k)
    (if (clojure.string/includes? k "/")
      k
      (try
        (let [int-val (Integer. k)]
          int-val)
        (catch NumberFormatException e (keyword k))))
    k))


(defn open-api-model
  [file-name validate?]
  (let [open-api-parser (OpenApi3Parser.)
        open-api-model (.parse open-api-parser (io/resource file-name) validate?)
        valid? (.isValid open-api-model)
        open-api-spec-json (.toString open-api-model)
        open-api-spec-edn (try 
                            (json/read-str open-api-spec-json :key-fn oas-key-fn)
                            (catch Exception e {:error (.getMessage e) :exception e}))
        response-map {:valid? valid?
                      :oas3/edn open-api-spec-edn
                      :oas3/json open-api-spec-json}
        ]
    
    (if-not valid?
      (assoc response-map :oas3/validation-results (-> (.getValidationResults open-api-model)
                                                       jdata/from-java))
      response-map)
    )
  )



(defn get-method-handler [method method-map]
  (let [operation-id (get-in method-map [method :operationId] :none)
        resolved-handler (resolve (symbol operation-id))
        ]
    (if-not (= :none operation-id)
      (if (nil? resolved-handler)
        (throw (ex-info (str "Operation Id : '" operation-id "' could not be resolved to a handler function!") {:operationId operation-id}))
        {:handler resolved-handler})
      (throw (ex-info (str "No operationId defined for method " method ", or no method found!") {:method method})))
    )
  )

(defn get-route-data [path method-map]
  (let [methods (keys method-map)]
    (loop [method (first methods) remaining-methods (rest methods)
           final-method-map (transient {})
           ]
      (if-not (nil? method)
        (let [method-handler (try (get-method-handler method method-map)
                                  (catch Exception e
                                    {:error (.getMessage e)
                                     :data (ex-data e)}))]
          (recur (first remaining-methods) (rest remaining-methods) (assoc! final-method-map method method-handler)))
        [path (persistent! final-method-map)])
      )
    )
  )

(defn filter-invalid-methods [path-route-data]
  (let [path (first path-route-data)
        method-map (second path-route-data)
        valid-methods (into {} (filter #(contains? (second %) :handler) method-map))
        ]
    (if-not (empty? valid-methods)
      [path valid-methods]
      nil
      )
    )
  )

(defn get-apidoc-route [api-model apidoc-endpoint-str]
  (let [api-spec-json (:oas3/json api-model)]
    [apidoc-endpoint-str (fn [req]
                           {:status 200 :headers {"Content-Type" "application/json"}
                            :body api-spec-json
                            }
                           )]
    )
  )

;["/swagger-ui/*" (ring/routes (ring/create-resource-handler {:root "public" :not-found-handler swagger-ui-resource-handler}))]

(defn get-swagger-ui-route [swagger-ui-endpoint-str]
 (let [swagger-ui-resource-handler (ring/create-resource-handler {:root swagger-ui-resource-root})
       swagger-ui-overlay-resource-handler (ring/create-resource-handler {:root "public" :not-found-handler swagger-ui-resource-handler})]
   [swagger-ui-endpoint-str (ring/routes swagger-ui-overlay-resource-handler)]
   ) 
  )

(defn get-routes
  ([api-spec-model]
   (let [api-model (:oas3/edn api-spec-model)]
     (->> (keys (:paths api-model))
          (map #(get-route-data % (get-in api-model [:paths %])))
          (map #(filter-invalid-methods %))
          (filter #(do %)) ;filter nils
          vec)))
  ([api-spec-model apidoc-endpoint-str]
   (conj (get-routes api-spec-model)
         (get-apidoc-route api-spec-model apidoc-endpoint-str)
         )
   )
  ([api-spec-model apidoc-endpoint-str swagger-ui-path]
   (conj (get-routes api-spec-model apidoc-endpoint-str)
         (get-swagger-ui-route swagger-ui-path)))
  )

(def default-service-spec {:spec-resource-name "api-spec.yaml"
                           :validate? true
                           :apidoc-endpoint "/apidoc/swagger.json"
                           :swagger-ui-path "/swagger-ui/*"
                           })

(defn- check-resource [resource-name]
  (let [resource-url (io/resource resource-name)]
    (if-not (nil? resource-url)
      resource-name
      (throw (ex-info (str "unable to find resource : " resource-name)
                      {:resource-name resource-name}
                      ))
      )
    )
  )
(defmethod ig/init-key ::request-handler [_ config]
  (let [final-service-config (merge default-service-spec config)
        apidoc-endpoint (:apidoc-endpoint final-service-config)
        swagger-ui-path (:swagger-ui-path final-service-config)
        api-spec-model (open-api-model (check-resource (:spec-resource-name final-service-config))
                                       (:validate? final-service-config))
        routes (get-routes api-spec-model apidoc-endpoint swagger-ui-path)
        ]
    (log/debug "Routes -> " routes)
    (ring/ring-handler (ring/router routes) 
                       (ring/create-default-handler))
    )
  )