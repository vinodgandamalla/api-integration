(ns cljs-starter.app
  (:require [reagent.core :as reagent :refer [atom render]]
            [sablono.core :as sab]
            [devcards.core :as dc]
            [cljs.test :as t :refer [report] :include-macros true]
            [devtools.core :as devtools]
            ;;[reforms.reagent :include-macros true :as f]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [fb-sdk-cljs.core :as fb]
            [cljsjs.chosen]
            [ajax.core :refer [GET POST
                               PUT DELETE]]
            ;;[cljsjs.facebook]
            )
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]
                   [cljs.test :refer [is testing async]]))

(enable-console-print!)

(defn init []
  #_(render  [:h1 "Hello, Clojurescript"]
             (.getElementById js/document "main-app-area"))
  (devtools/set-pref! :install-sanity-hints true)
  (devtools/install!)
  (devcards.core/start-devcard-ui!))



(defn simple-component []
  [:div
   [:p "I am a component!"]
   [:p.someclass
    "I have " [:strong "bold"]
    [:span {:style {:color "red"}} " and red "] "text."]])

(defcard simple-component
  (reagent/as-element [simple-component]))



(defn fblogin []
  ;; (fb/get-login-status
  ;;  (fn [response]
  ;;    (case (:status response)
  ;;      "connected"
  ;;      (fb/api "/me" {:fields "friends{email}"}
  ;;              println)
  ;;     ;; (.log js/console "connected")
  ;;      ;;else
  ;;      (fb/login #(.log js/console "else-" (str %)) {:scope "user_friends"})
  ;;      ))))


  (fb/ui {:method "share"
          :href "https://developers.facebook.com/docs/"}

         (fn [response]
           (if (= response [])
             (js/alert "Post was published")
             (js/alert "Post was not published.")))))




(defn facebook []
  (fb/load-sdk #(println "loaded"))
  
  (fb/init {:appId "1337857336279019"
          :status true
          :cookies true
          :xfbml true
          :version "v2.0"})

  [:button.btn.btn-primary {:on-click #(fblogin)} "fblogin"])

;; (defcard facebook
;;    (reagent/as-element [facebook]))

;;;;;;;;;;;;;;;;;;Gmail;;;;;;;;;;;;;;;;

(defn http-post1 [url data handler
                  error-handler
                  headers]
  (POST url
      {:params data
       :headers headers
       :handler handler
       :error-handler error-handler
       :format :json
       :response-format :json
       :keywords? true}))


(def server  "http://192.168.0.105:9000/")

(def state (atom {:user nil
                  :token nil
                  :personal-details-id nil
                  :avatar "../_ui/images/user-picture.jpg"
                  :page-location nil
                  :documents nil}))

(defn get-value! [k]
  (@state k))


(def results (atom []))

(def jquery (js* "$"))

(def sendingdata (atom nil))


(defn sendemails []
  (http-post1 (str server "")
              @sendingdata
              #(do
                 (reset! sendingdata [])
                 (js/console.log %))
              #(do
                 (reset! sendingdata [])
                 (js/console.log %))
              {:X-Auth-Token
               (get-value! :token)}
              ))


(defn add-sendingdata []
  (reset! sendingdata (js->clj (.val (jquery "#multiselect")))))

(defn multi-select []
  (.trigger (jquery "#multiselect") "chosen:updated")
  
  (.change (.chosen (jquery "#multiselect") (clj->js {:width "350px"}))
           #(add-sendingdata))
  (.chosen (jquery "#multiselect")))


(defn select-email [results]
  (reagent/create-class
   {:component-did-mount #(multi-select)
    :component-will-update #(multi-select)
    ;;:display-name  "my-component"
    :reagent-render
    (fn []
      [:div
       [:div.form-group
        [:div.row
         [:label.col-sm-3.control-label "Select Email-ids"]
         [:div.col-sm-4
          [:select {:id "multiselect"
                    :class "chosen-select form-control"
                    :multiple true}
           (for [d @results]
             [:option {:value d} d])
           ]]]
        ]])}))



(defn getemailaddress [resp]
  (swap! results conj (.-value (first(.-emailAddresses resp)
                                     ))))

(defn get-resource-information [resource]
  (.execute
   (.get js/gapi.client.people.people
         (clj->js {:resourceName (str resource)
                   :pageSize 10
                   }))
   getemailaddress
   ))



(defn list-of-resourcenames [resp]
  (loop [z (.-connections js/resp)
        result [] ]
    (if (empty? z)
       result 
      (recur (rest z) (conj result (str (.-resourceName  (first z))))
             ))))


 (defn list-of-emailaddresses [resp]
   (loop [i (list-of-resourcenames resp)
            output []]
       (if (empty? i)
         (js/console.log
          output)
         (recur (rest i) (conj output (get-resource-information (first i))))
         )))


(defn list-of-connections []
  (.execute
    (.list js/gapi.client.people.people.connections
           (clj->js {:resourceName "people/me"
                     :pageSize 500
                     }))
    list-of-emailaddresses
    ))


(defn loadpeopleapi []
  (.load js/gapi.client "https://people.googleapis.com/$discovery/rest" "v1"  list-of-connections))



(defn loadgmail []
  (.authorize js/gapi.auth
              (clj->js
               {
                :client_id "357222071570-9v41i7jde2551alippj0jgbdtt3npask.apps.googleusercontent.com"
                :scope ["https://www.googleapis.com/auth/contacts.readonly"]
                })loadpeopleapi))




(defn gmail-login []
  [:div#authorize
   [:button.btn.btn-primary
    {:on-click #(do (reset! results [])
                    (loadgmail))}
    "google login"]
   [:div  (str @results)]])


(defn multipleselect []
  [:div
   [gmail-login]
   [select-email results]
   (if @sendingdata
     [:button.btn.btn-primary {:on-click
                               #(do
                                  (sendemails)
                                  (reset! results []))} "send"])
   [:p (str @sendingdata)]])



;; (defcard multipleselect
;;   (reagent/as-element [multipleselect]))

;; (defcard Gmail
;;   (reagent/as-element [gmail-login]))



;;;;;;;;;;;Linked IN;;;;;;;;;;;;;;

(defn onlogout []
  (js/console.log "logged out from linked in"))


(defn onSuccess [data]
  (do
    (.logout js/IN.User  onlogout)
    (js/console.log data)))


(def payload
  {:comment "Check out developer.linkedin.com! http://linkd.in/1FC2PyG"
   :visibility {:code "anyone"}})

(defn share-content []
  (.result (.body (.method (.Raw js/IN.API  "/people/~/shares?format=json") "POST") (.stringify js/JSON (clj->js payload))) onSuccess))



(defn get-profile-data []
  (.result (.Raw js/IN.API  "/people/~") onSuccess))

(defn on-linkedin-load []
  (.on js/IN.Event js/IN "systemReady" get-profile-data))


(defn linkedin []
  (.authorize js/IN.User share-content "window"))


(defn linkedin-login []
  [:div
   [:button.btn.btn-primary
    {:on-click #(linkedin)} "linkedin login"]
   ])


;; (defcard linkedin
;;   (reagent/as-element [linkedin-login]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;hotmail;;;;;;;;;;;;;;;;


(defn hotmailinit []
  (.init js/WL (clj->js
                {:client_id "0f445520-aaf8-4512-9ee1-9dc76c499ca2"
                 :redirect_uri "http://localhost:3000"
                 :scope ["wl.basic" "wl.contacts_emails"]
                 :response_type "token"
                 })))


(def hotmailemails (atom []))

(defn response1 [response]
  (let [d (:data (-> response (js->clj :keywordize-keys true)))]
    (reset! hotmailemails (vec (for [m d] (get-in m [:emails :preferred]))))))


(defn response [response]
  (js/console.log   (str (->  response (js->clj :keywordize-keys true)))))

(defn hotmailapi []
  (.api js/WL  (clj->js {:path "me/contacts"
                :method "GET"})
        response1))


(defn hotmail-login []
  (.login js/WL (clj->js {:scope ["wl.basic" "wl.contacts_emails"]}
                         )
         hotmailapi))



(defn showatom []
  [:div
   ;; (filter (fn [x] (not (nil? x))) @hotmailemails)
   [:p (str @hotmailemails)]
   ])

;;;chosen for hotmail;;;;


(defn add-sendingdata-hot []
  (reset! sendingdata (js->clj (.val (jquery "#multiselect")))))

(defn multi-select-hot []
  (do
  (.trigger (jquery "#multiselect1") "chosen:updated")
  (.change (.chosen (jquery "#multiselect1"))
           #(add-sendingdata))
  (.chosen (jquery "#multiselect1"))))


(defn select-email-hot [results]
  (reagent/create-class
   {:component-did-mount #(multi-select-hot)
    :component-will-update #(multi-select-hot)
    ;;:display-name  "my-component"
    :reagent-render
    (fn []
      [:div
       [:div.form-group
        [:div.row
         [:label.col-sm-3.control-label "Select Email-ids"]
         [:div.col-sm-4
          [:select {:id "multiselect1"
                    :class "chosen-select form-control"
                    :multiple true}
           
           (for [d @results]
             ^{:key d}
             [:option {:value d} d])
           ]]
         [:div.col-sm-4
          [:select {:id "select"
                    :class " form-control"
                    ;; :multiple true
                    }
           (for [d @results]
             ^{:key d}
             [:option {:value d} d])
           ]]]
        ]])}))




(defn hotmail []
  (hotmailinit)
  [:div
   [:button.btn.btn-primary
    {:on-click #(hotmail-login)}
    "hotmail-login"]
   [showatom]
   [select-email-hot hotmailemails]
   ])



 (defcard hotmail-login
   (reagent/as-element [hotmail]))


