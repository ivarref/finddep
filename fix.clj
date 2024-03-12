(ns fix
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(def lines (->> (vec (str/split-lines (slurp "out.txt")))
                (filterv (fn [lin] (and (str/starts-with? lin "java.lang.NoSuchMethodException: ")
                                        (str/includes? lin ".<init>()"))))))

(defn add-to-config! [clazz]
  (let [json (->> (json/parse-string (slurp "reflect-config.json"))
                  (vec))
        json (conj json {"name"                    clazz
                         "allDeclaredConstructors" true})
        json (vec (sort-by (fn [x] (get x "name")) json))]
    (spit "reflect-config.json" (json/generate-string json {:pretty true}))))

(doseq [lin lines]
  (let [lin (subs lin (count "java.lang.NoSuchMethodException: "))
        lin (subs lin 0 (- (count lin)
                           (count ".<init>()")))]
    (println "adding" lin)
    (add-to-config! lin)))

(println "done fix!")
