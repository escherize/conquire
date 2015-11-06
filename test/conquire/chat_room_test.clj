(ns conquire.chat-room-test
  (:require [conquire.chat-room :refer :all]
            [clojure.test :refer :all]
            [schema.core :as s]))

(deftest app-validation-test
  (testing "theortiecal app passes validation"
    (let [room-id (gen-room-id)
          owner-id (gen-user-id)
          question-id (gen-question-id)
          app {room-id {:id room-id
                        :title "Startup Lecture"
                        :owner owner-id
                        :questions {question-id
                                    {:id question-id
                                     :text "why is sky blue?"
                                     :upvotes #{(gen-user-id)}}}}}]
      (is (= app (s/validate App app)))))
  (testing "Add a new room as a user."
    (let [room-id (gen-room-id)
          user-id (gen-user-id)
          title "Startup Lecture"
          app {}
          room-added-app {room-id
                          {:id room-id
                           :title title
                           :owner user-id
                           :questions {}}}]
      (is (= room-added-app
             (new-room app user-id title room-id)))))
  (testing "Add a new room as a user."
    (let [user-id (gen-user-id)
          title "Startup Lecture"
          app {}
          new-room-added (new-room app user-id title)
          auto-gen-room-id (-> new-room-added keys first)]
      (is (= new-room-added
             {auto-gen-room-id {:id auto-gen-room-id
                                :title title
                                :owner user-id
                                :questions {}}}))))
  (testing "Add an upvote as a user."
    (let [room-id (gen-room-id)
          owner-id (gen-user-id)
          some-user-id (gen-user-id)
          question-id (gen-question-id)
          app {room-id {:id room-id
                        :title "Startup Lecture"
                        :owner owner-id
                        :questions {question-id
                                    {:id question-id
                                     :text "why is sky blue?"
                                     :upvotes #{}}}}}
          upvoted-app {room-id
                       {:id room-id
                        :title "Startup Lecture"
                        :owner owner-id
                        :questions {question-id
                                    {:id question-id
                                     :text "why is sky blue?"
                                     :upvotes #{some-user-id}}}}}]
      (is (= upvoted-app
             (upvote app some-user-id room-id question-id)))))
  #_(testing "Ask question."
      (let [owner-id (gen-user-id)
            user-id (gen-user-id)
            room-id (gen-room-id)
            question-id (gen-question-id)
            question-text "Earth is not round, right?"
            app {room-id {:id room-id
                          :title "Best room to ask a question in."
                          :owner owner-id
                          :questions {}}}]
        (is (= {room-id {:id room-id
                         :title "Best room to ask a question in."
                         :owner owner-id
                         :questions {question-id
                                     {:id question-id
                                      :text question-text
                                      :upvotes #{}}}}}
               (ask-question app user-id room-id question-text question-id))))))
