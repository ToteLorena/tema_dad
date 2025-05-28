# ----------------------------------------------------
#  Image-processor – build cu OpenMPI + OpenMP + MySQL
# ----------------------------------------------------

CC       = mpicc
CFLAGS   = -Wall -fopenmp               \
           $(shell mysql_config --cflags)

LDFLAGS  = $(shell mysql_config --libs) \
           -lgomp                       # explicit, pt. unele distro-uri

TARGET   = image_processor
SRC      = image_processor.c

all: $(TARGET)

$(TARGET): $(SRC)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

clean:
	rm -f $(TARGET)
