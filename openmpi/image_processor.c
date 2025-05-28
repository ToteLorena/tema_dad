/* -------------------------------------------------------------------------
 * image_processor.c  –  MPI + OpenMP + MySQL
 * ------------------------------------------------------------------------- */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <mpi.h>
#include <omp.h>
#include <unistd.h>
#include <mysql/mysql.h>

/* ───────────── structurile header BMP ───────────── */
#pragma pack(push, 1)
typedef struct {
    unsigned char signature[2];
    unsigned int  fileSize;
    unsigned short reserved1;
    unsigned short reserved2;
    unsigned int  dataOffset;
} BMPHeader;

typedef struct {
    unsigned int  headerSize;
    int           width;
    int           height;
    unsigned short planes;
    unsigned short bitsPerPixel;
    unsigned int  compression;
    unsigned int  imageSize;
    int           xPixelsPerMeter;
    int           yPixelsPerMeter;
    unsigned int  colorsUsed;
    unsigned int  colorsImportant;
} BMPInfoHeader;
#pragma pack(pop)

/* ───────────── prototipuri ───────────── */
void aes_encrypt_ecb (unsigned char *d,int n,const char *k);
void aes_decrypt_ecb (unsigned char *d,int n,const char *k);
void aes_encrypt_cbc (unsigned char *d,int n,const char *k);
void aes_decrypt_cbc (unsigned char *d,int n,const char *k);
void save_to_mysql   (const char *filePath,const char *jobId);

/* ====================================================================== */
int main(int argc, char **argv)
{
    /* ---------- MPI setup ---------- */
    MPI_Init(&argc,&argv);
    int rank,size;
    MPI_Comm_rank(MPI_COMM_WORLD,&rank);
    MPI_Comm_size(MPI_COMM_WORLD,&size);

    if (argc < 6) {
        if (rank==0)
            fprintf(stderr,"Usage: %s <image_path> <key> <encrypt|decrypt> <ECB|CBC> <job_id>\n",argv[0]);
        MPI_Finalize();
        return 1;
    }

    const char *imagePath = argv[1];
    const char *key       = argv[2];
    const char *operation = argv[3];
    const char *mode      = argv[4];
    const char *jobId     = argv[5];

    /* ========== RANK 0 ========== */
    if (rank == 0) {

        printf("Processing image: %s | op=%s | mode=%s | job=%s\n",
               imagePath,operation,mode,jobId);

        /* ---- 1. citește BMP ---- */
        FILE *in = fopen(imagePath,"rb");
        if (!in) { perror("fopen"); MPI_Abort(MPI_COMM_WORLD,1); }

        BMPHeader     hdr;
        BMPInfoHeader ihdr;
        fread(&hdr ,sizeof(hdr) ,1,in);
        fread(&ihdr,sizeof(ihdr),1,in);

        if (hdr.signature[0]!='B'||hdr.signature[1]!='M'){
            fprintf(stderr,"Not a BMP file\n");
            fclose(in);  MPI_Abort(MPI_COMM_WORLD,1);
        }

        int dataSize = ihdr.imageSize ? ihdr.imageSize
                                      : hdr.fileSize - hdr.dataOffset;

        unsigned char *imageData = malloc(dataSize);
        fseek(in,hdr.dataOffset,SEEK_SET);
        fread(imageData,1,dataSize,in);
        fclose(in);

        /* ---- 2. pregătește scatter ---- */
        int *sendcounts = malloc(size*sizeof(int));
        int *displs     = malloc(size*sizeof(int));
        int base        = dataSize/size;
        int extra       = dataSize%size;
        for (int i=0;i<size;++i){
            sendcounts[i] = base + (i<extra);
            displs[i]     = (i==0)?0:displs[i-1]+sendcounts[i-1];
        }

        /* ---- 3. broadcast header ---- */
        MPI_Bcast(&hdr ,sizeof(hdr) ,MPI_BYTE,0,MPI_COMM_WORLD);
        MPI_Bcast(&ihdr,sizeof(ihdr),MPI_BYTE,0,MPI_COMM_WORLD);

        /* ---- 4. scatter + procesare locală ---- */
        unsigned char *chunk = malloc(sendcounts[rank]);
        MPI_Scatterv(imageData,sendcounts,displs,MPI_BYTE,
                     chunk,sendcounts[rank],MPI_BYTE,0,MPI_COMM_WORLD);

        #pragma omp parallel
        {
            int tid         = omp_get_thread_num();
            int nthreads    = omp_get_num_threads();
            int baseT       = sendcounts[rank]/nthreads;
            int extraT      = sendcounts[rank]%nthreads;
            int start       = tid*baseT + (tid<extraT ? tid : extraT);
            int end         = start + baseT + (tid<extraT);
            if (!strcmp(operation,"encrypt")){
                if (!strcmp(mode,"ECB"))
                    aes_encrypt_ecb(chunk+start,end-start,key);
                else
                    aes_encrypt_cbc(chunk+start,end-start,key);
            }else{
                if (!strcmp(mode,"ECB"))
                    aes_decrypt_ecb(chunk+start,end-start,key);
                else
                    aes_decrypt_cbc(chunk+start,end-start,key);
            }
        }

        /* ---- 5. gather ---- */
        MPI_Gatherv(chunk,sendcounts[rank],MPI_BYTE,
                    imageData,sendcounts,displs,MPI_BYTE,
                    0,MPI_COMM_WORLD);
        free(chunk);

        /* ---- 6. salvează fișierul rezultat ---- */
        char outPath[512];
        snprintf(outPath,sizeof(outPath),"%s.processed.bmp",imagePath);

        FILE *out = fopen(outPath,"wb");
        if (!out){ perror("fopen out"); MPI_Abort(MPI_COMM_WORLD,1); }
        fwrite(&hdr ,sizeof(hdr) ,1,out);
        fwrite(&ihdr,sizeof(ihdr),1,out);
        fseek (out,hdr.dataOffset,SEEK_SET);
        fwrite(imageData,1,dataSize,out);
        fclose(out);
        printf("Saved processed image: %s\n",outPath);

        /* ---- 7. DB ---- */
        save_to_mysql(outPath,jobId);

        /* cleanup rank-0 */
        free(imageData); free(sendcounts); free(displs);
    }
    /* ========== ALȚI RANK-URI ========== */
    else {
        BMPHeader hdr; BMPInfoHeader ihdr;
        MPI_Bcast(&hdr ,sizeof(hdr) ,MPI_BYTE,0,MPI_COMM_WORLD);
        MPI_Bcast(&ihdr,sizeof(ihdr),MPI_BYTE,0,MPI_COMM_WORLD);

        int dataSize = ihdr.imageSize ? ihdr.imageSize
                                      : hdr.fileSize - hdr.dataOffset;
        int base  = dataSize/size;
        int extra = dataSize%size;
        int mySz  = base + (rank<extra);

        unsigned char *chunk = malloc(mySz);
        MPI_Scatterv(NULL,NULL,NULL,MPI_BYTE,
                     chunk,mySz,MPI_BYTE,0,MPI_COMM_WORLD);

        #pragma omp parallel
        {
            int tid      = omp_get_thread_num();
            int nt       = omp_get_num_threads();
            int baseT    = mySz/nt;
            int extraT   = mySz%nt;
            int start    = tid*baseT + (tid<extraT ? tid : extraT);
            int end      = start + baseT + (tid<extraT);
            if (!strcmp(operation,"encrypt")){
                if (!strcmp(mode,"ECB"))
                    aes_encrypt_ecb(chunk+start,end-start,key);
                else
                    aes_encrypt_cbc(chunk+start,end-start,key);
            }else{
                if (!strcmp(mode,"ECB"))
                    aes_decrypt_ecb(chunk+start,end-start,key);
                else
                    aes_decrypt_cbc(chunk+start,end-start,key);
            }
        }
        MPI_Gatherv(chunk,mySz,MPI_BYTE,
                    NULL,NULL,NULL,MPI_BYTE,
                    0,MPI_COMM_WORLD);
        free(chunk);
    }

    MPI_Finalize();
    return 0;
}

/* ====================================================================== */
/*  AES – demo XOR                                                        */
void aes_encrypt_ecb(unsigned char *d,int n,const char *k){
    int len=strlen(k);  for(int i=0;i<n;++i) d[i]^=k[i%len];
}
void aes_decrypt_ecb(unsigned char *d,int n,const char *k){
    aes_encrypt_ecb(d,n,k);
}
void aes_encrypt_cbc(unsigned char *d,int n,const char *k){
    int len=strlen(k); unsigned char iv=0x42;
    for(int i=0;i<n;++i){ d[i]^=iv; d[i]^=k[i%len]; iv=d[i]; }
}
void aes_decrypt_cbc(unsigned char *d,int n,const char *k){
    int len=strlen(k); unsigned char iv=0x42, tmp;
    for(int i=0;i<n;++i){ tmp=d[i]; d[i]^=k[i%len]; d[i]^=iv; iv=tmp; }
}

/* ====================================================================== */
/*  INSERT BLOB în MySQL                                                  */
void save_to_mysql(const char *filePath,const char *jobId)
{
    MYSQL *conn = mysql_init(NULL);
    if(!conn){ fprintf(stderr,"mysql_init failed\n"); return; }

    if(!mysql_real_connect(conn,"mysql","root","password",
                           "image_db",3306,NULL,0)){
        fprintf(stderr,"MySQL connect: %s\n",mysql_error(conn));
        mysql_close(conn); return;
    }

    /* citește fișierul */
    FILE *f=fopen(filePath,"rb"); if(!f){ perror("fopen"); mysql_close(conn);return;}
    fseek(f,0,SEEK_END); long fs=ftell(f); fseek(f,0,SEEK_SET);
    char *buf=malloc(fs); fread(buf,1,fs,f); fclose(f);

    const char *sql=
        "INSERT INTO processed_images (job_id,image_data,created_at)"
        " VALUES(?,?,NOW())";
    MYSQL_STMT *stmt=mysql_stmt_init(conn);
    if(mysql_stmt_prepare(stmt,sql,(unsigned long)strlen(sql))){
        fprintf(stderr,"stmt_prepare: %s\n",mysql_stmt_error(stmt));
        goto cleanup;
    }

    MYSQL_BIND b[2]; memset(b,0,sizeof(b));
    unsigned long lenJob=strlen(jobId), lenBlob=(unsigned long)fs;
    char isNull[2]={0,0};

    b[0].buffer_type   = MYSQL_TYPE_STRING;
    b[0].buffer        = (void*)jobId;
    b[0].buffer_length = lenJob;
    b[0].length        = &lenJob;
    b[0].is_null       = &isNull[0];

    b[1].buffer_type   = MYSQL_TYPE_LONG_BLOB;
    b[1].buffer        = buf;
    b[1].buffer_length = lenBlob;
    b[1].length        = &lenBlob;
    b[1].is_null       = &isNull[1];

    if(mysql_stmt_bind_param(stmt,b)){
        fprintf(stderr,"bind: %s\n",mysql_stmt_error(stmt)); goto cleanup;
    }
    if(mysql_stmt_execute(stmt)){
        fprintf(stderr,"exec: %s\n",mysql_stmt_error(stmt)); goto cleanup;
    }
    printf("Image saved in MySQL, job=%s (bytes=%ld)\n",jobId,fs);

cleanup:
    mysql_stmt_close(stmt);
    free(buf);
    mysql_close(conn);
}
