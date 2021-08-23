
### Deploy kafka via strimzi:

[https://strimzi.io/docs/operators/latest/deploying.html#deploy-tasks-prereqs_str](https://strimzi.io/docs/operators/latest/deploying.html#deploy-tasks-prereqs_str)

Download yaml files and run:

`cd ~/Documents/facultate/kafka/strimzi-0.23.0`

`sed -i 's/namespace: .*/namespace: kafka/' install/cluster-operator/*RoleBinding*.yaml`

`kubectl create -f install/cluster-operator -n kafka`

`kubectl apply -f examples/kafka/kafka-ephemeral.yaml -n kafka`

Topics:  
`kubectl get -n kafka kafkatopic`

`kubectl delete kafkatopic cars -n kafka`

#### Deploy Kafka Connect

1.  Download mqtt connector and extract:
[https://github.com/lensesio/stream-reactor/releases/download/2.1.3/kafka-connect-mqtt-2.1.3-2.5.0-all.tar.gz](https://github.com/lensesio/stream-reactor/releases/download/2.1.3/kafka-connect-mqtt-2.1.3-2.5.0-all.tar.gz)

2.  Create dockerfile:
    
```dockerfile
FROM confluentinc/cp-kafka-connect:6.1.0 as cp
FROM strimzi/kafka:0.23.0-kafka-2.8.0
USER root:root
COPY ./kafka-connect-mqtt-2.1.3-2.5.0-all /opt/kafka/plugins/
COPY --from=cp /usr/share/java/kafka-connect-storage-common /opt/kafka/plugins/kafka-connect-storage-common
COPY --from=cp /usr/share/java/confluent-common /opt/kafka/plugins/confluent-common
RUN cd /opt/kafka/plugins && for plugin in kafka-connect-mqtt-2.1.3-2.5.0-all; do cd $plugin; ln -s ../confluent-common; ln -s ../kafka-connect-storage-common; cd ..; done
```
3.  Push image to docker registry:

`docker build -t kafka-connect-mqtt .`

`docker tag kafka-connect-mqtt nexus.esolutions.ro/copio/kafka-connect-mqtt`

`docker push nexus.esolutions.ro/copio/kafka-connect-mqtt`

4.  Apply yaml:
    
```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaConnect
metadata:
  name: my-connect-cluster
spec:
  version: 2.8.0
  image: nexus.esolutions.ro/copio/kafka-connect-mqtt
  replicas: 1
  bootstrapServers: my-cluster-kafka-bootstrap:9093
  tls:
    trustedCertificates:
      - secretName: my-cluster-cluster-ca-cert
        certificate: ca.crt
  config:
    group.id: connect-cluster
    offset.storage.topic: connect-cluster-offsets
    config.storage.topic: connect-cluster-configs
    status.storage.topic: connect-cluster-status
    # -1 means it will use the default replication factor configured in the broker
    config.storage.replication.factor: -1
    offset.storage.replication.factor: -1
    status.storage.replication.factor: -1
    key.converter: io.confluent.connect.avro.AvroConverter
    key.converter.schema.registry.url: http://registry-client:8081
    value.converter: io.confluent.connect.avro.AvroConverter
    value.converter.schema.registry.url: http://registry-client:8081      
  template:
    pod:
      imagePullSecrets:
        - name: nexus
```
`kubectl apply -f examples/connect/kafka-connect.yaml -n kafka`

5.  Check if the jars were loaded:
    
`curl -sS my-connect-cluster-connect-api:8083/connector-plugins`

6.  Load mqtt connector config via REST API
```sh
curl -s -X POST my-connect-cluster-connect-api:8083/connectors -H 'Content-Type: application/json' \
--data-binary @- << EOF
{
    "name": "mqtt-connector",
    "config": {
        "tasks.max": "1",
        "connector.class": "com.datamountaineer.streamreactor.connect.mqtt.source.MqttSourceConnector",
        "connect.mqtt.clean":true,
        "connect.mqtt.timeout":1000,
        "connect.mqtt.kcql":"INSERT INTO cars SELECT * FROM cars/+",
        "connect.mqtt.keep.alive":1000,
        "connect.mqtt.client.id":"kafka_connect_source_id",
        "connect.mqtt.converter.throw.on.error":true,
        "connect.mqtt.hosts":"tcp://emqx.kafka:1883",
        "connect.mqtt.service.quality":1
    }
}
EOF
```
7.  Check if the connector config was loaded
    
`curl -s -X GET -H 'Content-Type: application/json' http://my-connect-cluster-connect-api:8083/connectors`

8.  Delete connector config
    
`curl -s -X DELETE -H 'Content-Type: application/json' http://my-connect-cluster-connect-api:8083/connectors/mqtt-connector`
#### Deploy Schema Registry
```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: registry-client
  namespace: kafka
spec:
  ports:
  - port: 8081
  clusterIP: None
  selector:
    app: my-registry
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-cluster-schema-registry
  namespace: kafka
spec:
  selector:
    matchLabels:
      app: "my-registry"
  replicas: 1
  template:
    metadata:
      labels:
        app: my-registry
    spec:
      terminationGracePeriodSeconds: 10
      containers:
        - name: my-cluster-schema-registry
          image: confluentinc/cp-schema-registry 
          env:  # see https://docs.confluent.io/current/schema-registry/installation/config.html
            - name: SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS
              value: PLAINTEXT://my-cluster-kafka-bootstrap:9092
            - name: SCHEMA_REGISTRY_HOST_NAME
              valueFrom:
                fieldRef:
                  fieldPath: status.podIP
            - name: SCHEMA_REGISTRY_LISTENERS
              value: http://0.0.0.0:8081
            - name: SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL
              value: PLAINTEXT
          ports:
            - containerPort: 8081 
 ```

## Deploy EMQX

`helm install emqx emqx/emqx --namespace kafka`

Change prometheus pushgateway url via configMap:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  namespace: kafka
  name: emqx-prometheus
data:
  emqx_prometheus.conf: |-
    ##--------------------------------------------------------------------
    ## emqx_prometheus for EMQ X
    ##--------------------------------------------------------------------

    ## The Prometheus Push Gateway URL address
    ## Note: You can comment out this line to disable it
    prometheus.push.gateway.server = http://prometheus-pushgateway.prometheus:9091

    ## The metrics data push interval (millisecond)
    ## Default: 15000
    prometheus.interval = 10000
```

Add volume to deployment:
```yaml
volumeMounts:
        - mountPath: /opt/emqx/etc/plugins/emqx_prometheus.conf
          name: emqx-prometheus
          subPath: emqx_prometheus.conf
volumes:
- configMap:
    defaultMode: 420
    name: emqx-prometheus
  name: emqx-prometheus
```

 
Open emqx dashboard:

`kubectl port-forward -n kafka statefulset/emqx 18083:18083`

[http://localhost:18083/](http://localhost:18083/)

Add grafana templates from:

[https://github.com/emqx/emqx-prometheus/tree/master/grafana_template](https://github.com/emqx/emqx-prometheus/tree/master/grafana_template)

  

### Mosquitto

`apt-get install mosquitto-clients`

`mosquitto_pub -h emqx.kafka -p 1883 -t cars/car_id -m "test" -q 1`

`mosquitto_sub -h emqx.kafka -p 1883 -t cars/+ -q 1`

Check kafka messages:

`kubectl -n kafka run kafka-consumer -ti --image=quay.io/strimzi/kafka:0.23.0-kafka-2.8.0 --rm=true --restart=Never -- bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic cars --from-beginning`

  

### MZBench

`git clone git://github.com/mzbench/mzbench`

`cd mzbench`

Change py2-pip to py3-pip in Dockerfile and add RUN apk add make

Add this as last line in Dockerfile
```sh
CMD ($MZBENCH_API_DIR/bin/mzbench_api foreground &) && sleep 5 && curl -XPOST --form bench=@mzbench_mqtt.bdl http://localhost/start && sleep infinity
```
`docker build -t mzbench -f Dockerfile .`

Run container:

`docker run -d -p 4800:80 --name mzbench_server mzbench`

  
`kubectl port-forward -n kafka deployment/mzbench 4800:80`

Open  [http://localhost:4800](http://localhost:4800)

Add ConfigMap:
```sh
#!benchDL

make_install(git = "https://github.com/erlio/vmq_mzbench.git",
             branch = "master")

pool(size = 1000,
     worker_type = mqtt_worker,
     worker_start = poisson(1000 rps)):

            connect(
                        host = "emqx.kafka",
                        port = 1883,
                        client = random_client_id(15),
                        clean_session = true,
                        keepalive_interval = 60,
                        proto_version = 4,
                        reconnect_timeout = 30
    )

            set_signal(connect1, 1)
            wait_signal(connect1, 1000)
            wait(random_number(30) sec)
            loop(time = 60 min):
                    publish(sprintf("cars/~p", [random_client_id(15)]), random_binary(150), 1)
                    wait(random_number(100, 160) sec)
            wait(3600 sec)
            disconnect()
```

  

Start benchmark via API call:

`curl -XPOST --form bench=@mzbench_mqtt.bdl http://localhost/start`


Push to my docker hub (first do login):

`docker tag mzbench codrut11/mzbench:mzbench-codr`

`docker push codrut11/mzbench:mzbench-codr`

`kubectl port-forward -n kafka deployment/mzbench 4800:80`

  
JMeter

Create dockerfile:
```Dockerfile
FROM openjdk:11

ARG JMETER_VERSION="5.4.1"
ENV JMETER_HOME /opt/apache-jmeter-${JMETER_VERSION}
ENV JMETER_BIN    ${JMETER_HOME}/bin
ENV JMETER_DOWNLOAD_URL  https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz
ENV MQTT_PLUGIN_DOWNLOAD_URL  https://github.com/xmeter-net/mqtt-jmeter/releases/download/v2.0.2/mqtt-xmeter-2.0.2-jar-with-dependencies.jar

RUN mkdir -p /tmp/dependencies  \
    && curl -L --silent ${JMETER_DOWNLOAD_URL} >  /tmp/dependencies/apache-jmeter-${JMETER_VERSION}.tgz  \
    && mkdir -p /opt  \
    && tar -xzf /tmp/dependencies/apache-jmeter-${JMETER_VERSION}.tgz -C /opt  \
    && rm -rf /tmp/dependencies \
    && curl -L --silent ${MQTT_PLUGIN_DOWNLOAD_URL} > ${JMETER_HOME}/lib/ext/mqtt-xmeter-2.0.2-jar-with-dependencies.jar
    
# Set global PATH such that "jmeter" command is found
ENV PATH $PATH:$JMETER_BIN
WORKDIR ${JMETER_HOME}
CMD ["sleep","infinity"]
```

`docker tag jmeter codrut11/jmeter:jmeter-codr`

`docker push codrut11/jmeter:jmeter-codr`

  

Copy local test file to k8s volume:

`kubectl cp background_connection.jmx persistence:/persistence/jmeter`

Local Test:
```sh
JVM_ARGS="-Xms8g -Xmx8g" ./bin/jmeter -n -t background_connection.jmx -l jmeter_results -e -o web_report
```
K8s test:
```sh
JVM_ARGS="-Xms8g -Xmx8g" jmeter -Jserver=emqx.kafka -n -t /persistence/jmeter/background_connection.jmx
```
  

### Prometheus

Good version kube-prometheus-stack-16.1.2 0.48.0

`helm repo add prometheus-community https://prometheus-community.github.io/helm-charts`

`helm install prometheus-stack prometheus-community/kube-prometheus-stack --version=16.1.2 -n prometheus`

  

Install pushgateway - used by emqx

`helm install prometheus-pushgateway prometheus-community/prometheus-pushgateway -n prometheus`

Install kafka-exporter:

`helm install prometheus-kafka-exporter prometheus-community/prometheus-kafka-exporter --set kafkaServer[0]=my-cluster-kafka-bootstrap.kafka:9092 -n prometheus`

Add pushgateway and kafka-exporter to prometheus:

Create file prometheus-additional.yaml
```yaml
- job_name: 'pushgateway'
  scrape_interval: 5s
  honor_labels: true
  static_configs:
    # pushgateway fill in according to the actual situation
    - targets: ['prometheus-pushgateway.prometheus:9091']
- job_name: 'kafka_exporter'
  scrape_interval: 5s
  honor_labels: true
  static_configs:
    # pushgateway fill in according to the actual situation
    - targets: ['prometheus-kafka-exporter.prometheus:9308']
```

Create secret:

`kubectl create secret generic additional-configs --from-file=prometheus-additional.yaml -n prometheus`

Create file custom-values.yaml
```yaml
prometheus:
  prometheusSpec:
    retention: 6d
    additionalScrapeConfigsSecret:
      enabled: true
      name: additional-configs
      key: prometheus-additional.yaml
    storageSpec:
      volumeClaimTemplate:
        spec:
          storageClassName: longhorn
          accessModes: ["ReadWriteOnce"]
          resources:
            requests:
              storage: 15Gi
grafana:
  adminPassword: my-password
  grafana.ini:
    server:
      root_url: http://tarent-app.tarent.de/grafana
      domain: tarent-app.tarent.de
      serve_from_sub_path: true
```

Apply configs:

`helm upgrade -f custom-values.yaml prometheus-stack prometheus-community/kube-prometheus-stack --version=16.1.2 -n prometheus`
  
