FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
        libopenmpi-dev openmpi-bin \
        libmysqlclient-dev default-libmysqlclient-dev \
        libomp-dev && \
    # dacă header-ul nu ajunge direct în /usr/include, îl linkăm acolo
    if [ ! -f /usr/include/mysql.h ] && [ -f /usr/include/mysql/mysql.h ]; then \
        ln -s /usr/include/mysql/mysql.h /usr/include/mysql.h ; \
    fi

WORKDIR /app
COPY . .
RUN make          # va folosi acum mysql.h

CMD ["mpirun", "--allow-run-as-root", "-np", "2", "./image_processor", "test.bmp", "1234567890abcdef", "encrypt", "ECB", "1"]
