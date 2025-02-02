FROM eclipse-temurin:21-jdk
EXPOSE 8080
WORKDIR /springboot-k8s-edu
COPY target/springboot-k8s-edu.jar springboot-k8s-edu.jar
ENTRYPOINT ["java", "-jar", "springboot-k8s-edu.jar"]
