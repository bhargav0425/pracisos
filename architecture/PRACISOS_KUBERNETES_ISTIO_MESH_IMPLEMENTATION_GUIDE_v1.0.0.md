# PRACISOS PLATFORM
## KUBERNETES & ISTIO MESH IMPLEMENTATION GUIDE
### Phase 6: Service Mesh, mTLS, Traffic Management, Observability Stack

---

## DOCUMENT CONTROL

| Field | Value |
|-------|-------|
| **Version** | 1.0.0 |
| **Status** | READY FOR IMPLEMENTATION |
| **Date** | 2026-06-27 |
| **Phase** | 6 of 7 |
| **Scope** | Kind Cluster, Istio Service Mesh, mTLS STRICT, Helm Charts, K8s Manifests, Observability |
| **Prerequisite** | All services (Auth, Booking, Charting, Billing) implemented and Dockerized |

---

## 1. GOAL

Deploy the entire Pracisos platform onto a local Kubernetes cluster with Istio service mesh that enables:
- **Kind (Kubernetes in Docker)** for local development cluster
- **Istio** with STRICT mTLS on all service-to-service communication
- **Ingress Gateway** for external traffic with TLS termination
- **VirtualService** for intelligent routing (canary, A/B testing ready)
- **AuthorizationPolicy** for service-to-service RBAC (zero-trust)
- **Helm charts** for repeatable, parameterized deployments
- **Observability stack**: Prometheus (metrics), Grafana (dashboards), Jaeger (tracing), Loki (logs)
- **End-to-end validation** of all flows within the mesh

**Validation:** `kubectl apply` all manifests -> all pods healthy -> mTLS verified -> traces visible in Jaeger -> metrics in Grafana.

---

## 2. WHAT YOU WILL BUILD

### 2.1 Infrastructure

```
k8s/
├── kind-config.yaml              # Kind cluster configuration
├── istio/
│   ├── install-istio.sh          # Istio installation script
│   ├── gateway.yaml              # Ingress Gateway
│   ├── peer-authentication.yaml  # STRICT mTLS
│   └── telemetry.yaml            # Metrics/tracing config
├── helm/
│   └── pracisos-service/
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
│           ├── _helpers.tpl
│           ├── deployment.yaml
│           ├── service.yaml
│           ├── configmap.yaml
│           ├── hpa.yaml
│           ├── istio-gateway.yaml
│           ├── istio-virtualservice.yaml
│           ├── istio-destinationrule.yaml
│           ├── istio-authorizationpolicy.yaml
│           └── serviceaccount.yaml
├── monitoring/
│   ├── prometheus/
│   │   ├── prometheus-config.yaml
│   │   └── prometheus-deployment.yaml
│   ├── grafana/
│   │   ├── grafana-deployment.yaml
│   │   └── dashboards/
│   │       ├── platform-health.json
│   │       ├── api-performance.json
│   │       └── clinic-operations.json
│   └── jaeger/
│       └── jaeger-deployment.yaml
└── scripts/
    ├── deploy-all.sh
    ├── verify-mtls.sh
    └── port-forward.sh
```

### 2.2 Application Deployment

```
apps/
├── auth-service/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
├── booking-service/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
├── charting-service/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
├── billing-service/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
├── frontend/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
├── postgres/
│   ├── statefulset.yaml
│   ├── service.yaml
│   └── pvc.yaml
├── redis/
│   ├── deployment.yaml
│   └── service.yaml
└── kafka/
    ├── statefulset.yaml
    ├── service.yaml
    └── configmap.yaml
```

---

## 3. KIND CLUSTER SETUP

### 3.1 kind-config.yaml

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: pracisos
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      # Istio Ingress Gateway ports
      - containerPort: 80
        hostPort: 8080
        protocol: TCP
      - containerPort: 443
        hostPort: 8443
        protocol: TCP
      # Grafana
      - containerPort: 30000
        hostPort: 30000
        protocol: TCP
      # Prometheus
      - containerPort: 30001
        hostPort: 30001
        protocol: TCP
      # Jaeger
      - containerPort: 30002
        hostPort: 30002
        protocol: TCP
      # Kafka (for debugging)
      - containerPort: 30003
        hostPort: 30003
        protocol: TCP
  - role: worker
  - role: worker
```

### 3.2 Cluster Creation Script

```bash
#!/bin/bash
# scripts/create-cluster.sh

set -e

echo "Creating Kind cluster..."
kind create cluster --config k8s/kind-config.yaml

echo "Waiting for cluster to be ready..."
kubectl wait --for=condition=Ready nodes --all --timeout=120s

echo "Cluster ready!"
echo "Nodes:"
kubectl get nodes
```

---

## 4. ISTIO INSTALLATION

### 4.1 install-istio.sh

```bash
#!/bin/bash
# k8s/istio/install-istio.sh

set -e

ISTIO_VERSION="1.22.0"

echo "Downloading Istio ${ISTIO_VERSION}..."
curl -L https://istio.io/downloadIstio | ISTIO_VERSION=${ISTIO_VERSION} sh -

export PATH="$PWD/istio-${ISTIO_VERSION}/bin:$PATH"

echo "Installing Istio with default profile..."
istioctl install --set profile=default -y

echo "Enabling sidecar injection on pracisos namespace..."
kubectl create namespace pracisos --dry-run=client -o yaml | kubectl apply -f -
kubectl label namespace pracisos istio-injection=enabled --overwrite

echo "Verifying Istio installation..."
kubectl get pods -n istio-system

echo "Istio installation complete!"
```

### 4.2 PeerAuthentication (STRICT mTLS)

```yaml
# k8s/istio/peer-authentication.yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: pracisos
spec:
  mtls:
    mode: STRICT
---
# Also enforce mTLS for the entire mesh
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: istio-system
spec:
  mtls:
    mode: STRICT
```

### 4.3 Telemetry Configuration

```yaml
# k8s/istio/telemetry.yaml
apiVersion: telemetry.istio.io/v1alpha1
kind: Telemetry
metadata:
  name: mesh-default
  namespace: istio-system
spec:
  metrics:
    - providers:
        - name: prometheus
      overrides:
        - match:
            metric: ALL_METRICS
          tagOverrides:
            request_protocol:
              operation: UPSERT
              value: "%REQUEST_PROTOCOL%"
  accessLogging:
    - providers:
        - name: envoy
      filter:
        expression: "response.code >= 400"
  tracing:
    - providers:
        - name: jaeger
      randomSamplingPercentage: 100.0
```

### 4.4 Ingress Gateway

```yaml
# k8s/istio/gateway.yaml
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: pracisos-gateway
  namespace: pracisos
spec:
  selector:
    istio: ingressgateway
  servers:
    - port:
        number: 80
        name: http
        protocol: HTTP
      hosts:
        - "*"
      # Redirect HTTP to HTTPS in production
      # tls:
      #   httpsRedirect: true
    - port:
        number: 443
        name: https
        protocol: HTTPS
      hosts:
        - "*"
      tls:
        mode: SIMPLE
        credentialName: pracisos-tls-secret
```

---

## 5. HELM CHART: REUSABLE SERVICE TEMPLATE

### 5.1 Chart.yaml

```yaml
# k8s/helm/pracisos-service/Chart.yaml
apiVersion: v2
name: pracisos-service
description: A reusable Helm chart for Pracisos microservices
type: application
version: 1.0.0
appVersion: "1.0.0"
```

### 5.2 values.yaml (Defaults)

```yaml
# k8s/helm/pracisos-service/values.yaml
nameOverride: ""
fullnameOverride: ""

replicaCount: 2

image:
  repository: ""
  pullPolicy: IfNotPresent
  tag: "latest"

serviceAccount:
  create: true
  annotations: {}
  name: ""

podAnnotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/actuator/prometheus"

securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000

service:
  type: ClusterIP
  port: 8080
  targetPort: 8080

resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 250m
    memory: 512Mi

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 5
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80

env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: SERVER_PORT
    value: "8080"
  - name: MANAGEMENT_SERVER_PORT
    value: "8081"

envFromSecrets: []
#  - name: DB_PASSWORD
#    secretName: postgres-auth
#    secretKey: password

configMap:
  enabled: true
  data: {}

istio:
  enabled: true
  gateway: pracisos-gateway
  host: "*"
  pathPrefix: ""
  timeout: 30s
  retries:
    attempts: 3
    perTryTimeout: 10s
    retryOn: gateway-error,connect-failure,refused-stream
  circuitBreaker:
    consecutiveErrors: 5
    interval: 30s
    baseEjectionTime: 30s
  authorization:
    enabled: true
    allowedServices: []

probes:
  liveness:
    path: /actuator/health/liveness
    initialDelaySeconds: 60
    periodSeconds: 30
  readiness:
    path: /actuator/health/readiness
    initialDelaySeconds: 30
    periodSeconds: 10

nodeSelector: {}
tolerations: []
affinity: {}
```

### 5.3 _helpers.tpl

```yaml
{{/* k8s/helm/pracisos-service/templates/_helpers.tpl */}}
{{/* Expand the name of the chart */}}
{{- define "pracisos-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Create a default fully qualified app name */}}
{{- define "pracisos-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/* Create chart name and version */}}
{{- define "pracisos-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Common labels */}}
{{- define "pracisos-service.labels" -}}
helm.sh/chart: {{ include "pracisos-service.chart" . }}
{{ include "pracisos-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/* Selector labels */}}
{{- define "pracisos-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "pracisos-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/* Service account name */}}
{{- define "pracisos-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "pracisos-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
```

### 5.4 deployment.yaml

```yaml
{{/* k8s/helm/pracisos-service/templates/deployment.yaml */}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "pracisos-service.fullname" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "pracisos-service.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  selector:
    matchLabels:
      {{- include "pracisos-service.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      {{- with .Values.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      labels:
        {{- include "pracisos-service.selectorLabels" . | nindent 8 }}
    spec:
      serviceAccountName: {{ include "pracisos-service.serviceAccountName" . }}
      securityContext:
        {{- toYaml .Values.securityContext | nindent 8 }}
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.service.targetPort }}
              protocol: TCP
            - name: management
              containerPort: 8081
              protocol: TCP
          env:
            {{- toYaml .Values.env | nindent 12 }}
          {{- if .Values.envFromSecrets }}
          envFrom:
            {{- range .Values.envFromSecrets }}
            - secretRef:
                name: {{ .secretName }}
            {{- end }}
          {{- end }}
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.liveness.path }}
              port: management
            initialDelaySeconds: {{ .Values.probes.liveness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.readiness.path }}
              port: management
            initialDelaySeconds: {{ .Values.probes.readiness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
```

### 5.5 service.yaml

```yaml
{{/* k8s/helm/pracisos-service/templates/service.yaml */}}
apiVersion: v1
kind: Service
metadata:
  name: {{ include "pracisos-service.fullname" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "pracisos-service.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: http
      protocol: TCP
      name: http
  selector:
    {{- include "pracisos-service.selectorLabels" . | nindent 4 }}
```

### 5.6 hpa.yaml

```yaml
{{/* k8s/helm/pracisos-service/templates/hpa.yaml */}}
{{- if .Values.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "pracisos-service.fullname" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "pracisos-service.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "pracisos-service.fullname" . }}
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    {{- if .Values.autoscaling.targetCPUUtilizationPercentage }}
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
    {{- end }}
    {{- if .Values.autoscaling.targetMemoryUtilizationPercentage }}
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetMemoryUtilizationPercentage }}
    {{- end }}
{{- end }}
```

---

## 6. ISTIO RESOURCES (Helm Templates)

### 6.1 istio-virtualservice.yaml

```yaml
{{/* k8s/helm/pracisos-service/templates/istio-virtualservice.yaml */}}
{{- if .Values.istio.enabled }}
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: {{ include "pracisos-service.fullname" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "pracisos-service.labels" . | nindent 4 }}
spec:
  hosts:
    - {{ .Values.istio.host | quote }}
  gateways:
    - {{ .Values.istio.gateway }}
  http:
    - match:
        - uri:
            prefix: {{ .Values.istio.pathPrefix | quote }}
      route:
        - destination:
            host: {{ include "pracisos-service.fullname" . }}
            port:
              number: {{ .Values.service.port }}
      timeout: {{ .Values.istio.timeout }}
      retries:
        attempts: {{ .Values.istio.retries.attempts }}
        perTryTimeout: {{ .Values.istio.retries.perTryTimeout }}
        retryOn: {{ .Values.istio.retries.retryOn }}
      cors:
        allowOrigins:
          - exact: "*"
        allowMethods:
          - GET
          - POST
          - PUT
          - DELETE
          - OPTIONS
        allowHeaders:
          - authorization
          - content-type
          - x-request-id
{{- end }}
```

### 6.2 istio-destinationrule.yaml

```yaml
{{/* k8s/helm/pracisos-service/templates/istio-destinationrule.yaml */}}
{{- if .Values.istio.enabled }}
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: {{ include "pracisos-service.fullname" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "pracisos-service.labels" . | nindent 4 }}
spec:
  host: {{ include "pracisos-service.fullname" . }}
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
        http2MaxRequests: 100
        maxRequestsPerConnection: 10
    loadBalancer:
      simple: LEAST_CONN
    outlierDetection:
      consecutive5xxErrors: {{ .Values.istio.circuitBreaker.consecutiveErrors }}
      interval: {{ .Values.istio.circuitBreaker.interval }}
      baseEjectionTime: {{ .Values.istio.circuitBreaker.baseEjectionTime }}
  portLevelSettings:
    - port:
        number: {{ .Values.service.port }}
      tls:
        mode: ISTIO_MUTUAL
{{- end }}
```

### 6.3 istio-authorizationpolicy.yaml

```yaml
{{/* k8s/helm/pracisos-service/templates/istio-authorizationpolicy.yaml */}}
{{- if and .Values.istio.enabled .Values.istio.authorization.enabled }}
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: {{ include "pracisos-service.fullname" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "pracisos-service.labels" . | nindent 4 }}
spec:
  selector:
    matchLabels:
      {{- include "pracisos-service.selectorLabels" . | nindent 6 }}
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - "cluster.local/ns/pracisos/sa/istio-ingressgateway-service-account"
        {{- range .Values.istio.authorization.allowedServices }}
        - source:
            principals:
              - "cluster.local/ns/pracisos/sa/{{ . }}"
        {{- end }}
      to:
        - operation:
            methods:
              - GET
              - POST
              - PUT
              - DELETE
              - PATCH
            paths:
              - "*"
{{- end }}
```

### 6.4 configmap.yaml

```yaml
{{/* k8s/helm/pracisos-service/templates/configmap.yaml */}}
{{- if .Values.configMap.enabled }}
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "pracisos-service.fullname" . }}-config
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "pracisos-service.labels" . | nindent 4 }}
data:
  {{- toYaml .Values.configMap.data | nindent 2 }}
{{- end }}
```

### 6.5 serviceaccount.yaml

```yaml
{{/* k8s/helm/pracisos-service/templates/serviceaccount.yaml */}}
{{- if .Values.serviceAccount.create }}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ include "pracisos-service.serviceAccountName" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "pracisos-service.labels" . | nindent 4 }}
  {{- with .Values.serviceAccount.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
{{- end }}
```

---

## 7. PER-SERVICE HELM VALUES

### 7.1 Auth Service Values

```yaml
# k8s/apps/auth-service/values.yaml
nameOverride: auth-service
fullnameOverride: auth-service

image:
  repository: pracisos/auth-service
  tag: "1.0.0"

replicaCount: 2

env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: DB_HOST
    value: "postgres-auth"
  - name: DB_PORT
    value: "5432"
  - name: DB_NAME
    value: "auth_db"
  - name: DB_USER
    value: "auth_user"
  - name: KAFKA_BOOTSTRAP
    value: "kafka:9092"
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: jwt-secret
        key: secret

envFromSecrets:
  - name: DB_PASSWORD
    secretName: postgres-auth
    secretKey: password

configMap:
  enabled: true
  data:
    application.yml: |
      server:
        port: 8080
      management:
        server:
          port: 8081
        endpoints:
          web:
            exposure:
              include: health,info,prometheus,metrics

istio:
  enabled: true
  gateway: pracisos-gateway
  host: "*"
  pathPrefix: "/api/v1/auth"
  authorization:
    enabled: true
    allowedServices:
      - booking-service
      - charting-service
      - billing-service
```

### 7.2 Booking Service Values

```yaml
# k8s/apps/booking-service/values.yaml
nameOverride: booking-service
fullnameOverride: booking-service

image:
  repository: pracisos/booking-service
  tag: "1.0.0"

replicaCount: 2

env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: DB_HOST
    value: "postgres-booking"
  - name: DB_PORT
    value: "5432"
  - name: DB_NAME
    value: "booking_db"
  - name: DB_USER
    value: "booking_user"
  - name: REDIS_HOST
    value: "redis"
  - name: REDIS_PORT
    value: "6379"
  - name: KAFKA_BOOTSTRAP
    value: "kafka:9092"
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: jwt-secret
        key: secret

envFromSecrets:
  - name: DB_PASSWORD
    secretName: postgres-booking
    secretKey: password

istio:
  enabled: true
  gateway: pracisos-gateway
  host: "*"
  pathPrefix: "/api/v1/booking"
  authorization:
    enabled: true
    allowedServices:
      - auth-service
      - charting-service
      - billing-service
```

### 7.3 Charting Service Values

```yaml
# k8s/apps/charting-service/values.yaml
nameOverride: charting-service
fullnameOverride: charting-service

image:
  repository: pracisos/charting-service
  tag: "1.0.0"

replicaCount: 2

env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: DB_HOST
    value: "postgres-charting"
  - name: DB_PORT
    value: "5432"
  - name: DB_NAME
    value: "charting_db"
  - name: DB_USER
    value: "charting_user"
  - name: KAFKA_BOOTSTRAP
    value: "kafka:9092"
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: jwt-secret
        key: secret

envFromSecrets:
  - name: DB_PASSWORD
    secretName: postgres-charting
    secretKey: password

istio:
  enabled: true
  gateway: pracisos-gateway
  host: "*"
  pathPrefix: "/api/v1/charting"
  authorization:
    enabled: true
    allowedServices:
      - auth-service
      - booking-service
      - billing-service
```

### 7.4 Billing Service Values

```yaml
# k8s/apps/billing-service/values.yaml
nameOverride: billing-service
fullnameOverride: billing-service

image:
  repository: pracisos/billing-service
  tag: "1.0.0"

replicaCount: 2

env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: DB_HOST
    value: "postgres-billing"
  - name: DB_PORT
    value: "5432"
  - name: DB_NAME
    value: "billing_db"
  - name: DB_USER
    value: "billing_user"
  - name: KAFKA_BOOTSTRAP
    value: "kafka:9092"
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: jwt-secret
        key: secret
  - name: STRIPE_SECRET_KEY
    valueFrom:
      secretKeyRef:
        name: stripe-secret
        key: secret-key
  - name: STRIPE_WEBHOOK_SECRET
    valueFrom:
      secretKeyRef:
        name: stripe-secret
        key: webhook-secret

envFromSecrets:
  - name: DB_PASSWORD
    secretName: postgres-billing
    secretKey: password

istio:
  enabled: true
  gateway: pracisos-gateway
  host: "*"
  pathPrefix: "/api/v1/billing"
  authorization:
    enabled: true
    allowedServices:
      - auth-service
      - booking-service
      - charting-service
```

### 7.5 Frontend Values

```yaml
# k8s/apps/frontend/values.yaml
nameOverride: frontend
fullnameOverride: frontend

image:
  repository: pracisos/frontend
  tag: "1.0.0"

replicaCount: 2

service:
  type: ClusterIP
  port: 80
  targetPort: 80

env:
  - name: VITE_API_URL
    value: "http://localhost:8080/api/v1"
  - name: VITE_STRIPE_PUBLIC_KEY
    valueFrom:
      secretKeyRef:
        name: stripe-secret
        key: public-key

resources:
  limits:
    cpu: 500m
    memory: 256Mi
  requests:
    cpu: 100m
    memory: 128Mi

istio:
  enabled: true
  gateway: pracisos-gateway
  host: "*"
  pathPrefix: "/"
  authorization:
    enabled: false  # Public frontend
```

---

## 8. STATEFUL SERVICES MANIFESTS

### 8.1 PostgreSQL StatefulSet (Auth DB Example)

```yaml
# k8s/apps/postgres/auth-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres-auth
  namespace: pracisos
  labels:
    app: postgres-auth
spec:
  serviceName: postgres-auth
  replicas: 1
  selector:
    matchLabels:
      app: postgres-auth
  template:
    metadata:
      labels:
        app: postgres-auth
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
              name: postgres
          env:
            - name: POSTGRES_DB
              value: "auth_db"
            - name: POSTGRES_USER
              value: "auth_user"
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-auth
                  key: password
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
          resources:
            limits:
              cpu: 500m
              memory: 512Mi
            requests:
              cpu: 100m
              memory: 256Mi
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 10Gi
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-auth
  namespace: pracisos
  labels:
    app: postgres-auth
spec:
  type: ClusterIP
  ports:
    - port: 5432
      targetPort: 5432
  selector:
    app: postgres-auth
```

### 8.2 Redis Deployment

```yaml
# k8s/apps/redis/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: pracisos
  labels:
    app: redis
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
        - name: redis
          image: redis:7-alpine
          ports:
            - containerPort: 6379
              name: redis
          resources:
            limits:
              cpu: 500m
              memory: 256Mi
            requests:
              cpu: 100m
              memory: 128Mi
---
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: pracisos
  labels:
    app: redis
spec:
  type: ClusterIP
  ports:
    - port: 6379
      targetPort: 6379
  selector:
    app: redis
```

### 8.3 Kafka StatefulSet (Simplified Single Node)

```yaml
# k8s/apps/kafka/statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
  namespace: pracisos
  labels:
    app: kafka
spec:
  serviceName: kafka
  replicas: 1
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
        - name: kafka
          image: confluentinc/cp-kafka:7.5.0
          ports:
            - containerPort: 9092
              name: kafka
            - containerPort: 29092
              name: kafka-internal
          env:
            - name: KAFKA_BROKER_ID
              value: "1"
            - name: KAFKA_ZOOKEEPER_CONNECT
              value: "zookeeper:2181"
            - name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
              value: "PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT"
            - name: KAFKA_ADVERTISED_LISTENERS
              value: "PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092"
            - name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR
              value: "1"
            - name: KAFKA_AUTO_CREATE_TOPICS_ENABLE
              value: "true"
          volumeMounts:
            - name: data
              mountPath: /var/lib/kafka/data
          resources:
            limits:
              cpu: 1000m
              memory: 1Gi
            requests:
              cpu: 250m
              memory: 512Mi
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 20Gi
---
apiVersion: v1
kind: Service
metadata:
  name: kafka
  namespace: pracisos
  labels:
    app: kafka
spec:
  type: ClusterIP
  ports:
    - port: 9092
      targetPort: 9092
      name: kafka
    - port: 29092
      targetPort: 29092
      name: kafka-internal
  selector:
    app: kafka
---
# Zookeeper for Kafka
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zookeeper
  namespace: pracisos
spec:
  replicas: 1
  selector:
    matchLabels:
      app: zookeeper
  template:
    metadata:
      labels:
        app: zookeeper
    spec:
      containers:
        - name: zookeeper
          image: confluentinc/cp-zookeeper:7.5.0
          ports:
            - containerPort: 2181
          env:
            - name: ZOOKEEPER_CLIENT_PORT
              value: "2181"
            - name: ZOOKEEPER_TICK_TIME
              value: "2000"
---
apiVersion: v1
kind: Service
metadata:
  name: zookeeper
  namespace: pracisos
spec:
  type: ClusterIP
  ports:
    - port: 2181
  selector:
    app: zookeeper
```

### 8.4 Secrets Template

```yaml
# k8s/apps/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
  namespace: pracisos
type: Opaque
stringData:
  secret: "change-me-in-production-min-256-bits-long"
---
apiVersion: v1
kind: Secret
metadata:
  name: postgres-auth
  namespace: pracisos
type: Opaque
stringData:
  password: "auth_pass"
---
apiVersion: v1
kind: Secret
metadata:
  name: postgres-booking
  namespace: pracisos
type: Opaque
stringData:
  password: "booking_pass"
---
apiVersion: v1
kind: Secret
metadata:
  name: postgres-charting
  namespace: pracisos
type: Opaque
stringData:
  password: "charting_pass"
---
apiVersion: v1
kind: Secret
metadata:
  name: postgres-billing
  namespace: pracisos
type: Opaque
stringData:
  password: "billing_pass"
---
apiVersion: v1
kind: Secret
metadata:
  name: stripe-secret
  namespace: pracisos
type: Opaque
stringData:
  secret-key: "sk_test_placeholder"
  webhook-secret: "whsec_placeholder"
  public-key: "pk_test_placeholder"
```

---

## 9. OBSERVABILITY STACK

### 9.1 Prometheus Configuration

```yaml
# k8s/monitoring/prometheus/prometheus-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: pracisos
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s

    scrape_configs:
      - job_name: 'prometheus'
        static_configs:
          - targets: ['localhost:9090']

      - job_name: 'istio-mesh'
        kubernetes_sd_configs:
          - role: endpoints
            namespaces:
              names:
                - istio-system
        relabel_configs:
          - source_labels: [__meta_kubernetes_service_name]
            action: keep
            regex: istio-proxy

      - job_name: 'kubernetes-pods'
        kubernetes_sd_configs:
          - role: pod
            namespaces:
              names:
                - pracisos
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
            action: keep
            regex: true
          - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
            action: replace
            target_label: __metrics_path__
            regex: (.+)
          - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
            action: replace
            regex: ([^:]+)(?::\d+)?;(\d+)
            replacement: $1:$2
            target_label: __address__

      - job_name: 'kubernetes-services'
        kubernetes_sd_configs:
          - role: service
            namespaces:
              names:
                - pracisos
        relabel_configs:
          - source_labels: [__meta_kubernetes_service_annotation_prometheus_io_scrape]
            action: keep
            regex: true
```

### 9.2 Prometheus Deployment

```yaml
# k8s/monitoring/prometheus/prometheus-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: pracisos
  labels:
    app: prometheus
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      serviceAccountName: prometheus
      containers:
        - name: prometheus
          image: prom/prometheus:v2.51.0
          ports:
            - containerPort: 9090
              name: prometheus
          volumeMounts:
            - name: config
              mountPath: /etc/prometheus
            - name: data
              mountPath: /prometheus
          resources:
            limits:
              cpu: 1000m
              memory: 2Gi
            requests:
              cpu: 250m
              memory: 512Mi
      volumes:
        - name: config
          configMap:
            name: prometheus-config
        - name: data
          emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: prometheus
  namespace: pracisos
  labels:
    app: prometheus
spec:
  type: NodePort
  ports:
    - port: 9090
      targetPort: 9090
      nodePort: 30001
  selector:
    app: prometheus
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: prometheus
  namespace: pracisos
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: prometheus
rules:
  - apiGroups: [""]
    resources: ["nodes", "nodes/proxy", "services", "endpoints", "pods"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["extensions"]
    resources: ["ingresses"]
    verbs: ["get", "list", "watch"]
  - nonResourceURLs: ["/metrics"]
    verbs: ["get"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: prometheus
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: prometheus
subjects:
  - kind: ServiceAccount
    name: prometheus
    namespace: pracisos
```

### 9.3 Grafana Deployment

```yaml
# k8s/monitoring/grafana/grafana-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: pracisos
  labels:
    app: grafana
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
        - name: grafana
          image: grafana/grafana:10.4.0
          ports:
            - containerPort: 3000
              name: grafana
          env:
            - name: GF_SECURITY_ADMIN_USER
              value: "admin"
            - name: GF_SECURITY_ADMIN_PASSWORD
              value: "admin"
            - name: GF_USERS_ALLOW_SIGN_UP
              value: "false"
          volumeMounts:
            - name: dashboards
              mountPath: /etc/grafana/provisioning/dashboards
            - name: datasources
              mountPath: /etc/grafana/provisioning/datasources
            - name: data
              mountPath: /var/lib/grafana
          resources:
            limits:
              cpu: 500m
              memory: 512Mi
            requests:
              cpu: 100m
              memory: 128Mi
      volumes:
        - name: dashboards
          configMap:
            name: grafana-dashboards
        - name: datasources
          configMap:
            name: grafana-datasources
        - name: data
          emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: grafana
  namespace: pracisos
  labels:
    app: grafana
spec:
  type: NodePort
  ports:
    - port: 3000
      targetPort: 3000
      nodePort: 30000
  selector:
    app: grafana
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-datasources
  namespace: pracisos
data:
  datasources.yaml: |
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        access: proxy
        url: http://prometheus:9090
        isDefault: true
        editable: false
      - name: Jaeger
        type: jaeger
        access: proxy
        url: http://jaeger:16686
        editable: false
```

### 9.4 Jaeger Deployment

```yaml
# k8s/monitoring/jaeger/jaeger-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
  namespace: pracisos
  labels:
    app: jaeger
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jaeger
  template:
    metadata:
      labels:
        app: jaeger
    spec:
      containers:
        - name: jaeger
          image: jaegertracing/all-in-one:1.55
          ports:
            - containerPort: 16686
              name: ui
            - containerPort: 14268
              name: collector
            - containerPort: 14250
              name: grpc
          env:
            - name: COLLECTOR_OTLP_ENABLED
              value: "true"
          resources:
            limits:
              cpu: 1000m
              memory: 2Gi
            requests:
              cpu: 250m
              memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger
  namespace: pracisos
  labels:
    app: jaeger
spec:
  type: NodePort
  ports:
    - port: 16686
      targetPort: 16686
      nodePort: 30002
      name: ui
    - port: 14268
      targetPort: 14268
      name: collector
    - port: 14250
      targetPort: 14250
      name: grpc
  selector:
    app: jaeger
```

### 9.5 Grafana Dashboard ConfigMap

```yaml
# k8s/monitoring/grafana/dashboards-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboards
  namespace: pracisos
data:
  dashboards.yaml: |
    apiVersion: 1
    providers:
      - name: 'default'
        orgId: 1
        folder: ''
        type: file
        disableDeletion: false
        editable: true
        options:
          path: /var/lib/grafana/dashboards
  platform-health.json: |
    {
      "dashboard": {
        "title": "Platform Health",
        "panels": [
          {
            "title": "Pod Status",
            "type": "stat",
            "targets": [
              {
                "expr": "kube_pod_status_phase{namespace="pracisos",phase="Running"}",
                "legendFormat": "{{pod}}"
              }
            ]
          },
          {
            "title": "Request Rate",
            "type": "graph",
            "targets": [
              {
                "expr": "rate(istio_requests_total{namespace="pracisos"}[5m])",
                "legendFormat": "{{destination_service}}"
              }
            ]
          },
          {
            "title": "Error Rate",
            "type": "graph",
            "targets": [
              {
                "expr": "rate(istio_requests_total{namespace="pracisos",response_code=~"5.."}[5m])",
                "legendFormat": "{{destination_service}}"
              }
            ]
          }
        ]
      }
    }
```

---

## 10. DEPLOYMENT SCRIPTS

### 10.1 deploy-all.sh

```bash
#!/bin/bash
# k8s/scripts/deploy-all.sh

set -e

NAMESPACE="pracisos"
HELM_CHART="../helm/pracisos-service"

echo "=== PRACISOS PLATFORM DEPLOYMENT ==="
echo

# Create namespace
echo "[1/8] Creating namespace..."
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
kubectl label namespace ${NAMESPACE} istio-injection=enabled --overwrite

# Apply secrets
echo "[2/8] Applying secrets..."
kubectl apply -f ../apps/secrets.yaml -n ${NAMESPACE}

# Deploy stateful services
echo "[3/8] Deploying stateful services..."
kubectl apply -f ../apps/postgres/ -n ${NAMESPACE}
kubectl apply -f ../apps/redis/ -n ${NAMESPACE}
kubectl apply -f ../apps/kafka/ -n ${NAMESPACE}

# Wait for databases
echo "[4/8] Waiting for databases to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres-auth -n ${NAMESPACE} --timeout=120s
kubectl wait --for=condition=ready pod -l app=postgres-booking -n ${NAMESPACE} --timeout=120s
kubectl wait --for=condition=ready pod -l app=postgres-charting -n ${NAMESPACE} --timeout=120s
kubectl wait --for=condition=ready pod -l app=postgres-billing -n ${NAMESPACE} --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n ${NAMESPACE} --timeout=60s
kubectl wait --for=condition=ready pod -l app=kafka -n ${NAMESPACE} --timeout=120s

# Deploy Istio resources
echo "[5/8] Applying Istio resources..."
kubectl apply -f ../istio/gateway.yaml -n ${NAMESPACE}
kubectl apply -f ../istio/peer-authentication.yaml -n ${NAMESPACE}
kubectl apply -f ../istio/telemetry.yaml -n istio-system

# Deploy microservices via Helm
echo "[6/8] Deploying microservices..."
helm upgrade --install auth-service ${HELM_CHART} -f ../apps/auth-service/values.yaml -n ${NAMESPACE}
helm upgrade --install booking-service ${HELM_CHART} -f ../apps/booking-service/values.yaml -n ${NAMESPACE}
helm upgrade --install charting-service ${HELM_CHART} -f ../apps/charting-service/values.yaml -n ${NAMESPACE}
helm upgrade --install billing-service ${HELM_CHART} -f ../apps/billing-service/values.yaml -n ${NAMESPACE}
helm upgrade --install frontend ${HELM_CHART} -f ../apps/frontend/values.yaml -n ${NAMESPACE}

# Deploy observability
echo "[7/8] Deploying observability stack..."
kubectl apply -f ../monitoring/prometheus/ -n ${NAMESPACE}
kubectl apply -f ../monitoring/grafana/ -n ${NAMESPACE}
kubectl apply -f ../monitoring/jaeger/ -n ${NAMESPACE}

# Wait for all pods
echo "[8/8] Waiting for all pods to be ready..."
kubectl wait --for=condition=ready pod --all -n ${NAMESPACE} --timeout=300s

echo
echo "=== DEPLOYMENT COMPLETE ==="
echo
echo "Services:"
kubectl get svc -n ${NAMESPACE}
echo
echo "Pods:"
kubectl get pods -n ${NAMESPACE}
echo
echo "Access Points:"
echo "  - Frontend:     http://localhost:8080"
echo "  - Grafana:      http://localhost:30000 (admin/admin)"
echo "  - Prometheus:   http://localhost:30001"
echo "  - Jaeger:       http://localhost:30002"
```

### 10.2 verify-mtls.sh

```bash
#!/bin/bash
# k8s/scripts/verify-mtls.sh

set -e

NAMESPACE="pracisos"

echo "=== mTLS VERIFICATION ==="
echo

# Check PeerAuthentication
echo "[1/4] Checking PeerAuthentication policies..."
kubectl get peerauthentication -n ${NAMESPACE}
kubectl get peerauthentication -n istio-system

# Verify mTLS is STRICT
echo "[2/4] Verifying STRICT mode..."
ISTIO_MTLS=$(kubectl get peerauthentication -n istio-system default -o jsonpath='{.spec.mtls.mode}')
if [ "$ISTIO_MTLS" = "STRICT" ]; then
    echo "  PASS: Mesh-wide mTLS is STRICT"
else
    echo "  FAIL: Mesh-wide mTLS is $ISTIO_MTLS"
    exit 1
fi

# Check envoy sidecars are injected
echo "[3/4] Checking sidecar injection..."
for pod in $(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name -o name); do
    CONTAINERS=$(kubectl get $pod -n ${NAMESPACE} -o jsonpath='{.spec.containers[*].name}')
    if echo "$CONTAINERS" | grep -q "istio-proxy"; then
        echo "  PASS: $pod has istio-proxy sidecar"
    else
        echo "  FAIL: $pod missing istio-proxy sidecar"
    fi
done

# Test service-to-service mTLS
echo "[4/4] Testing service-to-service mTLS..."
AUTH_POD=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=auth-service -o jsonpath='{.items[0].metadata.name}')
BOOKING_SVC="booking-service.${NAMESPACE}.svc.cluster.local"

# Try HTTP request from auth-service to booking-service
kubectl exec -n ${NAMESPACE} $AUTH_POD -c auth-service --     curl -s -o /dev/null -w "%{http_code}"     --connect-timeout 5     http://${BOOKING_SVC}:8080/actuator/health || true

echo
echo "=== mTLS VERIFICATION COMPLETE ==="
echo "All intra-mesh traffic should show 200 OK (or 401/403 if auth required)"
echo "Check Istio mTLS metrics in Grafana dashboard"
```

### 10.3 port-forward.sh

```bash
#!/bin/bash
# k8s/scripts/port-forward.sh

NAMESPACE="pracisos"

echo "Starting port forwards..."
echo "Press Ctrl+C to stop all"
echo

# Frontend
kubectl port-forward -n ${NAMESPACE} svc/frontend 8080:80 &

# Grafana
kubectl port-forward -n ${NAMESPACE} svc/grafana 30000:3000 &

# Prometheus
kubectl port-forward -n ${NAMESPACE} svc/prometheus 30001:9090 &

# Jaeger
kubectl port-forward -n ${NAMESPACE} svc/jaeger 30002:16686 &

# Kafka (for debugging)
kubectl port-forward -n ${NAMESPACE} svc/kafka 30003:9092 &

wait
```

---

## 11. VALIDATION CHECKLIST

| # | Test | Command / Action | Expected Result |
|---|------|-----------------|-----------------|
| 1 | Create cluster | `kind create cluster --config k8s/kind-config.yaml` | 3-node cluster ready |
| 2 | Install Istio | `./k8s/istio/install-istio.sh` | Istio pods running in istio-system |
| 3 | Label namespace | `kubectl label ns pracisos istio-injection=enabled` | Namespace labeled |
| 4 | Deploy all | `./k8s/scripts/deploy-all.sh` | All pods healthy, no CrashLoopBackOff |
| 5 | Check mTLS STRICT | `./k8s/scripts/verify-mtls.sh` | All pods have istio-proxy, STRICT mode confirmed |
| 6 | Test auth login | `curl http://localhost:8080/api/v1/auth/login` | 200 OK with JWT |
| 7 | Test booking flow | Book appointment via frontend | Booking created, slot locked |
| 8 | Test charting | Create note, lock it | Note immutable, amendment works |
| 9 | Test billing | Complete booking -> pay invoice | Stripe webhook updates status |
| 10 | Check Prometheus | `curl http://localhost:30001/metrics` | Metrics endpoint returns data |
| 11 | Check Grafana | Open http://localhost:30000 | Login with admin/admin, dashboards visible |
| 12 | Check Jaeger | Open http://localhost:30002 | Traces visible for all service calls |
| 13 | Check HPA | `kubectl get hpa -n pracisos` | All services have HPA configured |
| 14 | Circuit breaker | Simulate 5xx errors | Outlier detection ejects unhealthy pods |
| 15 | Cross-service auth | Booking service calls auth service | 200 with mTLS, 403 without |
| 16 | Ingress routing | `curl http://localhost:8080/api/v1/auth/login` | Routed to auth-service |
| 17 | Frontend SPA | `curl http://localhost:8080/` | Returns index.html |

---

## 12. TROUBLESHOOTING

### 12.1 Pod Stuck in Init

```bash
# Check istio-init container logs
kubectl logs <pod> -n pracisos -c istio-init

# Common issue: CNI plugin not ready
kubectl get pods -n kube-system | grep cni
```

### 12.2 mTLS Connection Refused

```bash
# Verify DestinationRule has ISTIO_MUTUAL
kubectl get destinationrule -n pracisos -o yaml

# Check if PeerAuthentication is STRICT
kubectl get peerauthentication -n pracisos -o yaml
```

### 12.3 Prometheus Not Scraping

```bash
# Check if pods have prometheus annotations
kubectl get pods -n pracisos -o jsonpath='{.items[*].metadata.annotations}'

# Verify ServiceAccount has RBAC permissions
kubectl auth can-i list pods --as=system:serviceaccount:pracisos:prometheus
```

### 12.4 Kafka Connection Issues

```bash
# Check Kafka broker is listening on correct address
kubectl exec -n pracisos kafka-0 -- kafka-broker-api-versions --bootstrap-server localhost:9092

# Verify topic creation
kubectl exec -n pracisos kafka-0 -- kafka-topics --bootstrap-server localhost:9092 --list
```

---

## 13. PRODUCTION CHECKLIST

Before moving to production:

- [ ] Replace `STRIPE_SECRET_KEY` with live key
- [ ] Replace `JWT_SECRET` with 256-bit random key
- [ ] Enable HTTPS redirect on Gateway
- [ ] Configure proper TLS certificates (cert-manager)
- [ ] Set up external DNS
- [ ] Configure backup for PostgreSQL (Velero)
- [ ] Set up alerting rules in Prometheus
- [ ] Configure log aggregation (Loki/Fluentd)
- [ ] Enable pod disruption budgets
- [ ] Configure network policies
- [ ] Set up CI/CD pipeline (GitHub Actions)
- [ ] Run security scan (Trivy/Snyk)
- [ ] Load testing (k6/Artillery)

---

## END OF KUBERNETES & ISTIO MESH GUIDE

**Version:** 1.0.0 | **Status:** READY FOR IMPLEMENTATION | **Next:** Phase 7 -- Full Observability & Alerting
