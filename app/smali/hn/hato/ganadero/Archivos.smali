.class public final Lhn/hato/ganadero/Archivos;
.super Ljava/lang/Object;
.source "Archivos.java"


# static fields
.field static final AUTORIDAD:Ljava/lang/String; = "hn.hato.ganadero.archivos"


# direct methods
.method private constructor <init>()V
    .registers 1

    .prologue
    .line 21
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static abrir(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
    .registers 6

    .prologue
    .line 93
    invoke-static {p0, p1, p2}, Lhn/hato/ganadero/Archivos;->escribir(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 94
    if-nez v0, :cond_8

    const/4 v0, 0x0

    .line 119
    :goto_7
    return v0

    .line 95
    :cond_8
    new-instance v1, Lhn/hato/ganadero/Archivos$2;

    invoke-direct {v1, v0, p3, p0}, Lhn/hato/ganadero/Archivos$2;-><init>(Ljava/lang/String;Ljava/lang/String;Landroid/app/Activity;)V

    invoke-virtual {p0, v1}, Landroid/app/Activity;->runOnUiThread(Ljava/lang/Runnable;)V

    .line 119
    const/4 v0, 0x1

    goto :goto_7
.end method

.method static carpeta(Landroid/content/Context;)Ljava/io/File;
    .registers 4

    .prologue
    .line 32
    new-instance v0, Ljava/io/File;

    invoke-virtual {p0}, Landroid/content/Context;->getCacheDir()Ljava/io/File;

    move-result-object v1

    const-string v2, "compartir"

    invoke-direct {v0, v1, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    .line 33
    invoke-virtual {v0}, Ljava/io/File;->exists()Z

    move-result v1

    if-nez v1, :cond_14

    invoke-virtual {v0}, Ljava/io/File;->mkdirs()Z

    .line 34
    :cond_14
    return-object v0
.end method

.method public static compartir(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
    .registers 7

    .prologue
    .line 68
    invoke-static {p0, p1, p2}, Lhn/hato/ganadero/Archivos;->escribir(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 69
    if-nez v0, :cond_8

    const/4 v0, 0x0

    .line 88
    :goto_7
    return v0

    .line 70
    :cond_8
    new-instance v1, Lhn/hato/ganadero/Archivos$1;

    invoke-direct {v1, v0, p3, p4, p0}, Lhn/hato/ganadero/Archivos$1;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/app/Activity;)V

    invoke-virtual {p0, v1}, Landroid/app/Activity;->runOnUiThread(Ljava/lang/Runnable;)V

    .line 88
    const/4 v0, 0x1

    goto :goto_7
.end method

.method static escribir(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .registers 15

    .prologue
    const/4 v1, 0x0

    const/4 v2, 0x0

    .line 39
    invoke-static {p1}, Lhn/hato/ganadero/Archivos;->limpiar(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 42
    const/4 v3, 0x0

    :try_start_7
    invoke-static {p2, v3}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B

    move-result-object v3

    .line 44
    invoke-static {p0}, Lhn/hato/ganadero/Archivos;->carpeta(Landroid/content/Context;)Ljava/io/File;

    move-result-object v4

    invoke-virtual {v4}, Ljava/io/File;->listFiles()[Ljava/io/File;

    move-result-object v4

    .line 45
    if-eqz v4, :cond_30

    array-length v5, v4

    :goto_16
    if-ge v2, v5, :cond_30

    aget-object v6, v4, v2

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v8

    invoke-virtual {v6}, Ljava/io/File;->lastModified()J

    move-result-wide v10

    sub-long/2addr v8, v10

    const-wide/32 v10, 0x5265c00

    cmp-long v7, v8, v10

    if-lez v7, :cond_2d

    invoke-virtual {v6}, Ljava/io/File;->delete()Z

    :cond_2d
    add-int/lit8 v2, v2, 0x1

    goto :goto_16

    .line 46
    :cond_30
    new-instance v2, Ljava/io/FileOutputStream;

    new-instance v4, Ljava/io/File;

    invoke-static {p0}, Lhn/hato/ganadero/Archivos;->carpeta(Landroid/content/Context;)Ljava/io/File;

    move-result-object v5

    invoke-direct {v4, v5, v0}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    invoke-direct {v2, v4}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V
    :try_end_3e
    .catch Ljava/lang/Exception; {:try_start_7 .. :try_end_3e} :catch_47
    .catchall {:try_start_7 .. :try_end_3e} :catchall_50

    .line 47
    :try_start_3e
    invoke-virtual {v2, v3}, Ljava/io/FileOutputStream;->write([B)V
    :try_end_41
    .catch Ljava/lang/Exception; {:try_start_3e .. :try_end_41} :catch_60
    .catchall {:try_start_3e .. :try_end_41} :catchall_5d

    .line 52
    if-eqz v2, :cond_46

    :try_start_43
    invoke-virtual {v2}, Ljava/io/FileOutputStream;->close()V
    :try_end_46
    .catch Ljava/lang/Exception; {:try_start_43 .. :try_end_46} :catch_57

    .line 50
    :cond_46
    :goto_46
    return-object v0

    .line 49
    :catch_47
    move-exception v0

    move-object v0, v1

    .line 52
    :goto_49
    if-eqz v0, :cond_4e

    :try_start_4b
    invoke-virtual {v0}, Ljava/io/FileOutputStream;->close()V
    :try_end_4e
    .catch Ljava/lang/Exception; {:try_start_4b .. :try_end_4e} :catch_59

    :cond_4e
    :goto_4e
    move-object v0, v1

    .line 50
    goto :goto_46

    .line 52
    :catchall_50
    move-exception v0

    :goto_51
    if-eqz v1, :cond_56

    :try_start_53
    invoke-virtual {v1}, Ljava/io/FileOutputStream;->close()V
    :try_end_56
    .catch Ljava/lang/Exception; {:try_start_53 .. :try_end_56} :catch_5b

    .line 53
    :cond_56
    :goto_56
    throw v0

    .line 52
    :catch_57
    move-exception v1

    goto :goto_46

    :catch_59
    move-exception v0

    goto :goto_4e

    :catch_5b
    move-exception v1

    goto :goto_56

    :catchall_5d
    move-exception v0

    move-object v1, v2

    goto :goto_51

    .line 49
    :catch_60
    move-exception v0

    move-object v0, v2

    goto :goto_49
.end method

.method public static guardar(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
    .registers 6

    .prologue
    .line 124
    invoke-static {p0, p1, p2}, Lhn/hato/ganadero/Archivos;->escribir(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 125
    if-nez v0, :cond_8

    const/4 v0, 0x0

    .line 138
    :goto_7
    return v0

    .line 126
    :cond_8
    new-instance v1, Lhn/hato/ganadero/Archivos$3;

    invoke-direct {v1, p0, v0, p3}, Lhn/hato/ganadero/Archivos$3;-><init>(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {p0, v1}, Landroid/app/Activity;->runOnUiThread(Ljava/lang/Runnable;)V

    .line 138
    const/4 v0, 0x1

    goto :goto_7
.end method

.method static limpiar(Ljava/lang/String;)Ljava/lang/String;
    .registers 4

    .prologue
    .line 25
    if-nez p0, :cond_12

    const-string v0, ""

    .line 26
    :goto_4
    const-string v1, "."

    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v1

    if-eqz v1, :cond_1b

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object v0

    goto :goto_4

    .line 25
    :cond_12
    const-string v0, "[^A-Za-z0-9._-]"

    const-string v1, "_"

    invoke-virtual {p0, v0, v1}, Ljava/lang/String;->replaceAll(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    goto :goto_4

    .line 27
    :cond_1b
    invoke-virtual {v0}, Ljava/lang/String;->isEmpty()Z

    move-result v1

    if-eqz v1, :cond_23

    const-string v0, "rumentis"

    .line 28
    :cond_23
    invoke-virtual {v0}, Ljava/lang/String;->length()I

    move-result v1

    const/16 v2, 0x78

    if-le v1, v2, :cond_35

    invoke-virtual {v0}, Ljava/lang/String;->length()I

    move-result v1

    add-int/lit8 v1, v1, -0x78

    invoke-virtual {v0, v1}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object v0

    :cond_35
    return-object v0
.end method

.method static tipo(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .registers 4

    .prologue
    .line 57
    if-eqz p1, :cond_9

    invoke-virtual {p1}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :cond_9

    .line 63
    :goto_8
    return-object p1

    .line 59
    :cond_9
    invoke-virtual {p0}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v0

    const-string v1, ".rumentis"

    invoke-virtual {v0, v1}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_18

    const-string p1, "application/vnd.rumentis"

    goto :goto_8

    .line 60
    :cond_18
    invoke-virtual {p0}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v0

    const-string v1, ".vaquero"

    invoke-virtual {v0, v1}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_27

    const-string p1, "application/vnd.rumentis.vaquero"

    goto :goto_8

    .line 61
    :cond_27
    const/16 v0, 0x2e

    invoke-virtual {p0, v0}, Ljava/lang/String;->lastIndexOf(I)I

    move-result v0

    .line 62
    if-ltz v0, :cond_45

    invoke-static {}, Landroid/webkit/MimeTypeMap;->getSingleton()Landroid/webkit/MimeTypeMap;

    move-result-object v1

    add-int/lit8 v0, v0, 0x1

    invoke-virtual {p0, v0}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v1, v0}, Landroid/webkit/MimeTypeMap;->getMimeTypeFromExtension(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 63
    :goto_41
    if-eqz v0, :cond_47

    :goto_43
    move-object p1, v0

    goto :goto_8

    .line 62
    :cond_45
    const/4 v0, 0x0

    goto :goto_41

    .line 63
    :cond_47
    const-string v0, "application/octet-stream"

    goto :goto_43
.end method
