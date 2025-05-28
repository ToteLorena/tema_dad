###############################################################################
# STAGE 0 – build artefacte Java
###############################################################################
FROM critoma/amd64_u24_noble_ism_security AS builder
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-17-jdk maven && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests dependency:copy-dependencies

###############################################################################
# STAGE 1 – extragem image_processor din worker
###############################################################################
FROM image-encryption-system-rev2-openmpi-worker:latest AS mpi_bin

###############################################################################
# STAGE 2 – runtime (conţine Java + binar MPI)
###############################################################################
FROM critoma/amd64_u24_noble_ism_security AS runtime
WORKDIR /app

## 🔧 instalăm DOAR runtime-ul, nu full JDK
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-17-jre-headless \
        libmysqlclient21 \
        iputils-ping && \
    rm -rf /var/lib/apt/lists/*

# artefactele Java
COPY --from=builder /src/target/classes     ./target/classes
COPY --from=builder /src/target/dependency  ./target/dependency

# binar MPI
RUN mkdir -p /app/openmpi
COPY --from=mpi_bin /app/image_processor ./openmpi/image_processor

CMD ["java","-cp","target/classes:target/dependency/*","com.imageprocessing.mdb.Main"]
