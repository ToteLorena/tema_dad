FROM critoma/amd64_u24_noble_ism_security
RUN apt-get update && apt-get install -y openjdk-17-jdk maven
WORKDIR /app
COPY . .
# construiește şi copiază dependenţele
RUN mvn clean package dependency:copy-dependencies
# include şi classes şi toate JAR-urile
CMD ["java","-cp","target/classes:target/dependency/*","com.imageprocessing.Main"]

