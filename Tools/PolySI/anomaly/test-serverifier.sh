for txt in /home/ubuntu/PolySI/anomaly/*.txt
do  
    echo ----------------- $txt
    java -jar ~/PolySI/build/libs/PolySI-1.0.0-SNAPSHOT.jar auditSER -t=TEXT $txt
done