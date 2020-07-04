(ns org.sysfoundry.dc.namespace
  (:require [malli.core :as m]
            [malli.error :as me]
            [org.sysfoundry.gen.comms :as c]
            )
  )

(defn err-msg-for-strings
  [{:keys [value]} _]
  (str value ", is not a valid string of min length 1 character")
  )

;;malli spec for namespace
;;
(def namespace-schema
  [:map 
   {:title "namespace"
    :description "Decision center's namespace"
    :json-schema/example {::name "test.ns" ::doc "Test ns doc blah blah" ::active true}}
   [::name [:string {:min 1 :max 250}]]
   [::doc [:string {:min 1 :max 500}]]
   [::active boolean?]
   ]
  )

;;malli spec used for APIs temporarily till reitit supports 
;;the above spec version
(def ns-schema
 [:map
  [::name string?]
  [::doc string?]
  [::active boolean?]]
  )

(comment (def valid?
           (m/validator namespace-schema)))

(defn validate 
  "Validate whether the provided namespace definition is as per the namespace schema. 
   Success or Failure is communicated with the standard comms approach "
  [namespace-def]
  (let [result (some->> namespace-def
                       (m/explain namespace-schema)
                       me/humanize
                       )]
    (if (nil? result)
      ;;the validation was successful
      (c/success nil)
      ;;the validation was a failure
      (c/failure result)
      )
    )
  )

;;NamespaceRepo protocol definition
;;
(defprotocol NamespaceRepo
  "A protocol which provides the abstraction of a Namespace Repository"
  (list-namespaces [this account query order-by page-info] "Lists the all the namespaces in this repository matching the given query, order-by and page-info. 
                                                            this, Account and Query are mandatory parameters")
  (create-namespaces [this account namespace-defs] "Creates the namespaces for the given account. 
                                                    This method is transacational and will either create or return the errors if any without creating the others.")
  (update-namespaces [this account update-map query] "Updates the namespaces matching the given query with the values in the update-map")
  (delete-namespaces [this account query] "Deletes the namespaces matching the query")
  )