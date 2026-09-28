.class public Lhn/hato/ganadero/ProveedorArchivos;
.super Landroid/content/ContentProvider;
.source "ProveedorArchivos.java"


# direct methods
.method public constructor <init>()V
    .registers 1

    .prologue
    .line 17
    invoke-direct {p0}, Landroid/content/ContentProvider;-><init>()V

    return-void
.end method

.method private archivo(Landroid/net/Uri;)Ljava/io/File;
    .registers 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/FileNotFoundException;
        }
    .end annotation

    .prologue
    .line 21
    invoke-virtual {p1}, Landroid/net/Uri;->getLastPathSegment()Ljava/lang/String;

    move-result-object v0

    .line 22
    if-eqz v0, :cond_10

    invoke-static {v0}, Lhn/hato/ganadero/Archivos;->limpiar(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_18

    :cond_10
    new-instance v0, Ljava/io/FileNotFoundException;

    const-string v1, "nombre no v\u00e1lido"

    invoke-direct {v0, v1}, Ljava/io/FileNotFoundException;-><init>(Ljava/lang/String;)V

    throw v0

    .line 23
    :cond_18
    new-instance v1, Ljava/io/File;

    invoke-virtual {p0}, Lhn/hato/ganadero/ProveedorArchivos;->getContext()Landroid/content/Context;

    move-result-object v2

    invoke-static {v2}, Lhn/hato/ganadero/Archivos;->carpeta(Landroid/content/Context;)Ljava/io/File;

    move-result-object v2

    invoke-direct {v1, v2, v0}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    .line 24
    invoke-virtual {v1}, Ljava/io/File;->isFile()Z

    move-result v2

    if-nez v2, :cond_31

    new-instance v1, Ljava/io/FileNotFoundException;

    invoke-direct {v1, v0}, Ljava/io/FileNotFoundException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 25
    :cond_31
    return-object v1
.end method


# virtual methods
.method public delete(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I
    .registers 5

    .prologue
    .line 53
    const/4 v0, 0x0

    return v0
.end method

.method public getType(Landroid/net/Uri;)Ljava/lang/String;
    .registers 4

    .prologue
    const/4 v0, 0x0

    .line 34
    invoke-virtual {p1}, Landroid/net/Uri;->getLastPathSegment()Ljava/lang/String;

    move-result-object v1

    .line 35
    if-nez v1, :cond_8

    :goto_7
    return-object v0

    :cond_8
    invoke-static {v1, v0}, Lhn/hato/ganadero/Archivos;->tipo(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    goto :goto_7
.end method

.method public insert(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;
    .registers 5

    .prologue
    .line 52
    new-instance v0, Ljava/lang/UnsupportedOperationException;

    const-string v1, "solo lectura"

    invoke-direct {v0, v1}, Ljava/lang/UnsupportedOperationException;-><init>(Ljava/lang/String;)V

    throw v0
.end method

.method public onCreate()Z
    .registers 2

    .prologue
    .line 18
    const/4 v0, 0x1

    return v0
.end method

.method public openFile(Landroid/net/Uri;Ljava/lang/String;)Landroid/os/ParcelFileDescriptor;
    .registers 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/FileNotFoundException;
        }
    .end annotation

    .prologue
    .line 29
    if-eqz p2, :cond_12

    const-string v0, "w"

    invoke-virtual {p2, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v0

    if-eqz v0, :cond_12

    new-instance v0, Ljava/io/FileNotFoundException;

    const-string v1, "solo lectura"

    invoke-direct {v0, v1}, Ljava/io/FileNotFoundException;-><init>(Ljava/lang/String;)V

    throw v0

    .line 30
    :cond_12
    invoke-direct {p0, p1}, Lhn/hato/ganadero/ProveedorArchivos;->archivo(Landroid/net/Uri;)Ljava/io/File;

    move-result-object v0

    const/high16 v1, 0x10000000

    invoke-static {v0, v1}, Landroid/os/ParcelFileDescriptor;->open(Ljava/io/File;I)Landroid/os/ParcelFileDescriptor;

    move-result-object v0

    return-object v0
.end method

.method public query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;
    .registers 12

    .prologue
    const/4 v3, 0x1

    const/4 v0, 0x0

    .line 40
    :try_start_2
    invoke-direct {p0, p1}, Lhn/hato/ganadero/ProveedorArchivos;->archivo(Landroid/net/Uri;)Ljava/io/File;
    :try_end_5
    .catch Ljava/io/FileNotFoundException; {:try_start_2 .. :try_end_5} :catch_26

    move-result-object v2

    .line 41
    if-eqz p2, :cond_29

    .line 42
    :goto_8
    new-instance v1, Landroid/database/MatrixCursor;

    invoke-direct {v1, p2, v3}, Landroid/database/MatrixCursor;-><init>([Ljava/lang/String;I)V

    .line 43
    array-length v3, p2

    new-array v3, v3, [Ljava/lang/Object;

    .line 44
    :goto_10
    array-length v4, p2

    if-ge v0, v4, :cond_4a

    .line 45
    const-string v4, "_display_name"

    aget-object v5, p2, v0

    invoke-virtual {v4, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_35

    invoke-virtual {v2}, Ljava/io/File;->getName()Ljava/lang/String;

    move-result-object v4

    aput-object v4, v3, v0

    .line 44
    :cond_23
    :goto_23
    add-int/lit8 v0, v0, 0x1

    goto :goto_10

    .line 40
    :catch_26
    move-exception v0

    const/4 v0, 0x0

    .line 49
    :goto_28
    return-object v0

    .line 41
    :cond_29
    const/4 v1, 0x2

    new-array p2, v1, [Ljava/lang/String;

    const-string v1, "_display_name"

    aput-object v1, p2, v0

    const-string v1, "_size"

    aput-object v1, p2, v3

    goto :goto_8

    .line 46
    :cond_35
    const-string v4, "_size"

    aget-object v5, p2, v0

    invoke-virtual {v4, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_23

    invoke-virtual {v2}, Ljava/io/File;->length()J

    move-result-wide v4

    invoke-static {v4, v5}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v4

    aput-object v4, v3, v0

    goto :goto_23

    .line 48
    :cond_4a
    invoke-virtual {v1, v3}, Landroid/database/MatrixCursor;->addRow([Ljava/lang/Object;)V

    move-object v0, v1

    .line 49
    goto :goto_28
.end method

.method public update(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I
    .registers 6

    .prologue
    .line 54
    const/4 v0, 0x0

    return v0
.end method
