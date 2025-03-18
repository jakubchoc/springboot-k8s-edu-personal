## 🚀 Automatic Deployment of a Spring Boot Application on K3s using GitHub Actions, Docker, and MetalLB

## 📌 What You'll Need
- ✅ Remote server/local machine with SSH access
- ✅ GitHub account
- ✅ DockerHub account
- ✅ Spring Boot application with a test endpoint

---

This guide shows you how to automate the deployment of the Spring Boot API to Kubernetes using GitHub Actions and how to set up a load balancer to ensure proper routing and scalability.

🆗 I tried this setup on a Raspberry Pi (raspbian) with internet access using cloudflare tunnel. On linode I had a problem with accessing the public IP address of the load balancer.

## 1️⃣ Installing Kubernetes (K3s) on the Server
To deploy our application, we need a Kubernetes cluster. We are using K3s, a lightweight version of Kubernetes, ideal for small applications.

### 📥 Install K3s
Run the following command on your server:
```sh
curl -sfL https://get.k3s.io | sh -
```
This will download and install K3s. Once installed, K3s should automatically start as a service.

### ✅ Verify K3s is Running
```sh
sudo systemctl status k3s
```
If everything is working, you should see output similar to this:
```sh
● k3s.service - Lightweight Kubernetes
   Loaded: loaded (/etc/systemd/system/k3s.service; enabled; preset: enabled)
   Active: active (running) since Sun 2025-02-02 08:34:59 UTC; 7s ago
```

---

## 2️⃣ Installing MetalLB ⚡
MetalLB is a load balancer for Kubernetes that provides external access to services deployed in the cluster.

### 📥 Install MetalLB
Run the following command on your server:
```sh
kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.13.10/config/manifests/metallb-native.yaml
```

### ✅ Check MetalLB is Running
```sh
kubectl get pods -n metallb-system
```
Expected output:
```sh
NAME                         READY   STATUS    RESTARTS   AGE
controller-75f7db8ddc-xxxxx  1/1     Running   0          2m
speaker-xxxxx                1/1     Running   0          2m
```

---

## 3️⃣ Configuring GitHub Secrets 🔐
GitHub Actions requires secrets to store sensitive data securely.

### 🔹 Required Secrets
- DOCKER_USERNAME (your DockerHub username)
- DOCKER_PASSWORD (your DockerHub password)
- YOUR_APP (application name, e.g., springboot-k8s-edu)
- KUBE_CONFIG (your Kubernetes config file)

### 🔹 Retrieving the Kubernetes Config File
On your server, run:
```sh
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
```
Edit the config file:
```sh
nano ~/.kube/config
```
Replace:
```sh
server: https://127.0.0.1:6443
```
With your server's actual IP:
```sh
server: https://172.237.15.201:6443
```

### 🔹 Convert Kube Config to Base64
Run:
```sh
cat ~/.kube/config | base64 -w 0
```
Copy the output and add it as the `KUBE_CONFIG` GitHub secret.

---

## 4️⃣ Installing Docker and Adding Docker Secrets to Kubernetes

### 📥 Install Docker
For Ubuntu/Debian:
```sh
sudo apt install -y docker.io
```
Start and enable Docker:
```sh
sudo systemctl start docker
sudo systemctl enable docker
```
Verify installation:
```sh
docker --version
```

### 🔹 Log in to Docker Hub
```sh
docker login
```
This creates a config file with authentication details.

### 🔹 Verify Docker Credentials
```sh
cat $HOME/.docker/config.json
```

### 🔹 Create a Kubernetes Secret for Docker Hub
```sh
kubectl create secret generic dockerhub-secret \
  --from-file=.dockerconfigjson=$HOME/.docker/config.json \
  --type=kubernetes.io/dockerconfigjson
```
This creates a Kubernetes Secret named `dockerhub-secret`.

---

## 5️⃣ Create the Dockerfile
```dockerfile
FROM eclipse-temurin:21-jdk
EXPOSE 8080
WORKDIR /springboot-k8s-edu
COPY target/springboot-k8s-edu.jar springboot-k8s-edu.jar
ENTRYPOINT ["java", "-jar", "springboot-k8s-edu.jar"]
```

---

## 6️⃣ GitHub Actions Configuration
GitHub Actions will automate building, testing, and deploying the application.

### Create `.github/workflows/production.yml` in your repository:
```yaml
name: CI/CD
on:
  push:
    branches:
      - production
jobs:
  test:
    name: Test
    runs-on: self-hosted
    steps:
      - name: Checkout repository
        uses: actions/checkout@v2
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Build and run tests
        run: mvn clean test
  build:
    name: Build
    needs: test
    runs-on: self-hosted
    steps:
      - name: Checkout repository
        uses: actions/checkout@v2
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Build and install
        run: mvn clean install
      - name: Log in to Docker Hub
        uses: docker/login-action@v2
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}
      - name: Build and push Docker image
        run: |
          # Vytvoření pouze pro ARM64 platformu
          docker build -t ${{ secrets.DOCKER_USERNAME }}/${{ secrets.YOUR_APP }}:latest .
          docker push ${{ secrets.DOCKER_USERNAME }}/${{ secrets.YOUR_APP }}:latest
  deploy:
    name: Deploy
    needs: [ test, build ]
    runs-on: self-hosted
    steps:
      - name: Checkout repository
        uses: actions/checkout@v2
      - name: Test Kubernetes Connection
        run: |
          kubectl get nodes
      - name: Deploy to Kubernetes
        run: |
          kubectl apply -f k8s
```

---

## 7️⃣ Create Kubernetes Deployment
📄 `k8s/deployment.yml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: springboot-k8s-edu
spec:
  replicas: 1
  selector:
    matchLabels:
      app: springboot-k8s-edu
  template:
    metadata:
      labels:
        app: springboot-k8s-edu
    spec:
      containers:
        - name: springboot-k8s-edu
          image: your-dockerhub-username/springboot-k8s-edu:latest
          ports:
            - containerPort: 8080
```

---

## 8️⃣ Create Kubernetes Service
📄 `k8s/service.yml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: springboot-k8s-edu-service
spec:
  selector:
    app: springboot-k8s-edu
  ports:
    - protocol: TCP
      port: 8080
      targetPort: 8080
  type: LoadBalancer
```

---

## 9️⃣ Configure MetalLB
📄 `k8s/metallb-config.yaml`
```yaml
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: first-pool
  namespace: metallb-system
spec:
  addresses:
    - 172.236.15.203-172.236.15.210
  autoAssign: true
---
apiVersion: metallb.io/v1beta1
kind: L2Advertisement
metadata:
  name: example
  namespace: metallb-system
```

---

## 🔴 How to verify that everything works?
```sh
kubectl get pods
kubectl logs <name of your springboot pod>
```
If you see the spring boot logs running, it's correct.
Now check if you have an external IP address

```sh
kubectl get svc
```
If the `LOAD BALANCER` service has an external IP, everything looks fine and you can try calling the API endpoint. Enjoy. 🎉


## 🎉 Conclusion
- ✅ Automated deployment of a Spring Boot application to a Kubernetes cluster
- ✅ Fully managed with GitHub Actions
- ✅ Scalable infrastructure with K3s + MetalLB
